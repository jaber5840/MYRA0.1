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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import com.myra.assistant.viewmodel.MainViewModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var geminiLive: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingTtsText: String? = null

    private val chatAdapter = ChatAdapter()

    private val inputBuffer = StringBuilder()
    private val outputBuffer = StringBuilder()

    private var isAiRunning = false
    private var isMuted = true
    private var isInCallMode = false
    private var pendingStartAfterPermission = false
    private var hasReceivedLiveAudioInTurn = false
    private var hasTriggeredGreetingFallback = false
    private var lastConfigSignature: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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

        initTts()
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
        // Throttle automatic launch checks to once every 6 hours
        if (now - lastCheck < 6 * 60 * 60 * 1000L) return
        prefs.edit().putLong("last_update_check_ms", now).apply()

        GitHubUpdateChecker.checkLatestRelease(this) { result ->
            if (result is GitHubUpdateChecker.CheckResult.UpdateAvailable) {
                val release = result.release
                val skippedTag = prefs.getString("skipped_update_tag", "")
                if (release.tagName == skippedTag) return@checkLatestRelease

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("MYRA Update Available (${release.tagName}) 🚀")
                        .setMessage(
                            "Installed: v${BuildConfig.VERSION_NAME}\n" +
                                "New Version: ${release.tagName}\n\n" +
                                release.releaseNotes.take(350)
                        )
                        .setPositiveButton(if (release.apkDownloadUrl != null) "UPDATE NOW" else "OPEN RELEASE") { _, _ ->
                            GitHubUpdateChecker.openReleaseInBrowser(
                                this@MainActivity,
                                release,
                                preferApkLink = release.apkDownloadUrl != null
                            )
                        }
                        .setNeutralButton("SKIP") { _, _ ->
                            prefs.edit().putString("skipped_update_tag", release.tagName).apply()
                        }
                        .setNegativeButton("LATER", null)
                        .show()
                }
            }
        }
    }

    private fun updateTtsLanguage() {
        val tts = textToSpeech ?: return
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val language = prefs.getString("language_mode", "Banglish") ?: "Banglish"
        val candidates = when (language) {
            "Bangla" -> listOf(Locale("bn", "BD"), Locale("bn", "IN"), Locale("en", "IN"), Locale.US)
            "English" -> listOf(Locale.US, Locale.ENGLISH, Locale.getDefault())
            "Hindi" -> listOf(Locale("hi", "IN"), Locale("en", "IN"), Locale.US)
            else -> listOf(Locale("bn", "BD"), Locale("en", "IN"), Locale.US, Locale.ENGLISH)
        }
        for (loc in candidates) {
            val res = tts.isLanguageAvailable(loc)
            if (res >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = loc
                break
            }
        }
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                updateTtsLanguage()
                textToSpeech?.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                isTtsReady = true
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        audioEngine?.setExternalSpeaking(true)
                        runOnUiThread {
                            binding.orbView.setState(OrbAnimationView.State.SPEAKING)
                            binding.statusText.text = "Bol rahi hoon... 🔊"
                            binding.waveformView.setAmplitude(0.75f)
                            animateRedOverlay(true)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        audioEngine?.setExternalSpeaking(false)
                        runOnUiThread {
                            if (isAiRunning) {
                                binding.orbView.setState(OrbAnimationView.State.LISTENING)
                                binding.statusText.text = if (isMuted) "Microphone Muted 🔇" else "Sun rahi hoon... 🎙️"
                            } else {
                                binding.orbView.setState(OrbAnimationView.State.IDLE)
                                binding.statusText.text = "Tap START to talk ✨"
                            }
                            binding.waveformView.setAmplitude(0.1f)
                            animateRedOverlay(false)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onDone(utteranceId)
                    }
                })
                pendingTtsText?.let { pending ->
                    pendingTtsText = null
                    speakWithTts(pending)
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

    private val greetingFallbackRunnable = Runnable {
        if (isAiRunning && !hasReceivedLiveAudioInTurn) {
            Log.i(TAG, "Live audio did not arrive in time; triggering active REST + voice fallback greeting")
            speakActiveGeminiGreeting()
        }
    }

    private fun startAiAssistant() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions(autoStart = true)
            return
        }

        isAiRunning = true
        isMuted = false
        hasReceivedLiveAudioInTurn = false
        hasTriggeredGreetingFallback = false
        updateControlButtons()

        binding.orbView.setState(OrbAnimationView.State.THINKING)
        binding.statusText.text = "Starting MYRA... ✨"

        initGeminiLive()
        audioEngine?.isMuted = false
        audioEngine?.startPlayback()
        audioEngine?.startRecording()

        if (geminiLive?.isConnected == true) {
            sendGreeting()
        } else {
            geminiLive?.connect()
        }

        handler.removeCallbacks(greetingFallbackRunnable)
        handler.postDelayed(greetingFallbackRunnable, 3000L)
    }

    private fun stopAiAssistant() {
        isAiRunning = false
        isMuted = true
        handler.removeCallbacks(greetingFallbackRunnable)
        textToSpeech?.stop()
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
                        binding.statusText.text = "MYRA active — bol rahi hoon... 🔊"
                        sendGreeting()
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
                    Log.e(TAG, "Gemini Error: $err")
                    if (isAiRunning) {
                        binding.statusText.text = err
                        if (!hasReceivedLiveAudioInTurn) {
                            handler.removeCallbacks(greetingFallbackRunnable)
                            speakActiveGeminiGreeting()
                        }
                    }
                }

                onAudioReceived = { pcmBytes ->
                    hasReceivedLiveAudioInTurn = true
                    handler.removeCallbacks(greetingFallbackRunnable)
                    audioEngine?.queueAudio(pcmBytes)
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
                        if (!hasReceivedLiveAudioInTurn) {
                            speakWithTts(myraText)
                        }
                    }

                    hasReceivedLiveAudioInTurn = false
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

    private fun speakActiveGeminiGreeting() {
        if (hasTriggeredGreetingFallback || hasReceivedLiveAudioInTurn || !isAiRunning) return
        hasTriggeredGreetingFallback = true
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        var apiKey = prefs.getString("api_key", "")?.trim() ?: ""
        if (apiKey.isEmpty()) apiKey = BuildConfig.GEMINI_API_KEY
        val name = prefs.getString("user_name", "Alex") ?: "Alex"
        val language = prefs.getString("language_mode", "Banglish") ?: "Banglish"

        val fallbackLocalGreeting = when (language) {
            "Bangla" -> "হ্যালো $name! আমি মাইরা বলছি, বলো তোমাকে কীভাবে সাহায্য করতে পারি?"
            "English" -> "Hello $name! I am MYRA, how can I help you today?"
            "Hindi" -> "Hello $name! Main MYRA hoon, bataiye main aapki kya help kar sakti hoon?"
            else -> "Hey $name! Ami MYRA bolchi, bolo ki help lagbe tomar?"
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            addMyraMessage(fallbackLocalGreeting)
            speakWithTts(fallbackLocalGreeting)
            return
        }

        val prompt = when (language) {
            "Bangla" -> "You are MYRA. Greet $name warmly in Bengali (বাংলা) in 1 short spoken sentence."
            "English" -> "You are MYRA. Greet $name warmly in English in 1 short spoken sentence."
            "Hindi" -> "You are MYRA. Greet $name warmly in Hindi/Hinglish in 1 short spoken sentence."
            else -> "You are MYRA. Greet $name warmly in Banglish (Bengali + English) in 1 short spoken sentence."
        }

        // Use cached active models immediately if available for instant response
        val cachedJson = prefs.getString("cached_active_chat_models", null)
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val arr = JSONArray(cachedJson)
                val cachedList = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val m = arr.optString(i)
                    if (m.isNotEmpty()) cachedList.add(m)
                }
                if (cachedList.isNotEmpty()) {
                    tryFallbackGenerate(apiKey, prompt, cachedList, 0, fallbackLocalGreeting)
                    return
                }
            } catch (_: Exception) {}
        }

        // Otherwise discover active generateContent model and fetch a live greeting
        val listReq = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        httpClient.newCall(listReq).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    if (isAiRunning && !hasReceivedLiveAudioInTurn) {
                        addMyraMessage(fallbackLocalGreeting)
                        speakWithTts(fallbackLocalGreeting)
                    }
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val listJson = response.body?.string() ?: ""
                val activeModels = mutableListOf<String>()
                try {
                    val arr = JSONObject(listJson).optJSONArray("models")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val id = obj.optString("name", "").removePrefix("models/")
                            val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue
                            var supportsGen = false
                            for (m in 0 until methods.length()) {
                                if (methods.optString(m) == "generateContent") supportsGen = true
                            }
                            if (supportsGen && id.startsWith("gemini-") && id.contains("flash") &&
                                !id.contains("tts") && !id.contains("image") && !id.contains("exp")
                            ) {
                                activeModels.add(id)
                            }
                        }
                    }
                } catch (_: Exception) {}

                val modelsToTry = if (activeModels.isNotEmpty()) {
                    prefs.edit().putString("cached_active_chat_models", JSONArray(activeModels.take(6)).toString()).apply()
                    activeModels.take(4)
                } else {
                    listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-3.8-flash")
                }

                tryFallbackGenerate(apiKey, prompt, modelsToTry, 0, fallbackLocalGreeting)
            }
        })
    }

    private fun tryFallbackGenerate(
        apiKey: String,
        prompt: String,
        models: List<String>,
        index: Int,
        fallbackLocalGreeting: String
    ) {
        if (!isAiRunning || hasReceivedLiveAudioInTurn) return
        if (index >= models.size) {
            runOnUiThread {
                addMyraMessage(fallbackLocalGreeting)
                speakWithTts(fallbackLocalGreeting)
            }
            return
        }

        val modelId = models[index]
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                tryFallbackGenerate(apiKey, prompt, models, index + 1, fallbackLocalGreeting)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val str = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val reply = JSONObject(str)
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .trim()
                        runOnUiThread {
                            if (isAiRunning && !hasReceivedLiveAudioInTurn) {
                                addMyraMessage(reply)
                                speakWithTts(reply)
                            }
                        }
                        return
                    } catch (_: Exception) {}
                }
                tryFallbackGenerate(apiKey, prompt, models, index + 1, fallbackLocalGreeting)
            }
        })
    }

    private fun speakWithTts(text: String) {
        if (!isTtsReady) {
            pendingTtsText = text
            return
        }
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "myra_utterance_${System.currentTimeMillis()}")
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
        textToSpeech?.stop()
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
        updateTtsLanguage()
        if (isAiRunning && !isMuted) {
            audioEngine?.isMuted = false
        }
        updateSystemStatus()

        val currentSig = currentConfigSignature()
        if (isAiRunning && geminiLive != null && lastConfigSignature != null && currentSig != lastConfigSignature) {
            lastConfigSignature = currentSig
            binding.statusText.text = "Reconnecting with updated settings..."
            geminiLive?.reconnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statusUpdateRunnable)
        handler.removeCallbacks(greetingFallbackRunnable)
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: Exception) {}

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
        geminiLive?.disconnect()
        audioEngine?.release()
    }
}
