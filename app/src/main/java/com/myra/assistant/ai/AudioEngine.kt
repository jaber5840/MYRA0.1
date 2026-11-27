package com.myra.assistant.ai

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        private const val MIC_SAMPLE_RATE = 16000
        private const val SPEAKER_SAMPLE_RATE = 24000
        private const val CHUNK_SIZE = 1024
        private const val USER_SPEECH_RMS_THRESHOLD = 0.025f
        private const val USER_SPEECH_END_MS = 240L
        private const val AI_POST_SPEECH_COOLDOWN_MS = 180L
        private const val CHUNK_GAP_GRACE_MS = 180L
    }

    @Volatile var isMuted: Boolean = false
    @Volatile var isSpeaking: Boolean = false
        private set

    @Volatile var isUserSpeaking: Boolean = false
        private set

    @Volatile private var isAiTurnActive: Boolean = false
    @Volatile private var isExternalSpeaking: Boolean = false
    @Volatile private var micUnmuteAfterMs: Long = 0L
    @Volatile private var lastAudioQueuedAtMs: Long = 0L
    @Volatile private var lastUserSpeechAtMs: Long = 0L

    @Volatile private var isRecording: Boolean = false
    @Volatile private var isPlaying: Boolean = false

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private val playbackQueue = LinkedBlockingQueue<ByteArray>()

    var onAudioChunkRecorded: ((String) -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onSpeakingStarted: (() -> Unit)? = null
    var onSpeakingStopped: (() -> Unit)? = null
    var onUserSpeechStateChanged: ((Boolean) -> Unit)? = null

    private var recordingThread: Thread? = null
    private var playbackThread: Thread? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SPEAKER_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // Compact buffer for immediate low-latency voice output
            val bufferSize = maxOf(minBufferSize, SPEAKER_SAMPLE_RATE / 6)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SPEAKER_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val builder = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            audioTrack = builder.build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack: ${e.message}", e)
        }
    }

    fun isMicBlockedByAi(): Boolean {
        return isSpeaking || isAiTurnActive || isExternalSpeaking ||
            System.currentTimeMillis() < micUnmuteAfterMs
    }

    fun setExternalSpeaking(speaking: Boolean) {
        isExternalSpeaking = speaking
        if (speaking) {
            if (isUserSpeaking) {
                isUserSpeaking = false
                onUserSpeechStateChanged?.invoke(false)
            }
        } else {
            micUnmuteAfterMs = System.currentTimeMillis() + AI_POST_SPEECH_COOLDOWN_MS
        }
    }

    fun markAiTurnComplete() {
        isAiTurnActive = false
        if (playbackQueue.isEmpty() && isSpeaking) {
            finishSpeakingState()
        }
    }

    fun startRecording() {
        if (isRecording) return

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize, CHUNK_SIZE * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MIC_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state not initialized")
                return
            }

            attachAudioEffects(audioRecord!!.audioSessionId)

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = thread(name = "AudioRecordThread") {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                val buffer = ByteArray(CHUNK_SIZE)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Half-duplex gate: while AI is speaking or cooling down, ignore all mic audio
                        if (isMuted || isMicBlockedByAi()) {
                            if (isUserSpeaking) {
                                isUserSpeaking = false
                                onUserSpeechStateChanged?.invoke(false)
                            }
                            continue
                        }

                        val rms = calculateRms(buffer, read)
                        onAmplitudeChanged?.invoke(rms)

                        val now = System.currentTimeMillis()
                        if (rms >= USER_SPEECH_RMS_THRESHOLD) {
                            lastUserSpeechAtMs = now
                            if (!isUserSpeaking) {
                                isUserSpeaking = true
                                onUserSpeechStateChanged?.invoke(true)
                            }
                        } else if (isUserSpeaking && (now - lastUserSpeechAtMs) >= USER_SPEECH_END_MS) {
                            isUserSpeaking = false
                            onUserSpeechStateChanged?.invoke(false)
                        }

                        val base64 = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        onAudioChunkRecorded?.invoke(base64)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        releaseAudioEffects()
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AEC unavailable: ${e.message}")
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NoiseSuppressor unavailable: ${e.message}")
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AGC unavailable: ${e.message}")
        }
    }

    private fun releaseAudioEffects() {
        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null
        try {
            gainControl?.release()
        } catch (_: Exception) {}
        gainControl = null
    }

    fun stopRecording() {
        isRecording = false
        isUserSpeaking = false
        releaseAudioEffects()
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
        }
    }

    fun startPlayback() {
        if (isPlaying) return
        isPlaying = true

        playbackThread = thread(name = "AudioPlaybackThread") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            while (isPlaying) {
                try {
                    val chunk = playbackQueue.poll(30, TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        if (!isSpeaking) {
                            isSpeaking = true
                            if (isUserSpeaking) {
                                isUserSpeaking = false
                                onUserSpeechStateChanged?.invoke(false)
                            }
                            onSpeakingStarted?.invoke()
                        }

                        val rms = calculateRms(chunk, chunk.size)
                        onAmplitudeChanged?.invoke(rms)

                        audioTrack?.write(chunk, 0, chunk.size)
                    } else if (isSpeaking) {
                        val elapsedSinceLastChunk = System.currentTimeMillis() - lastAudioQueuedAtMs
                        if (!isAiTurnActive || elapsedSinceLastChunk >= CHUNK_GAP_GRACE_MS) {
                            if (playbackQueue.isEmpty() && (!isAiTurnActive || elapsedSinceLastChunk >= CHUNK_GAP_GRACE_MS)) {
                                finishSpeakingState()
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in playback loop: ${e.message}", e)
                }
            }
        }
    }

    private fun finishSpeakingState() {
        if (!isSpeaking && !isAiTurnActive) return
        isSpeaking = false
        isAiTurnActive = false
        micUnmuteAfterMs = System.currentTimeMillis() + AI_POST_SPEECH_COOLDOWN_MS
        onSpeakingStopped?.invoke()
    }

    fun stopPlayback() {
        isPlaying = false
        isAiTurnActive = false
        playbackQueue.clear()
        if (isSpeaking) {
            finishSpeakingState()
        }
    }

    fun queueAudio(pcmData: ByteArray) {
        if (pcmData.isNotEmpty()) {
            lastAudioQueuedAtMs = System.currentTimeMillis()
            isAiTurnActive = true
            playbackQueue.offer(pcmData)
            if (!isPlaying) {
                startPlayback()
            }
        }
    }

    fun clearPlaybackQueue() {
        isAiTurnActive = false
        playbackQueue.clear()
        if (isSpeaking) {
            finishSpeakingState()
        }
    }

    fun release() {
        stopRecording()
        stopPlayback()
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio track: ${e.message}", e)
        }
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += (shortSample * shortSample).toDouble()
            i += 2
        }
        val count = length / 2
        if (count == 0) return 0f
        val rms = sqrt(sum / count)
        return (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
    }
}
