package com.myra.assistant.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.myra.assistant.service.MyraVoiceService
import com.myra.assistant.service.MyraVoiceSession
import com.myra.assistant.service.ScreenCaptureService

/**
 * Lightweight translucent trampoline Activity used when voice commands
 * ("screen share on koro") trigger MediaProjection consent while the user
 * is outside MainActivity.
 */
class ScreenCapturePermissionActivity : ComponentActivity() {

    private var launchedConsent = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_CAPTURE
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, captureIntent)

            if (MyraVoiceSession.isRunning.value) {
                val notifyVoiceIntent = Intent(this, MyraVoiceService::class.java).apply {
                    action = MyraVoiceService.ACTION_SEND_TEXT
                    putExtra(
                        MyraVoiceService.EXTRA_TEXT,
                        "Screen share is now ON. Confirm in 1 short sentence that you can see the user's screen now."
                    )
                    putExtra(MyraVoiceService.EXTRA_ADD_TO_CHAT, false)
                }
                try {
                    startService(notifyVoiceIntent)
                } catch (_: Exception) {}
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val startVoiceIntent = Intent(this, MyraVoiceService::class.java).apply {
                    action = MyraVoiceService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(this, startVoiceIntent)
                } catch (_: Exception) {}
            }
        } else {
            MyraVoiceSession.setScreenSharing(false)
            Toast.makeText(this, "Screen share cancelled", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestConsentIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!launchedConsent) {
            requestConsentIfNeeded()
        }
    }

    private fun requestConsentIfNeeded() {
        if (launchedConsent) return
        if (MyraVoiceSession.isScreenSharing.value) {
            finish()
            return
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            Toast.makeText(this, "Screen capture is unavailable on this device", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        launchedConsent = true
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
