package com.myra.assistant.ui.main

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.myra.assistant.BuildConfig
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.service.MyraOverlayService
import com.myra.assistant.service.MyraVoiceService
import com.myra.assistant.service.MyraVoiceSession
import com.myra.assistant.service.ScreenCaptureService
import com.myra.assistant.service.WakeWordManager
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.ui.settings.SettingsCategory
import com.myra.assistant.ui.theme.MyraTheme
import com.myra.assistant.update.GitHubUpdateChecker
import com.myra.assistant.update.RemoteConfigUpdateManager
import com.myra.assistant.update.UpdateDialogHelper
import com.myra.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeWordManager: WakeWordManager? = null
    private var pendingStartAfterPermission = false
    private var navigatingToSettings = false
    private var overlayPromptShownThisRun = false
    private var isActivityResumed = false
    private var pendingPhotoFile: File? = null

    private val telemetryState = mutableStateOf(HomeTelemetryState())
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
            syncWakeWordState()
            if (pendingStartAfterPermission) {
                pendingStartAfterPermission = false
                startAiAssistant()
            }
        } else {
            pendingStartAfterPermission = false
            Toast.makeText(this, "Microphone permission is required for MYRA", Toast.LENGTH_LONG).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val photoName = pendingPhotoFile?.name ?: "photo"
        if (success) {
            val msg = "Photo captured ($photoName)."
            MyraVoiceSession.addMyraMessage(msg)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Photo capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_CAPTURE
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, captureIntent)
            Toast.makeText(this, "Screen Share ON — MYRA can see your screen", Toast.LENGTH_SHORT).show()

            if (MyraVoiceSession.isRunning.value) {
                sendServiceAction(MyraVoiceService.ACTION_SEND_TEXT) {
                    putExtra(
                        MyraVoiceService.EXTRA_TEXT,
                        "Screen share is now ON. Confirm in 1 short sentence that you can see the user's screen now."
                    )
                    putExtra(MyraVoiceService.EXTRA_ADD_TO_CHAT, false)
                }
            } else if (requireApiKeyAndMic()) {
                val voiceIntent = Intent(this, MyraVoiceService::class.java).apply {
                    action = MyraVoiceService.ACTION_START
                }
                ContextCompat.startForegroundService(this, voiceIntent)
            }

            maybePromptOverlayPermission()
        } else {
            MyraVoiceSession.setScreenSharing(false)
            Toast.makeText(this, "Screen share permission cancelled", Toast.LENGTH_SHORT).show()
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        MyraVoiceSession.loadPersistedState(this)
        updateSystemStatus()

        wakeWordManager = WakeWordManager(this) { trailingCommand ->
            runOnUiThread {
                Toast.makeText(this, "Hey MYRA detected", Toast.LENGTH_SHORT).show()
                if (!trailingCommand.isNullOrBlank()) {
                    sendTypedMessageToMyra(trailingCommand)
                } else {
                    startAiAssistant()
                }
            }
        }

        setContent {
            val themeMode by MyraVoiceSession.themeMode.collectAsStateWithLifecycle()
            MyraTheme(themeMode = themeMode) {
                val isRunning by MyraVoiceSession.isRunning.collectAsStateWithLifecycle()
                val isMuted by MyraVoiceSession.isMuted.collectAsStateWithLifecycle()
                val isSpeaking by MyraVoiceSession.isSpeaking.collectAsStateWithLifecycle()
                val orbState by MyraVoiceSession.orbState.collectAsStateWithLifecycle()
                val statusText by MyraVoiceSession.statusText.collectAsStateWithLifecycle()
                val amplitude by MyraVoiceSession.amplitude.collectAsStateWithLifecycle()
                val messages by MyraVoiceSession.messages.collectAsStateWithLifecycle()
                val isScreenSharing by MyraVoiceSession.isScreenSharing.collectAsStateWithLifecycle()
                val isWakeWordActive by MyraVoiceSession.isWakeWordActive.collectAsStateWithLifecycle()
                val isFlashlightOn by MyraVoiceSession.isFlashlightOn.collectAsStateWithLifecycle()
                val pendingConfirmation by MyraVoiceSession.pendingConfirmation.collectAsStateWithLifecycle()
                val commandHistory by MyraVoiceSession.commandHistory.collectAsStateWithLifecycle()
                val telemetry by telemetryState

                HomeScreen(
                    isRunning = isRunning,
                    isMuted = isMuted,
                    isSpeaking = isSpeaking,
                    isScreenSharing = isScreenSharing,
                    isWakeWordActive = isWakeWordActive,
                    isFlashlightOn = isFlashlightOn,
                    orbState = orbState,
                    statusText = statusText,
                    amplitude = amplitude,
                    messages = messages,
                    commandHistory = commandHistory,
                    pendingConfirmation = pendingConfirmation,
                    telemetry = telemetry,
                    onStartStopClick = {
                        if (MyraVoiceSession.isRunning.value) {
                            stopAiAssistant()
                        } else {
                            startAiAssistant()
                        }
                    },
                    onScreenShareClick = {
                        toggleScreenShare()
                    },
                    onMicClick = {
                        if (!MyraVoiceSession.isRunning.value) {
                            startAiAssistant()
                        } else {
                            toggleMute()
                        }
                    },
                    onMicLongPress = {
                        interruptMyra()
                    },
                    onOrbClick = {
                        if (MyraVoiceSession.isRunning.value) {
                            stopAiAssistant()
                        } else {
                            startAiAssistant()
                        }
                    },
                    onSendMessage = { text ->
                        sendTypedMessageToMyra(text)
                    },
                    onExecuteQuickCommand = { cmd ->
                        viewModel.executeCommand(cmd)
                    },
                    onConfirmPending = {
                        viewModel.confirmPendingAction()
                    },
                    onCancelPending = {
                        viewModel.cancelPendingAction()
                    },
                    onClearHistory = {
                        MyraVoiceSession.clearCommandHistory(this@MainActivity)
                    },
                    onCheckUpdatesClick = {
                        checkForUpdatesManually()
                    },
                    onOpenOverlaySettings = {
                        navigatingToSettings = true
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    onOpenAccessibilitySettings = {
                        navigatingToSettings = true
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenPermissionsSettings = {
                        navigatingToSettings = true
                        startActivity(
                            Intent(this@MainActivity, SettingsActivity::class.java).apply {
                                putExtra(SettingsActivity.EXTRA_INITIAL_CATEGORY, SettingsCategory.SYSTEM_PERMISSIONS.name)
                            }
                        )
                    },
                    onSettingsClick = {
                        navigatingToSettings = true
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                )
            }
        }

        observeSessionErrors()
        observeScreenShareCommands()
        observeCameraCaptureCommands()
        observeRunningForWakeWord()
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
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
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

    private fun observeSessionErrors() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MyraVoiceSession.errorEvents.collect { err ->
                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeScreenShareCommands() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MyraVoiceSession.screenShareCommandEvents.collect { enable ->
                    if (enable && !MyraVoiceSession.isScreenSharing.value) {
                        requestStartScreenShare()
                    } else if (!enable && MyraVoiceSession.isScreenSharing.value) {
                        stopScreenShare()
                    }
                }
            }
        }
    }

    private fun observeCameraCaptureCommands() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MyraVoiceSession.cameraCaptureEvents.collect {
                    launchTakePhotoFlow()
                }
            }
        }
    }

    private fun observeRunningForWakeWord() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MyraVoiceSession.isRunning.collect {
                    syncWakeWordState()
                }
            }
        }
    }

    private fun launchTakePhotoFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Grant Camera permission in Settings > Permission Manager first", Toast.LENGTH_SHORT).show()
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }
        try {
            val photosDir = File(cacheDir, "photos").apply { mkdirs() }
            val photoFile = File(photosDir, "MYRA_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = photoFile
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            navigatingToSettings = true
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to launch camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncWakeWordState() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val wakeWordEnabled = prefs.getBoolean("wake_word_enabled", false)
        if (isActivityResumed && wakeWordEnabled && !MyraVoiceSession.isRunning.value) {
            wakeWordManager?.start()
        } else {
            wakeWordManager?.stop()
        }
    }

    private fun toggleScreenShare() {
        if (MyraVoiceSession.isScreenSharing.value) {
            stopScreenShare()
        } else {
            requestStartScreenShare()
        }
    }

    private fun requestStartScreenShare() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            Toast.makeText(this, "Screen capture is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        navigatingToSettings = true
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopScreenShare() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP_CAPTURE
        }
        try {
            startService(stopIntent)
        } catch (_: Exception) {
            MyraVoiceSession.setScreenSharing(false)
        }
        Toast.makeText(this, "Screen Share OFF", Toast.LENGTH_SHORT).show()
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

    private fun checkForUpdatesManually() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        RemoteConfigUpdateManager.checkForUpdates(this, forceRefresh = true) { outcome ->
            runOnUiThread {
                when (val result = outcome.checkResult) {
                    is GitHubUpdateChecker.CheckResult.UpdateAvailable -> {
                        UpdateDialogHelper.showUpdateDialog(
                            activity = this@MainActivity,
                            release = result.release,
                            isMandatory = outcome.isMandatory
                        )
                    }
                    is GitHubUpdateChecker.CheckResult.UpToDate -> {
                        Toast.makeText(
                            this@MainActivity,
                            "MYRA v${result.currentVersion} is up to date",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is GitHubUpdateChecker.CheckResult.Error -> {
                        Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCallIntent(intent)
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

    private fun requireApiKeyAndMic(): Boolean {
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
                    navigatingToSettings = true
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("CANCEL", null)
                .show()
            return false
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions(autoStart = true)
            return false
        }

        return true
    }

    private fun sendTypedMessageToMyra(text: String) {
        if (!requireApiKeyAndMic()) return
        val intent = Intent(this, MyraVoiceService::class.java).apply {
            action = MyraVoiceService.ACTION_SEND_TEXT
            putExtra(MyraVoiceService.EXTRA_TEXT, text)
            putExtra(MyraVoiceService.EXTRA_ADD_TO_CHAT, true)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun startAiAssistant() {
        if (!requireApiKeyAndMic()) return

        val intent = Intent(this, MyraVoiceService::class.java).apply {
            action = MyraVoiceService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)

        maybePromptOverlayPermission()
    }

    private fun maybePromptOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
            if (overlayPromptShownThisRun || prefs.getBoolean("overlay_prompt_dismissed", false)) return
            overlayPromptShownThisRun = true
            AlertDialog.Builder(this)
                .setTitle("Enable Floating Siri Orb ✨")
                .setMessage("Allow MYRA to display over other apps so the glowing JARVIS/Siri orb appears on screen when you exit the app.")
                .setPositiveButton("ENABLE") { _, _ ->
                    navigatingToSettings = true
                    val overlayIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(overlayIntent)
                }
                .setNegativeButton("LATER") { _, _ ->
                    prefs.edit().putBoolean("overlay_prompt_dismissed", true).apply()
                }
                .show()
        }
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

    private fun startStatusUpdates() {
        handler.post(statusUpdateRunnable)
    }

    private fun updateSystemStatus() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val usedMem = memInfo.totalMem - memInfo.availMem
        val ramPercent = if (memInfo.totalMem > 0L) {
            ((usedMem.toDouble() / memInfo.totalMem) * 100).toInt()
        } else 0

        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Alex") ?: "Alex"
        val personality = prefs.getString("personality_mode", "GF") ?: "GF"
        val language = prefs.getString("language_mode", "Banglish") ?: "Banglish"
        val voice = prefs.getString("gemini_voice", "Aoede") ?: "Aoede"

        telemetryState.value = HomeTelemetryState(
            userName = userName,
            batteryPercent = batLevel,
            ramPercent = ramPercent,
            timeText = timeStr,
            personalityLabel = personality,
            languageLabel = language,
            voiceLabel = voice,
            isOverlayEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this),
            isAccessibilityEnabled = AccessibilityHelperService.isEnabled(this)
        )
    }

    override fun onStart() {
        super.onStart()
        MyraVoiceSession.isMainUiActive = true
        navigatingToSettings = false
        try {
            stopService(Intent(this, MyraOverlayService::class.java))
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        updateSystemStatus()
        syncWakeWordState()
        if (MyraVoiceSession.isRunning.value) {
            sendServiceAction(MyraVoiceService.ACTION_RECONNECT_IF_CONFIG_CHANGED)
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        wakeWordManager?.stop()
    }

    override fun onStop() {
        super.onStop()
        MyraVoiceSession.isMainUiActive = false
        if (!isChangingConfigurations && !isFinishing && !navigatingToSettings &&
            MyraVoiceSession.isRunning.value &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))
        ) {
            try {
                val overlayIntent = Intent(this, MyraOverlayService::class.java).apply {
                    action = MyraOverlayService.ACTION_SHOW_OVERLAY
                }
                ContextCompat.startForegroundService(this, overlayIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to show floating Siri orb: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statusUpdateRunnable)
        wakeWordManager?.destroy()
        wakeWordManager = null
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: Exception) {}

        speechRecognizer?.destroy()
        // MyraVoiceService stays alive in the background until user explicitly taps STOP
    }
}
