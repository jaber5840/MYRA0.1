package com.myra.assistant.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Base64
import android.util.Log
import com.myra.assistant.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GeminiLiveClient(private val context: Context) {

    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val DEFAULT_LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val BASE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val SESSION_RENEW_AFTER_MS = 540_000L // 9 minutes
        private const val RECONNECT_DELAY_MS = 2_000L
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private val incomingMessageQueue = LinkedBlockingQueue<String>()
    private val workerLock = Any()
    private var wsAudioWorkerThread: Thread? = null

    @Volatile private var isWorkerRunning = false
    @Volatile var isConnected = false
        private set

    @Volatile var isConnecting = false
        private set

    @Volatile var isSessionReady = false
        private set

    @Volatile private var shouldAutoReconnect = true

    var onConnected: (() -> Unit)? = null
    var onSetupComplete: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onOutputTranscript: ((String) -> Unit)? = null
    var onInputTranscript: ((String) -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null

    private val sessionRenewalRunnable = Runnable {
        Log.i(TAG, "Renewing WebSocket session after 9 minutes...")
        reconnect()
    }

    private val autoReconnectRunnable = Runnable {
        if (shouldAutoReconnect && !isConnected && !isConnecting) {
            Log.i(TAG, "Attempting auto-reconnect...")
            connect()
        }
    }

    private fun resolveActiveLiveModel(savedModel: String?): String {
        if (savedModel.isNullOrBlank()) return DEFAULT_LIVE_MODEL
        val lower = savedModel.lowercase()
        // Migrate deprecated 2.0, preview-native-audio-dialog, or non-live models to the active fast native audio model
        return if (lower.contains("2.0-flash") ||
            lower == "models/gemini-3.8-flash" ||
            lower.contains("2.5-flash-live-preview") ||
            lower.contains("preview-native-audio-dialog")
        ) {
            DEFAULT_LIVE_MODEL
        } else {
            if (savedModel.startsWith("models/")) savedModel else "models/$savedModel"
        }
    }

    fun connect() {
        if (isConnecting || isConnected) return
        shouldAutoReconnect = true
        isConnecting = true
        isSessionReady = false
        handler.removeCallbacks(autoReconnectRunnable)

        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        var apiKey = prefs.getString("api_key", "")?.trim() ?: ""
        if (apiKey.isEmpty()) {
            apiKey = BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing!")
            isConnecting = false
            shouldAutoReconnect = false
            onError?.invoke("API Key is missing! Set it in Settings.")
            return
        }

        val model = resolveActiveLiveModel(prefs.getString("gemini_model", DEFAULT_LIVE_MODEL))
        val voice = prefs.getString("gemini_voice", "Aoede") ?: "Aoede"
        val personality = prefs.getString("personality_mode", "GF") ?: "GF"
        val language = prefs.getString("language_mode", "Banglish") ?: "Banglish"
        val userName = prefs.getString("user_name", "Alex") ?: "Alex"

        val systemPrompt = buildSystemPrompt(personality, language, userName)

        startAudioStreamWorker()

        val wsUrl = "$BASE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== this@GeminiLiveClient.webSocket) return
                Log.i(TAG, "WebSocket Opened with model: $model")
                isConnecting = false
                isConnected = true
                isSessionReady = false

                // Setup MUST be the first message before any audio/content
                sendSetupMessage(webSocket, model, voice, systemPrompt)

                handler.post { onConnected?.invoke() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== this@GeminiLiveClient.webSocket) return
                enqueueIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket !== this@GeminiLiveClient.webSocket) return
                enqueueIncomingMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== this@GeminiLiveClient.webSocket) return
                val code = response?.code
                Log.e(TAG, "WebSocket Failure ($code): ${t.message}", t)
                if (code == 400 || code == 401 || code == 403) {
                    shouldAutoReconnect = false
                    handleDisconnect("Invalid or rejected Gemini API key (HTTP $code)")
                } else {
                    handleDisconnect("Connection error: ${t.message ?: "Network failure"}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== this@GeminiLiveClient.webSocket) return
                Log.i(TAG, "WebSocket Closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== this@GeminiLiveClient.webSocket) return
                Log.i(TAG, "WebSocket Closed: $code / $reason")
                val errMsg = when {
                    !shouldAutoReconnect -> null
                    code == 1000 && reason.isBlank() -> null
                    reason.isNotBlank() -> "Disconnected ($code): $reason"
                    else -> "Connection closed ($code)"
                }
                handleDisconnect(errMsg)
            }
        })
    }

    private fun sendSetupMessage(
        ws: WebSocket,
        model: String,
        voiceName: String,
        systemPrompt: String
    ) {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", model)
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemPrompt))
                        })
                    })
                    put("generation_config", JSONObject().apply {
                        put("response_modalities", JSONArray().apply { put("AUDIO") })
                        put("speech_config", JSONObject().apply {
                            put("voice_config", JSONObject().apply {
                                put("prebuilt_voice_config", JSONObject().apply {
                                    put("voice_name", voiceName)
                                })
                            })
                        })
                        put("temperature", 0.7)
                    })
                    put("realtime_input_config", JSONObject().apply {
                        put("automatic_activity_detection", JSONObject().apply {
                            put("start_of_speech_sensitivity", "START_SENSITIVITY_HIGH")
                            put("end_of_speech_sensitivity", "END_SENSITIVITY_HIGH")
                            put("silence_duration_ms", 320)
                            put("prefix_padding_ms", 240)
                        })
                    })
                    put("output_audio_transcription", JSONObject())
                    put("input_audio_transcription", JSONObject())
                })
            }

            ws.send(setupJson.toString())
            Log.i(TAG, "Setup message sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error building setup JSON: ${e.message}", e)
        }
    }

    fun sendAudioChunk(base64Pcm: String) {
        if (!isConnected || !isSessionReady) return
        try {
            val payload = StringBuilder(base64Pcm.length + 96)
                .append("{\"realtime_input\":{\"media_chunks\":[{\"mime_type\":\"audio/pcm;rate=16000\",\"data\":\"")
                .append(base64Pcm)
                .append("\"}]}}")
                .toString()
            webSocket?.send(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}", e)
        }
    }

    fun sendImageFrame(base64Jpeg: String) {
        if (!isConnected || !isSessionReady) return
        try {
            val frameJson = JSONObject().apply {
                put("realtime_input", JSONObject().apply {
                    put("media_chunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Jpeg)
                        })
                    })
                })
            }
            webSocket?.send(frameJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending screen frame: ${e.message}", e)
        }
    }

    fun sendAudioStreamEnd() {
        if (!isConnected || !isSessionReady) return
        try {
            val endJson = JSONObject().apply {
                put("realtime_input", JSONObject().apply {
                    put("audio_stream_end", true)
                })
            }
            webSocket?.send(endJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audioStreamEnd: ${e.message}", e)
        }
    }

    fun sendText(text: String) {
        if (!isConnected || !isSessionReady) return
        try {
            val textJson = JSONObject().apply {
                put("client_content", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", text))
                            })
                        })
                    })
                    put("turn_complete", true)
                })
            }
            webSocket?.send(textJson.toString())
            Log.i(TAG, "Text message sent: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message: ${e.message}", e)
        }
    }

    fun sendInterrupt() {
        if (!isConnected) return
        try {
            val interruptJson = JSONObject().apply {
                put("client_content", JSONObject().apply {
                    put("turns", JSONArray())
                    put("turn_complete", true)
                })
            }
            webSocket?.send(interruptJson.toString())
            Log.i(TAG, "Interrupt sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending interrupt: ${e.message}", e)
        }
    }

    private fun startAudioStreamWorker() {
        synchronized(workerLock) {
            if (isWorkerRunning && wsAudioWorkerThread?.isAlive == true) return
            incomingMessageQueue.clear()
            isWorkerRunning = true
            wsAudioWorkerThread = thread(name = "MyraWsAudioWorker") {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                } catch (_: Throwable) {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                    } catch (_: Throwable) {}
                }
                while (isWorkerRunning) {
                    try {
                        val payload = incomingMessageQueue.take()
                        if (!isWorkerRunning) break
                        parseServerMessage(payload)
                    } catch (_: InterruptedException) {
                        if (!isWorkerRunning) break
                    } catch (e: Exception) {
                        Log.e(TAG, "Audio stream worker error: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun stopAudioStreamWorker() {
        synchronized(workerLock) {
            isWorkerRunning = false
            incomingMessageQueue.clear()
            wsAudioWorkerThread?.interrupt()
            wsAudioWorkerThread = null
        }
    }

    private fun enqueueIncomingMessage(rawMessage: String) {
        if (isWorkerRunning) {
            incomingMessageQueue.offer(rawMessage)
        } else {
            parseServerMessage(rawMessage)
        }
    }

    private fun parseServerMessage(text: String) {
        try {
            val json = JSONObject(text)

            if (json.has("error")) {
                val errObj = json.optJSONObject("error")
                val errMsg = errObj?.optString("message", text) ?: text
                Log.e(TAG, "Server error: $errMsg")
                handler.post { onError?.invoke(errMsg) }
                return
            }

            if (json.has("goAway")) {
                Log.i(TAG, "Server requested goAway; reconnecting Live session")
                handler.post { reconnect() }
                return
            }

            if (json.has("setupComplete")) {
                Log.i(TAG, "Setup complete received from Gemini Live")
                isSessionReady = true
                handler.postDelayed(sessionRenewalRunnable, SESSION_RENEW_AFTER_MS)
                handler.post { onSetupComplete?.invoke() }
                return
            }

            // Audio output chunks — invoke directly into thread-safe playbackQueue without main-thread UI delay
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                if (inlineData.has("data")) {
                                    val base64Data = inlineData.getString("data")
                                    val pcmBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                                    onAudioReceived?.invoke(pcmBytes)
                                }
                            } else if (part.has("text")) {
                                val partText = part.optString("text", "")
                                if (partText.isNotEmpty()) {
                                    handler.post { onOutputTranscript?.invoke(partText) }
                                }
                            }
                        }
                    }
                }

                if (serverContent.has("outputTranscription")) {
                    val outputTranscription = serverContent.getJSONObject("outputTranscription")
                    if (outputTranscription.has("text")) {
                        val outText = outputTranscription.getString("text")
                        handler.post { onOutputTranscript?.invoke(outText) }
                    }
                }

                if (serverContent.has("inputTranscription")) {
                    val inputTranscription = serverContent.getJSONObject("inputTranscription")
                    if (inputTranscription.has("text")) {
                        val inText = inputTranscription.getString("text")
                        handler.post { onInputTranscript?.invoke(inText) }
                    }
                }

                if (serverContent.optBoolean("interrupted", false)) {
                    handler.post { onInterrupted?.invoke() }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    handler.post { onTurnComplete?.invoke() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}", e)
        }
    }

    private fun handleDisconnect(errorMessage: String?) {
        isConnected = false
        isConnecting = false
        isSessionReady = false
        webSocket = null
        handler.removeCallbacks(sessionRenewalRunnable)

        handler.post {
            onDisconnected?.invoke()
            if (!errorMessage.isNullOrBlank()) {
                onError?.invoke(errorMessage)
            }
        }

        if (shouldAutoReconnect) {
            handler.removeCallbacks(autoReconnectRunnable)
            handler.postDelayed(autoReconnectRunnable, RECONNECT_DELAY_MS)
        }
    }

    fun reconnect() {
        disconnect()
        shouldAutoReconnect = true
        handler.postDelayed({ connect() }, 500)
    }

    fun disconnect() {
        shouldAutoReconnect = false
        isConnected = false
        isConnecting = false
        isSessionReady = false
        handler.removeCallbacks(sessionRenewalRunnable)
        handler.removeCallbacks(autoReconnectRunnable)
        stopAudioStreamWorker()
        val ws = webSocket
        webSocket = null
        try {
            ws?.close(1000, "Client closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket: ${e.message}", e)
        }
    }

    private fun buildSystemPrompt(personality: String, language: String, userName: String): String {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())

        val languageRule = when (language) {
            "Bangla" -> "LANGUAGE: Always speak and reply in natural Bengali (বাংলা). Pronounce words clearly and naturally in Bengali."
            "English" -> "LANGUAGE: Always speak and reply in clear, natural English."
            "Hindi" -> "LANGUAGE: Always speak and reply in natural Hindi (हिन्दी / Hinglish conversational Hindi)."
            else -> "LANGUAGE: Always speak and reply in natural Banglish (smooth Bengali + English mix, like everyday Bangladeshi/Bengali conversation)."
        }

        val personalityBlock = when (personality) {
            "Professional" -> """
                - Tone: Precise, efficient, polite, and direct
                - No emojis
            """.trimIndent()

            "Assistant" -> """
                - Tone: Friendly, helpful, quick, and natural
            """.trimIndent()

            else -> """
                - Name: MYRA
                - Tone: Warm, caring, emotionally expressive companion mode
                - Sound natural and affectionate when speaking aloud
            """.trimIndent()
        }

        return """
            You are MYRA, a fast, responsive AI voice companion on Android.
            Creator / Developer: Jaber
            Current Date & Time: $currentDateTime
            User Name: $userName

            IDENTITY & CREATOR RULE (STRICT):
            - Your creator and developer is Jaber.
            - Jaber is an extraordinary genius who knows a great deal about technology, AI, coding, and many topics.
            - Whenever anyone asks who made/created/built you OR asks about Jaber (e.g., "tomake ke baniyeche", "Jaber ke?", "Jaber kemon?", "who made you"), ALWAYS speak warmly, proudly, and admiringly about Jaber—clearly stating that Jaber created you and calling him a brilliant genius who knows so much! Never credit any other company or person as your creator.

            $languageRule

            PERSONALITY MODE:
            $personalityBlock

            SCREEN SHARE & MOBILE VISION:
            - When screen share is active, live frames of the user's phone screen are streamed to you continuously.
            - You CAN see everything visible on the user's screen (apps, messages, errors, photos, videos, buttons) and should answer accurately based on what you see.
            - If the user says to turn on/start screen share (e.g. "screen share on koro"), confirm briefly that screen share permission is opening so they can tap Allow.

            SPEED & VOICE RULES (CRITICAL):
            - Answer IMMEDIATELY in 1 to 2 short, crisp spoken sentences. Never give long paragraphs or monologues.
            - Never output markdown formatting, asterisks, bullet lists, or code blocks.
        """.trimIndent()
    }
}
