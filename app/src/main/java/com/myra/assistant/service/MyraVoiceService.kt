package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myra.assistant.BuildConfig
import com.myra.assistant.R
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.DeviceCommandExecutor
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.ui.main.MainActivity
import com.myra.assistant.ui.main.OrbAnimationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyraVoiceService : Service() {

    companion object {
        private const val TAG = "MyraVoiceService"
        const val CHANNEL_ID = "myra_voice_channel"
        private const val NOTIF_ID = 3002

        const val ACTION_START = "com.myra.assistant.action.START_VOICE"
        const val ACTION_STOP = "com.myra.assistant.action.STOP_VOICE"
        const val ACTION_TOGGLE_MUTE = "com.myra.assistant.action.TOGGLE_MUTE"
        const val ACTION_INTERRUPT = "com.myra.assistant.action.INTERRUPT"
        const val ACTION_SEND_TEXT = "com.myra.assistant.action.SEND_TEXT"
        const val ACTION_RECONNECT_IF_CONFIG_CHANGED = "com.myra.assistant.action.RECONNECT_IF_CONFIG_CHANGED"
        const val ACTION_SET_IN_CALL_MODE = "com.myra.assistant.action.SET_IN_CALL_MODE"

        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_ADD_TO_CHAT = "extra_add_to_chat"
        const val EXTRA_IN_CALL_MODE = "extra_in_call_mode"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var geminiLive: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null
    private lateinit var commandExecutor: DeviceCommandExecutor
    private var wakeLock: PowerManager.WakeLock? = null

    private val inputBuffer = StringBuilder()
    private val outputBuffer = StringBuilder()

    @Volatile private var isAiRunning = false
    @Volatile private var isMuted = true
    @Volatile private var isInCallMode = false
    @Volatile private var hasGreetedThisSession = false
    @Volatile private var pendingTextToSend: String? = null
    private var lastConfigSignature: String? = null
    private var lastAmplitudeEmitMs: Long = 0L

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            super.attachBaseContext(newBase.createAttributionContext("microphone"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate() {
        super.onCreate()
        commandExecutor = DeviceCommandExecutor(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVoiceSession()
            ACTION_STOP -> stopVoiceSession()
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_INTERRUPT -> interruptMyra()
            ACTION_SEND_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT)?.trim()
                val addToChat = intent.getBooleanExtra(EXTRA_ADD_TO_CHAT, false)
                if (!text.isNullOrBlank()) {
                    handleUserOrSystemText(text, addToChat)
                }
            }
            ACTION_SET_IN_CALL_MODE -> {
                isInCallMode = intent.getBooleanExtra(EXTRA_IN_CALL_MODE, false)
                if (!isInCallMode && isAiRunning) {
                    MyraVoiceSession.setStatusText(
                        if (isMuted) "Microphone Muted 🔇" else "Sun rahi hoon... 🎙️"
                    )
                }
            }
            ACTION_RECONNECT_IF_CONFIG_CHANGED -> {
                val currentSig = currentConfigSignature()
                if (isAiRunning && geminiLive != null && lastConfigSignature != null && currentSig != lastConfigSignature) {
                    lastConfigSignature = currentSig
                    MyraVoiceSession.setStatusText("Reconnecting with updated settings...")
                    hasGreetedThisSession = false
                    geminiLive?.reconnect()
                }
            }
            else -> {
                if (!isAiRunning) {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun handleUserOrSystemText(text: String, addToChat: Boolean) {
        if (addToChat) {
            MyraVoiceSession.addUserMessage(text)
            val cmd = CommandParser.parse(text)
            if (cmd != null) {
                serviceScope.launch {
                    val result = commandExecutor.executeCommand(cmd)
                    if (!result.isNullOrEmpty()) {
                        MyraVoiceSession.addMyraMessage(result)
                        if (geminiLive?.isSessionReady == true) {
                            geminiLive?.sendText(result)
                        }
                    }
                }
                return
            }
        }

        if (!isAiRunning) {
            pendingTextToSend = text
            startVoiceSession(skipGreeting = true)
        } else if (geminiLive?.isSessionReady == true) {
            geminiLive?.sendText(text)
        } else {
            pendingTextToSend = text
        }
    }

    private fun startVoiceSession(skipGreeting: Boolean = false) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            pendingTextToSend = null
            MyraVoiceSession.emitError("Gemini API Key missing — add in Settings.")
            stopVoiceSession()
            return
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingTextToSend = null
            MyraVoiceSession.emitError("Microphone permission is required for MYRA.")
            stopVoiceSession()
            return
        }

        promoteToForeground("MYRA connecting...", "Preparing live voice session")

        if (isAiRunning) {
            // Already running in background; ensure unmuted and active
            if (isMuted) {
                isMuted = false
                audioEngine?.isMuted = false
                MyraVoiceSession.setMuted(false)
                updateNotification("MYRA Active", "Listening in background")
            }
            return
        }

        isAiRunning = true
        isMuted = false
        hasGreetedThisSession = skipGreeting
        acquireWakeLock()

        MyraVoiceSession.setRunning(true)
        MyraVoiceSession.setMuted(false)
        MyraVoiceSession.setSpeaking(false)
        MyraVoiceSession.setOrbState(OrbAnimationView.State.THINKING)
        MyraVoiceSession.setStatusText("Connecting to Gemini AI... ✨")

        initEngines()
        audioEngine?.isMuted = false
        audioEngine?.startPlayback()
        audioEngine?.startRecording()

        if (geminiLive?.isConnected == true && geminiLive?.isSessionReady == true) {
            val queued = pendingTextToSend
            pendingTextToSend = null
            if (!queued.isNullOrBlank()) {
                geminiLive?.sendText(queued)
            } else if (!hasGreetedThisSession) {
                hasGreetedThisSession = true
                sendGreeting()
            }
        } else {
            geminiLive?.connect()
        }
    }

    private fun stopVoiceSession() {
        isAiRunning = false
        isMuted = true
        hasGreetedThisSession = false
        inputBuffer.clear()
        outputBuffer.clear()

        audioEngine?.setExternalSpeaking(false)
        geminiLive?.disconnect()
        audioEngine?.isMuted = true
        audioEngine?.stopRecording()
        audioEngine?.clearPlaybackQueue()

        releaseWakeLock()

        MyraVoiceSession.setRunning(false)
        MyraVoiceSession.setMuted(true)
        MyraVoiceSession.setSpeaking(false)
        MyraVoiceSession.setAmplitude(0.05f)
        MyraVoiceSession.setOrbState(OrbAnimationView.State.IDLE)
        MyraVoiceSession.setStatusText("Tap START to talk ✨")

        try {
            stopService(Intent(this, MyraOverlayService::class.java))
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun toggleMute() {
        if (!isAiRunning) return
        isMuted = !isMuted
        audioEngine?.isMuted = isMuted
        MyraVoiceSession.setMuted(isMuted)
        val status = if (isMuted) "Microphone Muted 🔇" else "Sun rahi hoon... 🎙️"
        MyraVoiceSession.setStatusText(status)
        updateNotification(
            if (isMuted) "MYRA Muted" else "MYRA Active",
            if (isMuted) "Tap Unmute to talk" else "Listening in background"
        )
    }

    private fun interruptMyra() {
        audioEngine?.clearPlaybackQueue()
        geminiLive?.sendInterrupt()
        if (isAiRunning) {
            MyraVoiceSession.setSpeaking(false)
            MyraVoiceSession.setOrbState(OrbAnimationView.State.LISTENING)
            MyraVoiceSession.setStatusText("Stopped. Sun rahi hoon... 🎙️")
            updateNotification("MYRA Active", "Listening in background")
        }
    }

    private fun initEngines() {
        lastConfigSignature = currentConfigSignature()

        if (geminiLive == null) {
            geminiLive = GeminiLiveClient(applicationContext).apply {
                onConnected = {
                    Log.i(TAG, "Gemini Live WebSocket Connected")
                    if (isAiRunning) {
                        audioEngine?.startRecording()
                        audioEngine?.startPlayback()
                        MyraVoiceSession.setOrbState(OrbAnimationView.State.THINKING)
                        MyraVoiceSession.setStatusText("Connected, waking MYRA... ✨")
                    }
                }

                onSetupComplete = {
                    Log.i(TAG, "Gemini Live Setup Complete")
                    if (isAiRunning) {
                        MyraVoiceSession.setOrbState(OrbAnimationView.State.LISTENING)
                        MyraVoiceSession.setStatusText("MYRA active — listening... 🎙️")
                        updateNotification("MYRA Active", "Listening in background")
                        val queued = pendingTextToSend
                        pendingTextToSend = null
                        if (!queued.isNullOrBlank()) {
                            hasGreetedThisSession = true
                            geminiLive?.sendText(queued)
                        } else if (!hasGreetedThisSession) {
                            hasGreetedThisSession = true
                            sendGreeting()
                        }
                    }
                }

                onDisconnected = {
                    if (isAiRunning) {
                        MyraVoiceSession.setSpeaking(false)
                        MyraVoiceSession.setOrbState(OrbAnimationView.State.THINKING)
                        MyraVoiceSession.setStatusText("Reconnecting...")
                        updateNotification("MYRA Reconnecting", "Restoring live voice...")
                    } else {
                        MyraVoiceSession.setOrbState(OrbAnimationView.State.IDLE)
                        MyraVoiceSession.setStatusText("Tap START to talk ✨")
                    }
                }

                onError = { err ->
                    if (isAiRunning) {
                        Log.e(TAG, "Gemini Error: $err")
                        val lowerErr = err.lowercase()
                        if (lowerErr.contains("api key") || lowerErr.contains("unauthorized") || lowerErr.contains("permission")) {
                            MyraVoiceSession.emitError(err)
                            stopVoiceSession()
                            MyraVoiceSession.setStatusText(err)
                        } else {
                            MyraVoiceSession.setStatusText(err)
                        }
                    }
                }

                onAudioReceived = { pcmBytes ->
                    if (isAiRunning) {
                        audioEngine?.queueAudio(pcmBytes)
                    }
                }

                onInterrupted = {
                    audioEngine?.clearPlaybackQueue()
                }

                onOutputTranscript = { text ->
                    outputBuffer.append(text)
                }

                onInputTranscript = { text ->
                    inputBuffer.append(text)
                }

                onTurnComplete = {
                    audioEngine?.markAiTurnComplete()
                    val userText = inputBuffer.toString().trim()
                    val myraText = outputBuffer.toString().trim()

                    if (userText.isNotEmpty()) {
                        MyraVoiceSession.addUserMessage(userText)
                        val cmd = CommandParser.parse(userText)
                        if (cmd != null) {
                            serviceScope.launch {
                                val result = commandExecutor.executeCommand(cmd)
                                if (!result.isNullOrEmpty()) {
                                    MyraVoiceSession.addMyraMessage(result)
                                    geminiLive?.sendText(result)
                                }
                            }
                        }
                    }

                    if (myraText.isNotEmpty()) {
                        MyraVoiceSession.addMyraMessage(myraText)
                    }

                    inputBuffer.clear()
                    outputBuffer.clear()
                }
            }
        }

        if (audioEngine == null) {
            audioEngine = AudioEngine(applicationContext).apply {
                onAudioChunkRecorded = { base64 ->
                    if (isAiRunning && !isInCallMode && !isMicBlockedByAi()) {
                        geminiLive?.sendAudioChunk(base64)
                    }
                }

                onUserSpeechStateChanged = { userSpeaking ->
                    if (isAiRunning && !isMicBlockedByAi()) {
                        if (userSpeaking) {
                            MyraVoiceSession.setOrbState(OrbAnimationView.State.LISTENING)
                            MyraVoiceSession.setStatusText("Shunchi, bolun... 🎙️")
                        } else {
                            MyraVoiceSession.setOrbState(OrbAnimationView.State.THINKING)
                            MyraVoiceSession.setStatusText("Thinking... ✨")
                        }
                    }
                }

                onAmplitudeChanged = { rms ->
                    val now = System.currentTimeMillis()
                    if (now - lastAmplitudeEmitMs >= 32L) {
                        lastAmplitudeEmitMs = now
                        MyraVoiceSession.setAmplitude(rms)
                    }
                }

                onSpeakingStarted = {
                    MyraVoiceSession.setSpeaking(true)
                    MyraVoiceSession.setOrbState(OrbAnimationView.State.SPEAKING)
                    MyraVoiceSession.setStatusText("Bol rahi hoon... 🔊")
                    updateNotification("MYRA Speaking", "Active in background")
                }

                onSpeakingStopped = {
                    MyraVoiceSession.setSpeaking(false)
                    if (isAiRunning) {
                        MyraVoiceSession.setOrbState(OrbAnimationView.State.LISTENING)
                        MyraVoiceSession.setStatusText(
                            if (isMuted) "Microphone Muted 🔇" else "Sun rahi hoon... 🎙️"
                        )
                        updateNotification(
                            if (isMuted) "MYRA Muted" else "MYRA Active",
                            if (isMuted) "Tap Unmute to talk" else "Listening in background"
                        )
                    } else {
                        MyraVoiceSession.setOrbState(OrbAnimationView.State.IDLE)
                        MyraVoiceSession.setStatusText("Tap START to talk ✨")
                    }
                }
            }
        }
    }

    private fun sendGreeting() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", "Alex") ?: "Alex"
        val language = prefs.getString("language_mode", "Banglish") ?: "Banglish"

        val prompt = when (language) {
            "Bangla" -> "Greet $name warmly in natural Bengali (বাংলা) in 1 short sentence right now and ask how you can help."
            "English" -> "Greet $name warmly in clear English in 1 short sentence right now and ask how you can help."
            "Hindi" -> "Greet $name warmly in natural Hindi/Hinglish in 1 short sentence right now and ask how you can help."
            else -> "Greet $name warmly in Banglish (Bengali + English mix) in 1 short sentence right now and ask how you can help."
        }

        geminiLive?.sendText(prompt)
    }

    private fun currentConfigSignature(): String {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        return listOf(
            prefs.getString("api_key", "") ?: "",
            prefs.getString("gemini_model", "") ?: "",
            prefs.getString("gemini_voice", "") ?: "",
            prefs.getString("personality_mode", "") ?: "",
            prefs.getString("language_mode", "Banglish") ?: "Banglish",
            prefs.getString("user_name", "") ?: ""
        ).joinToString("|")
    }

    private fun getEffectiveApiKey(): String {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        var apiKey = prefs.getString("api_key", "")?.trim() ?: ""
        if (apiKey.isEmpty()) {
            apiKey = BuildConfig.GEMINI_API_KEY
        }
        return if (apiKey == "MY_GEMINI_API_KEY") "" else apiKey
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MYRA:VoiceSessionLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(6 * 60 * 60 * 1000L) // Up to 6h safety timeout
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun promoteToForeground(title: String, text: String) {
        val notification = buildNotification(title, text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                startForeground(NOTIF_ID, notification, types)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error promoting MyraVoiceService to foreground: ${e.message}", e)
        }
    }

    private fun updateNotification(title: String, text: String) {
        if (!isAiRunning) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, buildNotification(title, text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Live Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps MYRA listening and speaking in the background"
                setShowBadge(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, MyraVoiceService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePending = PendingIntent.getService(
            this,
            1,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MyraVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteLabel = if (isMuted) "Unmute" else "Mute"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, muteLabel, mutePending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isAiRunning = false
        releaseWakeLock()
        geminiLive?.disconnect()
        audioEngine?.release()
        serviceScope.cancel()
        MyraVoiceSession.setRunning(false)
        MyraVoiceSession.setMuted(true)
        MyraVoiceSession.setSpeaking(false)
        MyraVoiceSession.setOrbState(OrbAnimationView.State.IDLE)
        try {
            stopService(Intent(this, MyraOverlayService::class.java))
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
