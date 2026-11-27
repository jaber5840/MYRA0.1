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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.myra.assistant.R
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.databinding.ActivityMainBinding
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.ui.settings.SettingsActivity
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

    private var isMuted = false
    private var isInCallMode = false

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
            initGeminiLive()
        } else {
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
        checkAndRequestPermissions()
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

        binding.micButton.setOnClickListener {
            toggleMute()
        }

        binding.micButton.setOnLongClickListener {
            interruptMyra()
            true
        }
    }

    private fun checkAndRequestPermissions() {
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
            requestPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            startSystemServices()
            handler.postDelayed({ initGeminiLive() }, 300)
        }
    }

    private fun startSystemServices() {
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

    private fun initGeminiLive() {
        if (geminiLive != null) return

        geminiLive = GeminiLiveClient(this).apply {
            onConnected = {
                Log.i(TAG, "Gemini Live Connected")
                audioEngine?.startRecording()
                audioEngine?.startPlayback()
                binding.orbView.setState(OrbAnimationView.State.LISTENING)
                binding.statusText.text = "Sun rahi hoon... 🎙️"

                handler.postDelayed({ sendGreeting() }, 600)
            }

            onDisconnected = {
                binding.orbView.setState(OrbAnimationView.State.IDLE)
                binding.statusText.text = "Disconnected. Reconnecting..."
            }

            onError = { err ->
                Log.e(TAG, "Gemini Error: $err")
                binding.statusText.text = err
            }

            onAudioReceived = { pcmBytes ->
                audioEngine?.queueAudio(pcmBytes)
            }

            onOutputTranscript = { text ->
                outputBuffer.append(text)
            }

            onInputTranscript = { text ->
                inputBuffer.append(text)
            }

            onTurnComplete = {
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

        audioEngine = AudioEngine(this).apply {
            onAudioChunkRecorded = { base64 ->
                if (!isInCallMode) {
                    geminiLive?.sendAudioChunk(base64)
                }
            }

            onAmplitudeChanged = { rms ->
                binding.waveformView.setAmplitude(rms)
                binding.orbView.amplitude = rms
            }

            onSpeakingStarted = {
                binding.orbView.setState(OrbAnimationView.State.SPEAKING)
                binding.statusText.text = "Bol rahi hoon... 🔊"
                animateRedOverlay(true)
            }

            onSpeakingStopped = {
                binding.orbView.setState(OrbAnimationView.State.LISTENING)
                binding.statusText.text = "Sun rahi hoon... 🎙️"
                animateRedOverlay(false)
            }
        }

        geminiLive?.connect()
    }

    private fun sendGreeting() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", "Alex") ?: "Alex"
        val personality = prefs.getString("personality_mode", "GF")

        val greeting = when (personality) {
            "Professional" -> "Good day $name. MYRA is online and ready to assist you."
            "Assistant" -> "Hello $name! Main MYRA hoon. Kaise help karun aapki?"
            else -> "Hey $name! Main aa gayi hoon. Kya help chahiye tumhe?"
        }

        geminiLive?.sendText(greeting)
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
        binding.orbView.setState(OrbAnimationView.State.LISTENING)
        binding.statusText.text = "Stopped. Sun rahi hoon... 🎙️"
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
        if (!isMuted) {
            audioEngine?.isMuted = false
        }
        updateSystemStatus()
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
