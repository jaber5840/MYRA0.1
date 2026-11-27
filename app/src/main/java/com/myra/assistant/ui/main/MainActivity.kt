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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.myra.assistant.BuildConfig
import com.myra.assistant.R
import com.myra.assistant.databinding.ActivityMainBinding
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.service.MyraVoiceService
import com.myra.assistant.service.MyraVoiceSession
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.update.GitHubUpdateChecker
import com.myra.assistant.update.RemoteConfigUpdateManager
import com.myra.assistant.update.UpdateDialogHelper
import com.myra.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var speechRecognizer: SpeechRecognizer? = null
    private val chatAdapter = ChatAdapter()

    private var pendingStartAfterPermission = false
    private var lastRedOverlayState = false

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
            sendServiceAction(MyraVoiceService.ACTION_SET_IN_CALL_MODE) {
                putExtra(MyraVoiceService.EXTRA_IN_CALL_MODE, false)
            }
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
        observeVoiceSession()
        checkAndRequestPermissions(autoStart = false)
        startStatusUpdates()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, IntentFilter(CallMonitorService.ACTION_CALL_ENDED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callEndedReceiver, IntentFilter(CallMonitorService.ACTION_CALL_ENDED))
        }

        viewModel.commandResult.observe(this) { result ->
            if (!result.isNullOrEmpty()) {
                MyraVoiceSession.addMyraMessage(result)
                if (MyraVoiceSession.isRunning.value) {
                    sendServiceAction(MyraVoiceService.ACTION_SEND_TEXT) {
                        putExtra(MyraVoiceService.EXTRA_TEXT, result)
                    }
                }
            }
        }

        handleIncomingCallIntent(intent)
        checkForAppUpdateOnLaunch()
    }

    private fun observeVoiceSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    MyraVoiceSession.isRunning.collect { running ->
                        updateControlButtons(running, MyraVoiceSession.isMuted.value)
                    }
                }
                launch {
                    MyraVoiceSession.isMuted.collect { muted ->
                        updateControlButtons(MyraVoiceSession.isRunning.value, muted)
                    }
                }
                launch {
                    MyraVoiceSession.isSpeaking.collect { speaking ->
                        if (speaking != lastRedOverlayState) {
                            lastRedOverlayState = speaking
                            animateRedOverlay(speaking)
                        }
                    }
                }
                launch {
                    MyraVoiceSession.orbState.collect { state ->
                        binding.orbView.setState(state)
                    }
                }
                launch {
                    MyraVoiceSession.statusText.collect { text ->
                        binding.statusText.text = text
                    }
                }
                launch {
                    MyraVoiceSession.amplitude.collect { amp ->
                        binding.waveformView.setAmplitude(amp)
                        binding.orbView.amplitude = amp
                    }
                }
                launch {
                    MyraVoiceSession.messages.collect { messages ->
                        chatAdapter.submitMessages(messages)
                        if (messages.isNotEmpty()) {
                            binding.chatRecycler.smoothScrollToPosition(messages.size - 1)
                        }
                    }
                }
                launch {
                    MyraVoiceSession.errorEvents.collect { err ->
                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun checkForAppUpdateOnLaunch() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong("last_update_check_ms", 0L)
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
            if (MyraVoiceSession.isRunning.value) {
                stopAiAssistant()
            } else {
                startAiAssistant()
            }
        }

        binding.orbView.setOnClickListener {
            if (!MyraVoiceSession.isRunning.value) {
                startAiAssistant()
            }
        }

        binding.micButton.setOnClickListener {
            if (!MyraVoiceSession.isRunning.value) {
                startAiAssistant()
            } else {
                toggleMute()
            }
        }

        binding.micButton.setOnLongClickListener {
            interruptMyra()
            true
        }

        updateControlButtons(MyraVoiceSession.isRunning.value, MyraVoiceSession.isMuted.value)
    }

    private fun updateControlButtons(isAiRunning: Boolean, isMuted: Boolean) {
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
            MyraVoiceSession.setRunning(false)
            MyraVoiceSession.setMuted(true)
            MyraVoiceSession.setOrbState(OrbAnimationView.State.IDLE)
            MyraVoiceSession.setStatusText("Gemini API Key missing — add in Settings 🔑")
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

        val intent = Intent(this, MyraVoiceService::class.java).apply {
            action = MyraVoiceService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAiAssistant() {
        sendServiceAction(MyraVoiceService.ACTION_STOP)
    }

    private fun toggleMute() {
        sendServiceAction(MyraVoiceService.ACTION_TOGGLE_MUTE)
    }

    private fun interruptMyra() {
        sendServiceAction(MyraVoiceService.ACTION_INTERRUPT)
        Toast.makeText(this, "MYRA stopped speaking", Toast.LENGTH_SHORT).show()
    }

    private fun sendServiceAction(action: String, extras: (Intent.() -> Unit)? = null) {
        val intent = Intent(this, MyraVoiceService::class.java).apply {
            this.action = action
            extras?.invoke(this)
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to startForegroundService for $action: ${e.message}")
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun handleIncomingCallIntent(intent: Intent?) {
        if (intent?.action == CallMonitorService.ACTION_INCOMING_CALL) {
            val callerName = intent.getStringExtra(CallMonitorService.EXTRA_CALLER_NAME) ?: "Someone"
            announceCall(callerName)
        }
    }

    private fun announceCall(callerName: String) {
        sendServiceAction(MyraVoiceService.ACTION_SET_IN_CALL_MODE) {
            putExtra(MyraVoiceService.EXTRA_IN_CALL_MODE, true)
        }
        val announcement = "Sir, $callerName ka call aa raha hai. Uthau ya reject karu?"
        MyraVoiceSession.addMyraMessage(announcement)
        if (MyraVoiceSession.isRunning.value) {
            sendServiceAction(MyraVoiceService.ACTION_SEND_TEXT) {
                putExtra(MyraVoiceService.EXTRA_TEXT, announcement)
            }
        }

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
                    sendServiceAction(MyraVoiceService.ACTION_SET_IN_CALL_MODE) {
                        putExtra(MyraVoiceService.EXTRA_IN_CALL_MODE, false)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.lowercase() ?: ""
                    Log.i(TAG, "Call decision text: $text")

                    if (text.contains("uthao") || text.contains("haan") || text.contains("accept") || text.contains("pick")) {
                        viewModel.acceptCall()
                        MyraVoiceSession.addMyraMessage("Call utha diya sir.")
                    } else if (text.contains("reject") || text.contains("nahi") || text.contains("mat") || text.contains("cut")) {
                        viewModel.rejectCall()
                        MyraVoiceSession.addMyraMessage("Call reject kar diya sir.")
                    }
                    sendServiceAction(MyraVoiceService.ACTION_SET_IN_CALL_MODE) {
                        putExtra(MyraVoiceService.EXTRA_IN_CALL_MODE, false)
                    }
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

    override fun onResume() {
        super.onResume()
        updateSystemStatus()
        if (MyraVoiceSession.isRunning.value) {
            sendServiceAction(MyraVoiceService.ACTION_RECONNECT_IF_CONFIG_CHANGED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statusUpdateRunnable)
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: Exception) {}

        speechRecognizer?.destroy()
        // MyraVoiceService stays alive in the background until user explicitly taps STOP
    }
}
