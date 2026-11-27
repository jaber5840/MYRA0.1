package com.myra.assistant.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import com.myra.assistant.model.AppCommand
import com.myra.assistant.model.CommandHistoryItem
import com.myra.assistant.model.PendingConfirmation
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.service.MyraNotificationListenerService
import com.myra.assistant.service.MyraVoiceSession
import com.myra.assistant.service.ScreenCaptureService
import com.myra.assistant.ui.main.ScreenCapturePermissionActivity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun executeCommands(
        commands: List<AppCommand>,
        rawInput: String = "",
        skipConfirmation: Boolean = false
    ): String? {
        if (commands.isEmpty()) return null
        val outputs = mutableListOf<String>()
        for (cmd in commands) {
            val res = executeCommand(cmd, rawInput, skipConfirmation)
            if (!res.isNullOrBlank()) {
                outputs.add(res)
            }
        }
        return if (outputs.isEmpty()) null else outputs.joinToString(" ")
    }

    fun executeCommand(
        command: AppCommand,
        rawInput: String = "",
        skipConfirmation: Boolean = false
    ): String? {
        val result: String? = try {
            when (command.type) {
                AppCommand.TYPE_CONFIRM_ACTION -> confirmPendingAction()
                AppCommand.TYPE_CANCEL_ACTION -> cancelPendingAction()
                AppCommand.TYPE_OPEN_APP -> openApp(command.params["app_name"].orEmpty())
                AppCommand.TYPE_CLOSE_APP -> closeApp()
                AppCommand.TYPE_OPEN_DIALER -> openDialer()
                AppCommand.TYPE_OPEN_CONTACTS -> openContacts()
                AppCommand.TYPE_CALL -> prepareOrExecuteCall(command.params["name"].orEmpty(), skipConfirmation)
                AppCommand.TYPE_SMS -> prepareOrExecuteSms(command.params["name"].orEmpty(), command.params["message"].orEmpty(), skipConfirmation)
                AppCommand.TYPE_WHATSAPP_MSG -> prepareOrExecuteWhatsApp(command.params["name"].orEmpty(), command.params["message"].orEmpty(), skipConfirmation)
                AppCommand.TYPE_WHATSAPP_CHAT -> openWhatsAppChat(command.params["name"].orEmpty())
                AppCommand.TYPE_PRIME_CALL -> prepareOrExecutePrimeCall(command.params["index"]?.toIntOrNull() ?: 0, skipConfirmation)
                AppCommand.TYPE_PRIME_MSG -> prepareOrExecutePrimeMsg(command.params["index"]?.toIntOrNull() ?: 0, skipConfirmation)
                AppCommand.TYPE_VOLUME_UP -> adjustVolume(true)
                AppCommand.TYPE_VOLUME_DOWN -> adjustVolume(false)
                AppCommand.TYPE_BRIGHTNESS_UP -> SmartToolsHelper.adjustBrightness(context, 18)
                AppCommand.TYPE_BRIGHTNESS_DOWN -> SmartToolsHelper.adjustBrightness(context, -18)
                AppCommand.TYPE_FLASHLIGHT_ON -> toggleFlashlight(true)
                AppCommand.TYPE_FLASHLIGHT_OFF -> toggleFlashlight(false)
                AppCommand.TYPE_WIFI_ON, AppCommand.TYPE_WIFI_OFF -> toggleWifi()
                AppCommand.TYPE_BLUETOOTH_ON, AppCommand.TYPE_BLUETOOTH_OFF -> toggleBluetooth()
                AppCommand.TYPE_OPEN_SETTINGS -> openSystemSettings()
                AppCommand.TYPE_OPEN_CAMERA_BACK -> openCamera(front = false)
                AppCommand.TYPE_OPEN_CAMERA_FRONT -> openCamera(front = true)
                AppCommand.TYPE_TAKE_PHOTO -> requestPhotoCapture(command.params["front"] == "true")
                AppCommand.TYPE_TAKE_SCREENSHOT -> takeScreenshot()
                AppCommand.TYPE_SCREEN_SHARE_ON -> startScreenShare()
                AppCommand.TYPE_SCREEN_SHARE_OFF -> stopScreenShare()
                AppCommand.TYPE_WEATHER -> SmartToolsHelper.fetchWeather(context, command.params["city"])
                AppCommand.TYPE_TIME_DATE -> getTimeAndDate()
                AppCommand.TYPE_SET_ALARM -> setAlarm(
                    command.params["hour"]?.toIntOrNull() ?: 7,
                    command.params["minute"]?.toIntOrNull() ?: 0,
                    command.params["message"] ?: "MYRA Alarm"
                )
                AppCommand.TYPE_SET_TIMER -> setTimer(
                    command.params["seconds"]?.toIntOrNull() ?: 60,
                    command.params["message"] ?: "MYRA Timer"
                )
                AppCommand.TYPE_ADD_CALENDAR_EVENT -> addCalendarEvent(command.params["title"] ?: "MYRA Event")
                AppCommand.TYPE_CALCULATE -> SmartToolsHelper.evaluateMath(command.params["expression"].orEmpty())
                AppCommand.TYPE_WEB_SEARCH -> webSearch(command.params["query"].orEmpty())
                AppCommand.TYPE_MUSIC_PLAY_PAUSE -> SmartToolsHelper.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                AppCommand.TYPE_MUSIC_NEXT -> SmartToolsHelper.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                AppCommand.TYPE_MUSIC_PREV -> SmartToolsHelper.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                AppCommand.TYPE_BATTERY_INFO -> SmartToolsHelper.getBatteryInfo(context)
                AppCommand.TYPE_DEVICE_INFO -> SmartToolsHelper.getDeviceInfo(context)
                AppCommand.TYPE_LOCATION_INFO -> SmartToolsHelper.getLocationSummary(context)
                AppCommand.TYPE_READ_NOTIFICATIONS -> readRecentNotifications()
                AppCommand.TYPE_REMEMBER_PREF -> rememberPreference(command.params["note"].orEmpty())
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}", e)
            "Failed to execute command: ${e.message}"
        }

        if (!result.isNullOrBlank()) {
            MyraVoiceSession.recordCommandHistory(
                context,
                CommandHistoryItem(
                    commandType = command.type,
                    inputSummary = rawInput.ifBlank { command.type.replace("_", " ").lowercase() },
                    resultSummary = result,
                    succeeded = !result.startsWith("Failed", ignoreCase = true) &&
                        !result.startsWith("Could not", ignoreCase = true)
                )
            )
        }
        return result
    }

    fun confirmPendingAction(): String {
        val pending = MyraVoiceSession.pendingConfirmation.value
            ?: return "There is no pending action to confirm."
        MyraVoiceSession.setPendingConfirmation(null)
        return executeCommand(pending.command, pending.title, skipConfirmation = true)
            ?: "Action completed."
    }

    fun cancelPendingAction(): String {
        val pending = MyraVoiceSession.pendingConfirmation.value
            ?: return "There is no pending action to cancel."
        MyraVoiceSession.setPendingConfirmation(null)
        return "Cancelled: ${pending.title}."
    }

    private fun openApp(appName: String): String {
        val pm = context.packageManager
        val cleanName = appName.lowercase(Locale.ROOT).trim()
        var targetPackage = APP_PACKAGE_MAP[cleanName]
        if (targetPackage == null) {
            for ((k, v) in APP_PACKAGE_MAP) {
                if (k.contains(cleanName) || cleanName.contains(k)) {
                    targetPackage = v
                    break
                }
            }
        }
        var launchIntent: Intent? = if (targetPackage != null) pm.getLaunchIntentForPackage(targetPackage) else null

        if (launchIntent == null) {
            try {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                for (info in resolveInfos) {
                    val pkg = info.activityInfo.packageName
                    val label = info.loadLabel(pm).toString().lowercase(Locale.ROOT)
                    if (label.contains(cleanName) || pkg.lowercase(Locale.ROOT).contains(cleanName)) {
                        launchIntent = pm.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying launcher intents: ${e.message}", e)
            }
        }

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "Opening $appName."
        } else {
            "Could not find $appName installed on this device."
        }
    }

    private fun closeApp(): String {
        val service = AccessibilityHelperService.instance
        return if (service != null) {
            service.closeCurrentApp()
            "Returning to home screen."
        } else {
            "Accessibility Service is OFF. Enable it in Settings > Permissions to close apps."
        }
    }

    private fun openDialer(): String {
        val intent = Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return "Opened Phone Dialer."
    }

    private fun openContacts(): String {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opened Contacts."
    }

    private fun prepareOrExecuteCall(contactNameOrNumber: String, skipConfirmation: Boolean): String {
        val number = resolvePhoneNumber(contactNameOrNumber)
        if (number.isEmpty()) {
            return "Contact $contactNameOrNumber was not found."
        }
        if (!skipConfirmation) {
            MyraVoiceSession.setPendingConfirmation(
                PendingConfirmation(
                    title = "Confirm Phone Call",
                    description = "Call $contactNameOrNumber ($number)?",
                    confirmButtonLabel = "CALL NOW",
                    command = AppCommand(AppCommand.TYPE_CALL, mapOf("name" to contactNameOrNumber))
                )
            )
            return "Ready to call $contactNameOrNumber at $number. Say Yes or tap Call Now to confirm."
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Calling $contactNameOrNumber ($number)."
        } catch (_: Exception) {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            "Opened dialer for $contactNameOrNumber."
        }
    }

    private fun prepareOrExecuteSms(contactName: String, message: String, skipConfirmation: Boolean): String {
        val number = resolvePhoneNumber(contactName)
        val target = number.ifEmpty { contactName }
        val body = message.ifEmpty { "Hello" }
        if (!skipConfirmation) {
            MyraVoiceSession.setPendingConfirmation(
                PendingConfirmation(
                    title = "Confirm SMS",
                    description = "Prepare SMS to $contactName ($target):\n\"$body\"",
                    confirmButtonLabel = "PREPARE SMS",
                    command = AppCommand(AppCommand.TYPE_SMS, mapOf("name" to contactName, "message" to body))
                )
            )
            return "Prepared SMS to $contactName saying '$body'. Confirm to open Messages."
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$target")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opened Messages with SMS to $target."
    }

    private fun prepareOrExecuteWhatsApp(contactName: String, message: String, skipConfirmation: Boolean): String {
        val number = resolvePhoneNumber(contactName)
        val cleanNum = number.replace("+", "").replace(" ", "").replace("-", "")
        if (cleanNum.isEmpty()) {
            return "Could not find a phone number for $contactName in Contacts."
        }
        val body = message.ifEmpty { "Hello" }
        if (!skipConfirmation) {
            MyraVoiceSession.setPendingConfirmation(
                PendingConfirmation(
                    title = "Confirm WhatsApp Message",
                    description = "To: $contactName ($number)\nMessage: \"$body\"",
                    confirmButtonLabel = "OPEN WHATSAPP",
                    command = AppCommand(AppCommand.TYPE_WHATSAPP_MSG, mapOf("name" to contactName, "message" to body))
                )
            )
            return "Prepared WhatsApp message to $contactName saying '$body'. Say Yes or tap Open WhatsApp to confirm."
        }
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$cleanNum?text=${Uri.encode(body)}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return "Opened WhatsApp conversation with $contactName."
    }

    private fun openWhatsAppChat(contactName: String): String {
        val number = resolvePhoneNumber(contactName)
        val cleanNum = number.replace("+", "").replace(" ", "").replace("-", "")
        if (cleanNum.isEmpty()) {
            return "Could not find a phone number for $contactName in Contacts."
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opened WhatsApp chat with $contactName."
    }

    private fun prepareOrExecutePrimeCall(index: Int, skipConfirmation: Boolean): String {
        val primeContacts = getPrimeContacts()
        val chosen = primeContacts.getOrNull(index) ?: primeContacts.firstOrNull()
            ?: return "No Prime Contact set. Add one in Settings!"
        return prepareOrExecuteCall(chosen.second, skipConfirmation)
    }

    private fun prepareOrExecutePrimeMsg(index: Int, skipConfirmation: Boolean): String {
        val primeContacts = getPrimeContacts()
        val chosen = primeContacts.getOrNull(index) ?: primeContacts.firstOrNull()
            ?: return "No Prime Contact set. Add one in Settings!"
        return prepareOrExecuteSms(chosen.second, "Hey! MYRA sent this message.", skipConfirmation)
    }

    private fun openCamera(front: Boolean): String {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (front) {
                putExtra("android.intent.extras.CAMERA_FACING", 1)
                putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            }
        }
        return try {
            context.startActivity(intent)
            if (front) "Opened front camera." else "Opened camera."
        } catch (e: Exception) {
            "Unable to open camera: ${e.message}"
        }
    }

    private fun requestPhotoCapture(front: Boolean): String {
        MyraVoiceSession.requestCameraPhotoCapture(front)
        return "Opening camera to take a photo."
    }

    private fun takeScreenshot(): String {
        val service = AccessibilityHelperService.instance
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ok = service.takeScreenshotAction()
            if (ok) return "Screenshot captured using Android Accessibility API."
        }
        return "Enable MYRA Accessibility Helper in Settings > Permissions to trigger system screenshots."
    }

    private fun openSystemSettings(): String {
        val intent = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return "Opened Android Settings."
    }

    private fun getTimeAndDate(): String {
        val fmt = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault())
        return "It is ${fmt.format(Date())}."
    }

    private fun setAlarm(hour: Int, minute: Int, message: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Setting alarm for %02d:%02d.".format(Locale.US, hour, minute)
        } catch (_: Exception) {
            "No Clock app available to set alarms."
        }
    }

    private fun setTimer(seconds: Int, message: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Started timer for $seconds seconds."
        } catch (_: Exception) {
            "No Clock app available to set timers."
        }
    }

    private fun addCalendarEvent(title: String): String {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Opening Calendar to create event \"$title\"."
        } catch (_: Exception) {
            "No Calendar app found on device."
        }
    }

    private fun webSearch(query: String): String {
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Searching web for \"$query\"."
    }

    private fun readRecentNotifications(): String {
        if (!MyraNotificationListenerService.isPermissionGranted(context)) {
            return "Notification access is not enabled. Allow MYRA Notification Reader in Settings > Permissions to read notifications."
        }
        val list = MyraVoiceSession.recentNotifications.value
        if (list.isEmpty()) {
            return "You have no recent notifications right now."
        }
        val top = list.take(3).joinToString(". ") { n ->
            val msg = listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(": ")
            "From ${n.appName}, $msg"
        }
        return "Recent notifications: $top."
    }

    private fun rememberPreference(note: String): String {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("memory_consent_enabled", false)) {
            return "Memory consent is currently OFF. Enable 'Remember Preferences' in Settings > Language & Personality first."
        }
        val arr = try {
            JSONArray(prefs.getString("remembered_notes_json", "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        arr.put(note)
        while (arr.length() > 15) {
            arr.remove(0)
        }
        prefs.edit().putString("remembered_notes_json", arr.toString()).apply()
        return "Got it, I will remember that: $note."
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
        return "Volume " + if (up) "increased." else "decreased."
    }

    private fun toggleFlashlight(on: Boolean): String? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
                MyraVoiceSession.setFlashlightOn(on)
                "Flashlight " + if (on) "turned ON." else "turned OFF."
            } else {
                "No camera flash available on this device."
            }
        } catch (e: Exception) {
            "Could not toggle flashlight: ${e.message}"
        }
    }

    private fun toggleWifi(): String {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return "Opened Wi-Fi Settings."
    }

    private fun toggleBluetooth(): String {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return "Opened Bluetooth Settings."
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
                "Incoming call accepted."
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
                "Incoming call rejected."
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting call: ${e.message}", e)
            null
        }
    }
}
