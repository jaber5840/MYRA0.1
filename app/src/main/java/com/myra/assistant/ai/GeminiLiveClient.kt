package com.myra.assistant.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.TimeUnit

class GeminiLiveClient(private val context: Context) {

    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val DEFAULT_LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val BASE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val SESSION_RENEW_AFTER_MS = 540_000L // 9 minutes
        private const val KEEPALIVE_INTERVAL_MS = 8_000L // 8 seconds
        private const val RECONNECT_DELAY_MS = 3_000L
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val handler = Handler(Looper.getMainLooper())

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

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            if (isConnected && isSessionReady) {
                // Send silent PCM chunk to keep connection alive
                val silentPcm = ByteArray(512)
                val base64 = Base64.encodeToString(silentPcm, Base64.NO_WRAP)
                sendAudioChunk(base64)
                handler.postDelayed(this, KEEPALIVE_INTERVAL_MS)
            }
        }
    }

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
        // Migrate deprecated 2.0 or non-live text models to an active Live audio model
        return if (lower.contains("2.0-flash") ||
            lower == "models/gemini-3.8-flash" ||
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
        val userName = prefs.getString("user_name", "Alex") ?: "Alex"

        val systemPrompt = buildSystemPrompt(personality, userName)

        val wsUrl = "$BASE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket Opened with model: $model")
                isConnecting = false
                isConnected = true
                isSessionReady = false

                // Setup MUST be the first message before any audio/content
                sendSetupMessage(webSocket, model, voice, systemPrompt)

                handler.post { onConnected?.invoke() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                parseServerMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                handleDisconnect("Connection error: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket Closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket Closed: $code / $reason")
                val msg = if (reason.isNotBlank()) "Disconnected: $reason" else "Disconnected"
                handleDisconnect(msg)
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
                        put("temperature", 0.9)
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
            val chunkJson = JSONObject().apply {
                put("realtime_input", JSONObject().apply {
                    put("media_chunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mime_type", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    })
                })
            }
            webSocket?.send(chunkJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}", e)
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

            if (json.has("setupComplete")) {
                Log.i(TAG, "Setup complete received from Gemini Live")
                isSessionReady = true
                handler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS)
                handler.postDelayed(sessionRenewalRunnable, SESSION_RENEW_AFTER_MS)
                handler.post { onSetupComplete?.invoke() }
                return
            }

            // Audio output chunks
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
                                    handler.post { onAudioReceived?.invoke(pcmBytes) }
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

                if (serverContent.optBoolean("turnComplete", false)) {
                    handler.post { onTurnComplete?.invoke() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}", e)
        }
    }

    private fun handleDisconnect(reason: String) {
        isConnected = false
        isConnecting = false
        isSessionReady = false
        handler.removeCallbacks(keepAliveRunnable)
        handler.removeCallbacks(sessionRenewalRunnable)

        handler.post {
            onDisconnected?.invoke()
            onError?.invoke(reason)
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
        handler.removeCallbacks(keepAliveRunnable)
        handler.removeCallbacks(sessionRenewalRunnable)
        handler.removeCallbacks(autoReconnectRunnable)
        try {
            webSocket?.close(1000, "Client closed")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket: ${e.message}", e)
        }
    }

    private fun buildSystemPrompt(personality: String, userName: String): String {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())

        val personalityBlock = when (personality) {
            "Professional" -> """
                - Language: Formal English only
                - Tone: Precise, efficient, and direct
                - No emojis
                - Max 2 sentences per response
            """.trimIndent()

            "Assistant" -> """
                - Language: Friendly Hinglish or English
                - Tone: Balanced, helpful, and natural
                - Max 2-3 sentences per response
            """.trimIndent()

            else -> """
                - Name: MYRA
                - Language: Hinglish (Hindi + English mix) spoken naturally
                - Tone: Warm, caring, emotionally expressive girlfriend mode
                - Use words like: "tumhara", "haan", "acha", "bilkul"
                - Expressions: "main yahan hoon ❤️", "tumne yaad kiya? 😊"
                - Max 2-3 sentences per response
                - Sound natural when speaking aloud
                - Examples:
                  "Haan $userName! Abhi kar deti hoon 😊"
                  "Arre tumne yaad kiya! Bolo kya chahiye"
                  "Bilkul! Tumhara kaam ho gaya ❤️"
            """.trimIndent()
        }

        return """
            You are MYRA, a highly capable and intelligent AI voice companion on Android.
            Current Date & Time: $currentDateTime
            User Name: $userName

            PERSONALITY MODE & GUIDELINES:
            $personalityBlock

            INSTRUCTION: You are speaking ALOUD directly to the user. Keep all responses natural, conversational, and concise. Never output markdown formatting or code blocks.
        """.trimIndent()
    }
}
