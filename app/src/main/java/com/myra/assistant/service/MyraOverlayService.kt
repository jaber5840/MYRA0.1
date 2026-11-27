package com.myra.assistant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myra.assistant.R
import com.myra.assistant.ui.main.MainActivity
import com.myra.assistant.ui.main.OrbAnimationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class MyraOverlayService : Service() {

    companion object {
        private const val TAG = "MyraOverlayService"
        const val CHANNEL_ID = "myra_overlay_channel"
        private const val NOTIF_ID = 3001

        const val ACTION_SHOW_OVERLAY = "com.myra.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.myra.HIDE_OVERLAY"

        @Volatile var isRunning = false
            private set
    }

    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var orbView: OrbAnimationView? = null

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            super.attachBaseContext(newBase.createAttributionContext("default"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlays; stopping MyraOverlayService")
            stopSelf()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                startForeground(NOTIF_ID, createNotification(), types)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MyraOverlayService foreground: ${e.message}", e)
        }

        showOverlay()
        observeVoiceSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayView == null) {
            showOverlay()
        }
        return START_STICKY
    }

    private fun observeVoiceSession() {
        overlayScope.launch {
            launch {
                MyraVoiceSession.orbState.collect { state ->
                    orbView?.setState(state)
                }
            }
            launch {
                MyraVoiceSession.amplitude.collect { amp ->
                    orbView?.amplitude = amp
                }
            }
            launch {
                MyraVoiceSession.isRunning.collect { voiceActive ->
                    if (!voiceActive && overlayView != null) {
                        orbView?.setState(OrbAnimationView.State.IDLE)
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_orb, null)
        orbView = overlayView?.findViewById(R.id.overlayOrbView)

        orbView?.setState(MyraVoiceSession.orbState.value)
        orbView?.amplitude = MyraVoiceSession.amplitude.value

        val density = resources.displayMetrics.density
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Siri-like bottom-center floating position above navigation gestures
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (44 * density).toInt()
        }

        val closeBtn = overlayView?.findViewById<ImageButton>(R.id.overlayCloseBtn)
        closeBtn?.setOnClickListener {
            stopSelf()
        }

        // Drag + Siri-like tap actions + long-press to open app
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTimeMs = 0L
        var moved = false

        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTimeMs = System.currentTimeMillis()
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        moved = true
                    }
                    layoutParams.x = initialX + dx.toInt()
                    // Gravity is BOTTOM, so moving finger down decreases y offset
                    layoutParams.y = (initialY - dy.toInt()).coerceAtLeast(0)
                    try {
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val heldMs = System.currentTimeMillis() - downTimeMs
                    if (!moved) {
                        if (heldMs >= 500L) {
                            openMainApp()
                        } else {
                            handleOrbTap()
                        }
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view: ${e.message}", e)
        }
    }

    private fun handleOrbTap() {
        when {
            !MyraVoiceSession.isRunning.value -> {
                val startIntent = Intent(this, MyraVoiceService::class.java).apply {
                    action = MyraVoiceService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(this, startIntent)
                } catch (_: Exception) {
                    openMainApp()
                }
            }
            MyraVoiceSession.isSpeaking.value -> {
                val interruptIntent = Intent(this, MyraVoiceService::class.java).apply {
                    action = MyraVoiceService.ACTION_INTERRUPT
                }
                startService(interruptIntent)
            }
            MyraVoiceSession.isMuted.value -> {
                val muteIntent = Intent(this, MyraVoiceService::class.java).apply {
                    action = MyraVoiceService.ACTION_TOGGLE_MUTE
                }
                startService(muteIntent)
            }
            else -> {
                openMainApp()
            }
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayScope.cancel()
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view: ${e.message}", e)
            }
            overlayView = null
            orbView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Floating Siri Orb",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA Floating Orb")
            .setContentText("Siri-style voice orb active on screen")
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
