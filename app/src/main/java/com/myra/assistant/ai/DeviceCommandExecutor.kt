package com.myra.assistant.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import com.myra.assistant.model.AppCommand
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.service.MyraVoiceSession
import com.myra.assistant.service.ScreenCaptureService
import com.myra.assistant.ui.main.ScreenCapturePermissionActivity
import org.json.JSONArray

class DeviceCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DeviceCommandExecutor"

        private val APP_PACKAGE_MAP = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "contacts" to "com.google.android.contacts",
            "play store" to "com.android.vending",
            "zoom" to "us.zoom.videomeetings"
        )
    }

    fun executeCommand(command: AppCommand): String? {
        return try {
            when (command.type) {
                AppCommand.TYPE_OPEN_APP -> openApp(command.params["app_name"] ?: "")
                AppCommand.TYPE_CLOSE_APP -> closeApp(command.params["app_name"] ?: "")
                AppCommand.TYPE_CALL -> makeCall(command.params["name"] ?: "")
                AppCommand.TYPE_SMS -> sendSms(command.params["name"] ?: "", command.params["message"] ?: "Hello")
                AppCommand.TYPE_WHATSAPP_MSG -> sendWhatsAppMsg(command.params["name"] ?: "", command.params["message"] ?: "Hello")
                AppCommand.TYPE_PRIME_CALL -> callPrimeContact(command.params["index"]?.toIntOrNull() ?: 0)
                AppCommand.TYPE_PRIME_MSG -> msgPrimeContact(command.params["index"]?.toIntOrNull() ?: 0)
                AppCommand.TYPE_VOLUME_UP -> adjustVolume(true)
                AppCommand.TYPE_VOLUME_DOWN -> adjustVolume(false)
                AppCommand.TYPE_FLASHLIGHT_ON -> toggleFlashlight(true)
                AppCommand.TYPE_FLASHLIGHT_OFF -> toggleFlashlight(false)
                AppCommand.TYPE_WIFI_ON, AppCommand.TYPE_WIFI_OFF -> toggleWifi()
                AppCommand.TYPE_BLUETOOTH_ON, AppCommand.TYPE_BLUETOOTH_OFF -> toggleBluetooth()
                AppCommand.TYPE_SCREEN_SHARE_ON -> startScreenShare()
                AppCommand.TYPE_SCREEN_SHARE_OFF -> stopScreenShare()
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}", e)
            "Failed to execute command: ${e.message}"
        }
    }

    private fun openApp(appName: String): String {
        val pm = context.packageManager
        val cleanName = appName.lowercase()

        val targetPackage = APP_PACKAGE_MAP[cleanName]
        var launchIntent: Intent? = null

        if (targetPackage != null) {
            launchIntent = pm.getLaunchIntentForPackage(targetPackage)
        }

        if (launchIntent == null) {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label.contains(cleanName)) {
                    launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) break
                }
            }
        }

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "Opening $appName"
        } else {
            "Could not find $appName installed on device"
        }
    }

    private fun closeApp(@Suppress("UNUSED_PARAMETER") appName: String): String {
        val service = AccessibilityHelperService.instance
        return if (service != null) {
            service.closeCurrentApp()
            "Closing current app"
        } else {
            "Accessibility Service is OFF. Enable it in Settings to close apps."
        }
    }

    private fun makeCall(contactNameOrNumber: String): String {
        val number = resolvePhoneNumber(contactNameOrNumber)
        if (number.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                "Calling $contactNameOrNumber ($number)"
            } catch (e: Exception) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                "Opening dialer for $contactNameOrNumber"
            }
        }
        return "Contact $contactNameOrNumber not found"
    }

    private fun sendSms(contactName: String, message: String): String {
        val number = resolvePhoneNumber(contactName)
        val target = if (number.isNotEmpty()) number else contactName
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$target")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Preparing SMS to $target"
    }

    private fun sendWhatsAppMsg(contactName: String, message: String): String {
        val number = resolvePhoneNumber(contactName)
        val cleanNum = number.replace("+", "").replace(" ", "").replace("-", "")
        return if (cleanNum.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum?text=${Uri.encode(message)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Sending WhatsApp message to $contactName"
        } else {
            "Contact number for $contactName not found"
        }
    }

    private fun callPrimeContact(index: Int): String {
        val primeContacts = getPrimeContacts()
        return when {
            index in primeContacts.indices -> makeCall(primeContacts[index].second)
            primeContacts.isNotEmpty() -> makeCall(primeContacts[0].second)
            else -> "No Prime Contact set. Add one in Settings!"
        }
    }

    private fun msgPrimeContact(index: Int): String {
        val primeContacts = getPrimeContacts()
        return when {
            index in primeContacts.indices -> sendSms(primeContacts[index].second, "Hey! MYRA sent this message.")
            primeContacts.isNotEmpty() -> sendSms(primeContacts[0].second, "Hey! MYRA sent this message.")
            else -> "No Prime Contact set. Add one in Settings!"
        }
    }

    private fun getPrimeContacts(): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("prime_contacts_json", null)
        val list = mutableListOf<Pair<String, String>>()

        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val name = obj.optString("name", "")
                    val num = obj.optString("number", "")
                    if (name.isNotEmpty() || num.isNotEmpty()) {
                        list.add(Pair(name, num))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing prime contacts JSON: ${e.message}")
            }
        }

        if (list.isEmpty()) {
            val legacyName = prefs.getString("prime_name", "") ?: ""
            val legacyNum = prefs.getString("prime_number", "") ?: ""
            if (legacyName.isNotEmpty() || legacyNum.isNotEmpty()) {
                list.add(Pair(legacyName, legacyNum))
            }
        }

        return list
    }

    private fun resolvePhoneNumber(nameOrNumber: String): String {
        if (nameOrNumber.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            return nameOrNumber
        }
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$nameOrNumber%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx >= 0) {
                        return cursor.getString(numIdx) ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact: ${e.message}", e)
        }
        return ""
    }

    private fun adjustVolume(up: Boolean): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return "Volume " + if (up) "increased" else "decreased"
    }

    private fun toggleFlashlight(on: Boolean): String? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
                "Flashlight " + if (on) "turned ON" else "turned OFF"
            } else {
                null
            }
        } catch (e: Exception) {
            "Could not toggle flashlight: ${e.message}"
        }
    }

    private fun toggleWifi(): String {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opened Wi-Fi Settings"
    }

    private fun toggleBluetooth(): String {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opened Bluetooth Settings"
    }

    private fun startScreenShare(): String {
        if (MyraVoiceSession.isScreenSharing.value) {
            return "Screen share is already active. I can see your screen now."
        }
        if (MyraVoiceSession.isMainUiActive) {
            MyraVoiceSession.requestScreenShareToggle(true)
        } else {
            val intent = Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
        return "Opening screen share permission. Please tap Allow so I can see your screen."
    }

    private fun stopScreenShare(): String {
        if (!MyraVoiceSession.isScreenSharing.value) {
            return "Screen share is already turned off."
        }
        val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP_CAPTURE
        }
        try {
            context.startService(stopIntent)
        } catch (_: Exception) {
            MyraVoiceSession.setScreenSharing(false)
        }
        return "Screen share stopped."
    }

    fun acceptCall(): String? {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                telecomManager.acceptRingingCall()
                "Incoming call accepted"
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting call: ${e.message}", e)
            null
        }
    }

    fun rejectCall(): String? {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
                "Incoming call rejected"
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting call: ${e.message}", e)
            null
        }
    }
}
