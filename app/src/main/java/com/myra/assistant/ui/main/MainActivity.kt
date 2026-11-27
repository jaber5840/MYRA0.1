package com.myra.assistant.ui.main

import android.Manifest
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.myra.assistant.BuildConfig
import com.myra.assistant.R
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.databinding.ActivityMainBinding
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.update.GitHubUpdateChecker
import com.myra.assistant.update.RemoteConfigUpdateManager
import com.myra.assistant.update.UpdateDialogHelper
import com.myra.assistant.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var geminiLive: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val chatAdapter = ChatAdapter()

    private val inputBuffer = StringBuilder()
    private val outputBuffer = StringBuilder()

    private var isAiRunning = false
    private var isMuted = true
    private var isInCallMode = false
    private var pendingStartAfterPermission = false
    private var hasGreetedThisSession = false
    private var lastConfigSignature: String? = null

    private val handler = Handler(Looper.getMainLooper())

    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateSystemStatus()
            handler.postDelayed(this, 30_000L)
        }
    }

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "Call ended broadcast received")
            isInCallMode = false
            binding.statusText.text = "Sun rahi hoon... 🎙️"
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            startSystemServices()
            if (pendingStartAfterPermission) {
                pendingStartAfterPermission = false
                startAiAssistant()
            }
        } else {
            pendingStartAfterPermission = false
            Toast.makeText(this, "Microphone permission is required for MYRA", Toast.LENGTH_LONG).show()
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            super.attachBaseContext(newBase.createAttributionContext("default"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkAndRequestPermissions(autoStart = false)
        startStatusUpdates()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, IntentFilter(CallMonitorService.ACTION_CALL_ENDED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callEndedReceiver, IntentFilter(CallMonitorService.ACTION_CALL_ENDED))
        }

        viewModel.commandResult.observe(this) { result ->
            if (!result.isNullOrEmpty()) {
                addMyraMessage(result)
                geminiLive?.sendText(result)
            }
        }

        handleIncomingCallIntent(intent)
        checkForAppUpdateOnLaunch()
    }

    private fun checkForAppUpdateOnLaunch() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong("last_update_check_ms", 0L)
        // Short 1-minute cooldown so fresh app launches detect Remote Config / GitHub updates promptly
        if (now - lastCheck < 60 * 1000L) return
        prefs.edit().putLong("last_update_check_ms", now).apply()

        RemoteConfigUpdateManager.checkForUpdates(this, forceRefresh = false) { outcome ->
            val result = outcome.checkResult
            if (result is GitHubUpdateChecker.CheckResult.UpdateAvailable) {
                val release = result.release
                val skippedTag = prefs.getString("skipped_update_tag", "")
                if (!outcome.isMandatory && release.tagName == skippedTag) return@checkForUpdates

                runOnUiThread {
                    UpdateDialogHelper.showUpdateDialog(
                        activity = this@MainActivity,
                        release = release,
                        isMandatory = outcome.isMandatory
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCallIntent(intent)
    }

    private fun setupUI() {
        binding.chatRecycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.chatRecycler.adapter = chatAdapter

        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.startStopBtn.setOnClickListener {
            if (isAiRunning) {
                stopAiAssistant()
            } else {
                startAiAssistant()
            }
        }

        binding.orbView.setOnClickListener {
            if (!isAiRunning) {
                startAiAssistant()
            }
        }

        binding.micButton.setOnClickListener {
            if (!isAiRunning) {
                startAiAssistant()
            } else {
                toggleMute()
            }
        }

        binding.micButton.setOnLongClickListener {
            interruptMyra()
            true
        }

        updateControlButtons()
    }

    private fun updateControlButtons() {
        if (isAiRunning) {
            binding.startStopBtn.text = "STOP"
            binding.startStopBtn.setIconResource(android.R.drawable.ic_media_pause)
            binding.startStopBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#880E4F")
            )
            binding.micButton.setImageResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on)
        } else {
            binding.startStopBtn.text = "START"
            binding.startStopBtn.setIconResource(android.R.drawable.ic_media_play)
            binding.startStopBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#FF1744")
            )
            binding.micButton.setImageResource(R.drawable.ic_mic_off)
        }
    }

    private fun checkAndRequestPermissions(autoStart: Boolean) {
        val neededPermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            neededPermissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            pendingStartAfterPermission = autoStart
            requestPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            startSystemServices()
            if (autoStart) {
                startAiAssistant()
            }
        }
    }

    private fun startSystemServices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Skipping CallMonitorService start: READ_PHONE_STATE permission not granted")
            return
        }
        try {
            val callMonitorIntent = Intent(this, CallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(callMonitorIntent)
            } else {
                startService(callMonitorIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CallMonitorService: ${e.message}", e)
        }
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

    private fun startAiAssistant() {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            isAiRunning = false
            isMuted = true
            updateControlButtons()
            binding.orbView.setState(OrbAnimationView.State.IDLE)
            binding.statusText.text = "Gemini API Key missing — add in Settings 🔑"
            AlertDialog.Builder(this)
                .setTitle("Gemini API Key Required 🔑")
                .setMessage("Please add your Gemini API Key in Settings before starting MYRA.")
                .setPositiveButton("OPEN SETTINGS") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("CANCEL", null)
                .show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions(autoStart = true)
            return
        }

        isAiRunning = true
        isMuted = false
        hasGreetedThisSession = false
        updateControlButtons()

        binding.orbView.setState(OrbAnimationView.State.THINKING)
        binding.statusText.text = "Connecting to Gemini AI... ✨"

        initGeminiLive()
        audioEngine?.isMuted = false
        audioEngine?.startPlayback()
        audioEngine?.startRecording()

        if (geminiLive?.isConnected == true && geminiLive?.isSessionReady == true) {
            hasGreetedThisSession = true
            sendGreeting()
        } else {
            geminiLive?.connect()
        }
    }

    private fun stopAiAssistant() {
        isAiRunning = false
        isMuted = true
        hasGreetedThisSession = false
        audioEngine?.setExternalSpeaking(false)
        geminiLive?.disconnect()
        audioEngine?.isMuted = true
        audioEngine?.stopRecording()
        audioEngine?.clearPlaybackQueue()

        binding.orbView.setState(OrbAnimationView.State.IDLE)
        binding.waveformView.setAmplitude(0.05f)
        animateRedOverlay(false)
        binding.statusText.text = "Tap START to talk ✨"
        updateControlButtons()
    }

    private fun initGeminiLive() {
        lastConfigSignature = currentConfigSignature()

        if (geminiLive == null) {
            geminiLive = GeminiLiveClient(this).apply {
                onConnected = {
                    Log.i(TAG, "Gemini Live WebSocket Connected")
                    if (isAiRunning) {
                        audioEngine?.startRecording()
                        audioEngine?.startPlayback()
                        binding.orbView.setState(OrbAnimationView.State.THINKING)
                        binding.statusText.text = "Connected, waking MYRA... ✨"
                    }
                }

                onSetupComplete = {
                    Log.i(TAG, "Gemini Live Setup Complete")
                    if (isAiRunning) {
                        binding.orbView.setState(OrbAnimationView.State.LISTENING)
                        binding.statusText.text = "MYRA active — listening... 🎙️"
                        if (!hasGreetedThisSession) {
                            hasGreetedThisSession = true
                            sendGreeting()
                        }
                    }
                }

                onDisconnected = {
                    if (isAiRunning) {
                        binding.orbView.setState(OrbAnimationView.State.THINKING)
                        binding.statusText.text = "Reconnecting..."
                    } else {
                        binding.orbView.setState(OrbAnimationView.State.IDLE)
                        binding.statusText.text = "Tap START to talk ✨"
                    }
                }

                onError = { err ->
                    if (isAiRunning) {
                        Log.e(TAG, "Gemini Error: $err")
                        val lowerErr = err.lowercase()
                        if (lowerErr.contains("api key") || lowerErr.contains("unauthorized") || lowerErr.contains("permission")) {
                            stopAiAssistant()
                            binding.statusText.text = err
                            Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                        } else {
                            binding.statusText.text = err
                        }
                    }
                }

                onAudioReceived = { pcmBytes ->
                    if (isAiRunning) {
                        audioEngine?.queueAudio(pcmBytes)
                    }
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
                        addUserMessage(userText)
                        val cmd = CommandParser.parse(userText)
                        if (cmd != null) {
                            viewModel.executeCommand(cmd)
                        }
                    }

                    if (myraText.isNotEmpty()) {
                        addMyraMessage(myraText)
                    }

                    inputBuffer.clear()
                    outputBuffer.clear()
                }
            }
        }

        if (audioEngine == null) {
            audioEngine = AudioEngine(this).apply {
                onAudioChunkRecorded = { base64 ->
                    if (isAiRunning && !isInCallMode && !isMicBlockedByAi()) {
                        geminiLive?.sendAudioChunk(base64)
                    }
                }

                onUserSpeechStateChanged = { userSpeaking ->
                    runOnUiThread {
                        if (!isAiRunning || isMicBlockedByAi()) return@runOnUiThread
                        if (userSpeaking) {
                            binding.orbView.setState(OrbAnimationView.State.LISTENING)
                            binding.statusText.text = "Shunchi, bolun... 🎙️"
                        } else {
                            binding.orbView.setState(OrbAnimationView.State.THINKING)
                            binding.statusText.text = "Thinking... ✨"
                        }
                    }
                }

                onAmplitudeChanged = { rms ->
                    runOnUiThread {
                        binding.waveformView.setAmplitude(rms)
                        binding.orbView.amplitude = rms
                    }
                }

                onSpeakingStarted = {
                    runOnUiThread {
                        binding.orbView.setState(OrbAnimationView.State.SPEAKING)
                        binding.statusText.text = "Bol rahi hoon... 🔊"
                        animateRedOverlay(true)
                    }
                }

                onSpeakingStopped = {
                    runOnUiThread {
                        if (isAiRunning) {
                            binding.orbView.setState(OrbAnimationView.State.LISTENING)
                            binding.statusText.text = if (isMuted) "Microphone Muted 🔇" else "Sun rahi hoon... 🎙️"
                        } else {
                            binding.orbView.setState(OrbAnimationView.State.IDLE)
                            binding.statusText.text = "Tap START to talk ✨"
                        }
                        animateRedOverlay(false)
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

    private fun handleIncomingCallIntent(intent: Intent?) {
        if (intent?.action == CallMonitorService.ACTION_INCOMING_CALL) {
            val callerName = intent.getStringExtra(CallMonitorService.EXTRA_CALLER_NAME) ?: "Someone"
            announceCall(callerName)
        }
    }

    private fun announceCall(callerName: String) {
        isInCallMode = true
        val announcement = "Sir, $callerName ka call aa raha hai. Uthau ya reject karu?"
        addMyraMessage(announcement)
        geminiLive?.sendText(announcement)

        handler.postDelayed({
            startCallDecisionRecognizer()
        }, 4500)
    }

    private fun startCallDecisionRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isInCallMode = false
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.lowercase() ?: ""
                    Log.i(TAG, "Call decision text: $text")

                    if (text.contains("uthao") || text.contains("haan") || text.contains("accept") || text.contains("pick")) {
                        viewModel.acceptCall()
                        addMyraMessage("Call utha diya sir.")
                    } else if (text.contains("reject") || text.contains("nahi") || text.contains("mat") || text.contains("cut")) {
                        viewModel.rejectCall()
                        addMyraMessage("Call reject kar diya sir.")
                    }
                    isInCallMode = false
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun toggleMute() {
        isMuted = !isMuted
        audioEngine?.isMuted = isMuted
        binding.micButton.setImageResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on)
        binding.statusText.text = if (isMuted) "Microphone Muted 🔇" else "Sun rahi hoon... 🎙️"
    }

    private fun interruptMyra() {
        audioEngine?.clearPlaybackQueue()
        geminiLive?.sendInterrupt()
        if (isAiRunning) {
            binding.orbView.setState(OrbAnimationView.State.LISTENING)
            binding.statusText.text = "Stopped. Sun rahi hoon... 🎙️"
        }
        Toast.makeText(this, "MYRA stopped speaking", Toast.LENGTH_SHORT).show()
    }

    private fun addUserMessage(text: String) {
        chatAdapter.addMessage(ChatMessage(text = text, isUser = true))
        binding.chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun addMyraMessage(text: String) {
        chatAdapter.addMessage(ChatMessage(text = text, isUser = false))
        binding.chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun animateRedOverlay(show: Boolean) {
        val targetAlpha = if (show) 0.08f else 0f
        ValueAnimator.ofFloat(binding.redOverlay.alpha, targetAlpha).apply {
            duration = if (show) 300L else 500L
            addUpdateListener {
                binding.redOverlay.alpha = it.animatedValue as Float
            }
            start()
        }
    }

    private fun startStatusUpdates() {
        handler.post(statusUpdateRunnable)
    }

    private fun updateSystemStatus() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        binding.batteryText.text = "$batLevel%"

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val usedMem = memInfo.totalMem - memInfo.availMem
        val ramPercent = ((usedMem.toDouble() / memInfo.totalMem) * 100).toInt()
        binding.ramText.text = "RAM: $ramPercent%"

        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        binding.timeText.text = timeStr
    }

    override fun onPause() {
        super.onPause()
        audioEngine?.isMuted = true
    }

    override fun onResume() {
        super.onResume()
        if (isAiRunning && !isMuted) {
            audioEngine?.isMuted = false
        }
        updateSystemStatus()

        val currentSig = currentConfigSignature()
        if (isAiRunning && geminiLive != null && lastConfigSignature != null && currentSig != lastConfigSignature) {
            lastConfigSignature = currentSig
            binding.statusText.text = "Reconnecting with updated settings..."
            hasGreetedThisSession = false
            geminiLive?.reconnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statusUpdateRunnable)
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: Exception) {}

        speechRecognizer?.destroy()
        geminiLive?.disconnect()
        audioEngine?.release()
    }
}
