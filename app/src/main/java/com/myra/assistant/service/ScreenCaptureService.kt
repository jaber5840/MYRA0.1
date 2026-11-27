package com.myra.assistant.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.myra.assistant.R
import com.myra.assistant.ui.main.MainActivity
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "myra_screen_share_channel"
        private const val NOTIF_ID = 3009

        const val ACTION_START_CAPTURE = "com.myra.assistant.action.START_SCREEN_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.myra.assistant.action.STOP_SCREEN_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val FRAME_INTERVAL_MS = 1200L
        private const val MAX_CAPTURE_DIMENSION = 960
        private const val JPEG_QUALITY = 65
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    @Volatile
    private var lastFrameSentAtMs: Long = 0L

    private var captureWidth: Int = 540
    private var captureHeight: Int = 960
    private var captureDensityDpi: Int = DisplayMetrics.DENSITY_HIGH

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped by system or user")
            mainHandler.post { stopCaptureAndSelf() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startCapture(resultCode, resultData)
                } else {
                    Log.w(TAG, "Invalid MediaProjection result code/data; stopping service")
                    stopCaptureAndSelf()
                }
            }
            ACTION_STOP_CAPTURE -> {
                stopCaptureAndSelf()
            }
            else -> {
                if (!MyraVoiceSession.isScreenSharing.value) {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        promoteToForeground()
        releaseProjectionResources()

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val projection = try {
            projectionManager?.getMediaProjection(resultCode, resultData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain MediaProjection: ${e.message}", e)
            null
        }

        if (projection == null) {
            MyraVoiceSession.emitError("Unable to start screen capture.")
            stopCaptureAndSelf()
            return
        }

        computeScaledDisplayMetrics()

        val thread = HandlerThread("MyraScreenCapture").apply { start() }
        captureThread = thread
        val bgHandler = Handler(thread.looper)
        captureHandler = bgHandler

        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)

        val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        lastFrameSentAtMs = 0L

        reader.setOnImageAvailableListener({ imgReader ->
            val image = try {
                imgReader.acquireLatestImage()
            } catch (_: Exception) {
                null
            } ?: return@setOnImageAvailableListener

            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameSentAtMs < FRAME_INTERVAL_MS) {
                    return@setOnImageAvailableListener
                }
                if (!MyraVoiceSession.isScreenSharing.value) {
                    return@setOnImageAvailableListener
                }

                val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                if (pixelStride <= 0) return@setOnImageAvailableListener

                val rowPadding = rowStride - pixelStride * captureWidth
                val bitmapWidth = captureWidth + max(0, rowPadding) / pixelStride

                val rawBitmap = Bitmap.createBitmap(bitmapWidth, captureHeight, Bitmap.Config.ARGB_8888)
                var croppedBitmap: Bitmap? = null
                try {
                    rawBitmap.copyPixelsFromBuffer(buffer)
                    croppedBitmap = if (bitmapWidth != captureWidth) {
                        Bitmap.createBitmap(rawBitmap, 0, 0, captureWidth, captureHeight)
                    } else {
                        rawBitmap
                    }

                    val outStream = ByteArrayOutputStream()
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outStream)
                    val base64 = Base64.encodeToString(outStream.toByteArray(), Base64.NO_WRAP)

                    lastFrameSentAtMs = now
                    MyraVoiceSession.emitScreenFrame(base64)
                } finally {
                    if (croppedBitmap != null && croppedBitmap !== rawBitmap) {
                        croppedBitmap.recycle()
                    }
                    rawBitmap.recycle()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Frame capture skipped: ${e.message}")
            } finally {
                try {
                    image.close()
                } catch (_: Exception) {}
            }
        }, bgHandler)

        try {
            virtualDisplay = projection.createVirtualDisplay(
                "MYRAScreenCapture",
                captureWidth,
                captureHeight,
                captureDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                bgHandler
            )
            MyraVoiceSession.setScreenSharing(true)
            Log.i(TAG, "Screen capture started (${captureWidth}x${captureHeight})")
        } catch (e: Exception) {
            Log.e(TAG, "createVirtualDisplay failed: ${e.message}", e)
            MyraVoiceSession.emitError("Screen capture error: ${e.message}")
            stopCaptureAndSelf()
        }
    }

    private fun computeScaledDisplayMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rawWidth: Int
        val rawHeight: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            rawWidth = bounds.width().coerceAtLeast(360)
            rawHeight = bounds.height().coerceAtLeast(640)
            captureDensityDpi = resources.displayMetrics.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            rawWidth = metrics.widthPixels.coerceAtLeast(360)
            rawHeight = metrics.heightPixels.coerceAtLeast(640)
            captureDensityDpi = metrics.densityDpi
        }

        val maxSide = max(rawWidth, rawHeight).toFloat()
        val scale = if (maxSide > MAX_CAPTURE_DIMENSION) MAX_CAPTURE_DIMENSION / maxSide else 1f
        captureWidth = ((rawWidth * scale).roundToInt() and 0x7FFFFFFE).coerceAtLeast(2)
        captureHeight = ((rawHeight * scale).roundToInt() and 0x7FFFFFFE).coerceAtLeast(2)
    }

    private fun stopCaptureAndSelf() {
        MyraVoiceSession.setScreenSharing(false)
        releaseProjectionResources()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releaseProjectionResources() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        try {
            captureThread?.quitSafely()
        } catch (_: Exception) {}
        captureThread = null
        captureHandler = null
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ScreenCaptureService foreground: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Screen Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Allows MYRA to see your mobile screen in real time"
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP_CAPTURE
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA Screen Share Active")
            .setContentText("MYRA can see your phone screen live")
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop Sharing", stopPending)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        MyraVoiceSession.setScreenSharing(false)
        releaseProjectionResources()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
