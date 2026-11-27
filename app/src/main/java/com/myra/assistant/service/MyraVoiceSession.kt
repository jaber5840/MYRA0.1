package com.myra.assistant.service

import android.content.Context
import com.myra.assistant.model.AppCommand
import com.myra.assistant.model.CapturedNotification
import com.myra.assistant.model.CommandHistoryItem
import com.myra.assistant.model.PendingConfirmation
import com.myra.assistant.ui.main.ChatMessage
import com.myra.assistant.ui.main.OrbAnimationView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

object MyraVoiceSession {

    private const val PREFS_NAME = "myra_prefs"
    private const val KEY_COMMAND_HISTORY = "command_history_json"
    private const val KEY_THEME_MODE = "theme_mode"

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isMuted = MutableStateFlow(true)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _orbState = MutableStateFlow(OrbAnimationView.State.IDLE)
    val orbState: StateFlow<OrbAnimationView.State> = _orbState.asStateFlow()

    private val _statusText = MutableStateFlow("Tap START to talk ✨")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _amplitude = MutableStateFlow(0.05f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _screenShareCommandEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val screenShareCommandEvents: SharedFlow<Boolean> = _screenShareCommandEvents.asSharedFlow()

    private val _cameraCaptureEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val cameraCaptureEvents: SharedFlow<Boolean> = _cameraCaptureEvents.asSharedFlow()

    private val _isWakeWordActive = MutableStateFlow(false)
    val isWakeWordActive: StateFlow<Boolean> = _isWakeWordActive.asStateFlow()

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _themeMode = MutableStateFlow("DARK")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<CommandHistoryItem>>(emptyList())
    val commandHistory: StateFlow<List<CommandHistoryItem>> = _commandHistory.asStateFlow()

    private val _recentNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val recentNotifications: StateFlow<List<CapturedNotification>> = _recentNotifications.asStateFlow()

    @Volatile
    var isMainUiActive: Boolean = false

    @Volatile
    var imageFrameSink: ((String) -> Unit)? = null

    fun loadPersistedState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _themeMode.value = prefs.getString(KEY_THEME_MODE, "DARK") ?: "DARK"

        val rawHistory = prefs.getString(KEY_COMMAND_HISTORY, null)
        if (!rawHistory.isNullOrBlank()) {
            try {
                val arr = JSONArray(rawHistory)
                val items = mutableListOf<CommandHistoryItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    items.add(
                        CommandHistoryItem(
                            id = obj.optLong("id", i.toLong()),
                            commandType = obj.optString("type", ""),
                            inputSummary = obj.optString("input", ""),
                            resultSummary = obj.optString("result", ""),
                            timestamp = obj.optLong("ts", System.currentTimeMillis()),
                            succeeded = obj.optBoolean("ok", true)
                        )
                    )
                }
                _commandHistory.value = items
            } catch (_: Exception) {}
        }
    }

    fun setThemeMode(context: Context, mode: String) {
        val normalized = when (mode.uppercase()) {
            "LIGHT" -> "LIGHT"
            "SYSTEM" -> "SYSTEM"
            else -> "DARK"
        }
        _themeMode.value = normalized
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, normalized)
            .apply()
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun setSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun setOrbState(state: OrbAnimationView.State) {
        _orbState.value = state
    }

    fun setStatusText(text: String) {
        _statusText.value = text
    }

    fun setAmplitude(amp: Float) {
        _amplitude.value = amp
    }

    fun setScreenSharing(sharing: Boolean) {
        _isScreenSharing.value = sharing
    }

    fun setWakeWordActive(active: Boolean) {
        _isWakeWordActive.value = active
    }

    fun setFlashlightOn(on: Boolean) {
        _isFlashlightOn.value = on
    }

    fun requestScreenShareToggle(enable: Boolean) {
        _screenShareCommandEvents.tryEmit(enable)
    }

    fun requestCameraPhotoCapture(useFrontCamera: Boolean = false) {
        _cameraCaptureEvents.tryEmit(useFrontCamera)
    }

    fun emitScreenFrame(base64Jpeg: String) {
        if (_isScreenSharing.value) {
            imageFrameSink?.invoke(base64Jpeg)
        }
    }

    fun setPendingConfirmation(confirmation: PendingConfirmation?) {
        _pendingConfirmation.value = confirmation
    }

    fun recordCommandHistory(context: Context, item: CommandHistoryItem) {
        val updated = (listOf(item) + _commandHistory.value).take(40)
        _commandHistory.value = updated
        persistHistory(context, updated)
    }

    fun clearCommandHistory(context: Context) {
        _commandHistory.value = emptyList()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_COMMAND_HISTORY)
            .apply()
    }

    private fun persistHistory(context: Context, items: List<CommandHistoryItem>) {
        try {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("type", item.commandType)
                        put("input", item.inputSummary)
                        put("result", item.resultSummary)
                        put("ts", item.timestamp)
                        put("ok", item.succeeded)
                    }
                )
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_COMMAND_HISTORY, arr.toString())
                .apply()
        } catch (_: Exception) {}
    }

    fun addCapturedNotification(notification: CapturedNotification) {
        _recentNotifications.update { current ->
            (listOf(notification) + current).take(15)
        }
    }

    fun replaceCapturedNotifications(notifications: List<CapturedNotification>) {
        _recentNotifications.value = notifications
    }

    fun addUserMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        _messages.update { current ->
            current + ChatMessage(text = clean, isUser = true)
        }
    }

    fun addMyraMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        _messages.update { current ->
            val last = current.lastOrNull()
            if (last != null && !last.isUser && last.text == clean) {
                current
            } else {
                current + ChatMessage(text = clean, isUser = false)
            }
        }
    }

    fun emitError(message: String) {
        _errorEvents.tryEmit(message)
    }
}
