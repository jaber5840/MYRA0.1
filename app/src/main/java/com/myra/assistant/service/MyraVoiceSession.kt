package com.myra.assistant.service

import com.myra.assistant.ui.main.ChatMessage
import com.myra.assistant.ui.main.OrbAnimationView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object MyraVoiceSession {

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
