package com.myra.assistant.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Optional "Hey MYRA" wake-word listener active only while the user enables
 * Wake Word mode in Settings and MYRA's full voice stream is currently idle.
 */
class WakeWordManager(
    private val context: Context,
    private val onWakeWordDetected: (initialCommand: String?) -> Unit
) {
    companion object {
        private const val TAG = "WakeWordManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile private var isEnabled = false
    @Volatile private var isDestroyed = false

    private val restartRunnable = Runnable {
        if (isEnabled && !isDestroyed && !MyraVoiceSession.isRunning.value) {
            beginListeningCycle()
        }
    }

    fun start() {
        if (isDestroyed) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            MyraVoiceSession.setWakeWordActive(false)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            MyraVoiceSession.setWakeWordActive(false)
            return
        }
        if (MyraVoiceSession.isRunning.value) {
            stop()
            return
        }
        isEnabled = true
        MyraVoiceSession.setWakeWordActive(true)
        mainHandler.post { beginListeningCycle() }
    }

    fun stop() {
        isEnabled = false
        MyraVoiceSession.setWakeWordActive(false)
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.post { destroyRecognizer() }
    }

    fun destroy() {
        isDestroyed = true
        stop()
    }

    private fun beginListeningCycle() {
        if (!isEnabled || isDestroyed || MyraVoiceSession.isRunning.value) return
        destroyRecognizer()
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (isEnabled && !isDestroyed && !MyraVoiceSession.isRunning.value) {
                        val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 600L
                        mainHandler.postDelayed(restartRunnable, delay)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val matched = matches.firstOrNull { containsWakeWord(it) }
                    if (matched != null) {
                        val trailing = extractTrailingCommand(matched)
                        stop()
                        onWakeWordDetected(trailing)
                    } else if (isEnabled && !isDestroyed && !MyraVoiceSession.isRunning.value) {
                        mainHandler.postDelayed(restartRunnable, 300L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (matches.any { containsWakeWordExact(it) }) {
                        stop()
                        onWakeWordDetected(null)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Wake word start failed: ${e.message}")
            MyraVoiceSession.setWakeWordActive(false)
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun containsWakeWord(phrase: String): Boolean {
        val lower = phrase.lowercase(Locale.ROOT).trim()
        return lower.contains("hey myra") ||
            lower.contains("hello myra") ||
            lower.contains("hi myra") ||
            lower.contains("hey mira") ||
            lower.contains("myra ")
    }

    private fun containsWakeWordExact(phrase: String): Boolean {
        val lower = phrase.lowercase(Locale.ROOT).trim()
        return lower == "hey myra" || lower == "hey mira" || lower == "hello myra"
    }

    private fun extractTrailingCommand(phrase: String): String? {
        val cleaned = phrase.replace(
            Regex("^(hey|hello|hi)?\\s*(myra|mira)[,\\s]*", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        return cleaned.takeIf { it.isNotEmpty() }
    }
}
