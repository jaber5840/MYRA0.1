package com.myra.assistant.ai

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        private const val MIC_SAMPLE_RATE = 16000
        private const val SPEAKER_SAMPLE_RATE = 24000
        private const val CHUNK_SIZE = 1024
    }

    @Volatile var isMuted: Boolean = false
    @Volatile var isSpeaking: Boolean = false
        private set

    @Volatile private var isRecording: Boolean = false
    @Volatile private var isPlaying: Boolean = false

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()

    var onAudioChunkRecorded: ((String) -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onSpeakingStarted: (() -> Unit)? = null
    var onSpeakingStopped: (() -> Unit)? = null

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
            val bufferSize = Math.max(minBufferSize, SPEAKER_SAMPLE_RATE * 2)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SPEAKER_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack: ${e.message}", e)
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
            val bufferSize = Math.max(minBufferSize, CHUNK_SIZE * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state not initialized")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = thread(name = "AudioRecordThread") {
                val buffer = ByteArray(CHUNK_SIZE)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        val rms = calculateRms(buffer, read)
                        onAmplitudeChanged?.invoke(rms)

                        if (!isMuted && !isSpeaking) {
                            val base64 = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                            onAudioChunkRecorded?.invoke(base64)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
        }
    }

    fun stopRecording() {
        isRecording = false
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
            while (isPlaying) {
                val chunk = playbackQueue.poll()
                if (chunk != null) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        onSpeakingStarted?.invoke()
                    }

                    val rms = calculateRms(chunk, chunk.size)
                    onAmplitudeChanged?.invoke(rms)

                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (isSpeaking) {
                        isSpeaking = false
                        onSpeakingStopped?.invoke()
                    }
                    try {
                        Thread.sleep(10)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackQueue.clear()
        if (isSpeaking) {
            isSpeaking = false
            onSpeakingStopped?.invoke()
        }
    }

    fun queueAudio(pcmData: ByteArray) {
        if (pcmData.isNotEmpty()) {
            playbackQueue.add(pcmData)
            if (!isPlaying) {
                startPlayback()
            }
        }
    }

    fun clearPlaybackQueue() {
        playbackQueue.clear()
        if (isSpeaking) {
            isSpeaking = false
            onSpeakingStopped?.invoke()
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
