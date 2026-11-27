package com.myra.assistant.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.model.AppCommand
import com.myra.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"

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

    private val _commandResult = MutableLiveData<String?>()
    val commandResult: LiveData<String?> = _commandResult

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command: ${e.message}", e)
                _commandResult.postValue("Failed to execute command: ${e.message}")
            }
        }
    }

    private fun openApp(appName: String) {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val cleanName = appName.lowercase()

        val targetPackage = APP_PACKAGE_MAP[cleanName]
        var launchIntent: Intent? = null

        if (targetPackage != null) {
            launchIntent = pm.getLaunchIntentForPackage(targetPackage)
        }

        if (launchIntent == null) {
            // Scan installed packages
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label.contains(cleanName)) {
                    launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) break
                }
            }
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            _commandResult.postValue("Opening $appName")
        } else {
            _commandResult.postValue("Could not find $appName installed on device")
        }
    }

    private fun closeApp(appName: String) {
        val service = AccessibilityHelperService.instance
        if (service != null) {
            service.closeCurrentApp()
            _commandResult.postValue("Closing current app")
        } else {
            _commandResult.postValue("Accessibility Service is OFF. Enable it in Settings to close apps.")
        }
    }

    private fun makeCall(contactNameOrNumber: String) {
        val context = getApplication<Application>()
        val number = resolvePhoneNumber(contactNameOrNumber)
        if (number.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                _commandResult.postValue("Calling $contactNameOrNumber ($number)")
            } catch (e: Exception) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                _commandResult.postValue("Opening dialer for $contactNameOrNumber")
            }
        } else {
            _commandResult.postValue("Contact $contactNameOrNumber not found")
        }
    }

    private fun sendSms(contactName: String, message: String) {
        val context = getApplication<Application>()
        val number = resolvePhoneNumber(contactName)
        val target = if (number.isNotEmpty()) number else contactName
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$target")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _commandResult.postValue("Preparing SMS to $target")
    }

    private fun sendWhatsAppMsg(contactName: String, message: String) {
        val context = getApplication<Application>()
        val number = resolvePhoneNumber(contactName)
        val cleanNum = number.replace("+", "").replace(" ", "").replace("-", "")
        if (cleanNum.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum?text=${Uri.encode(message)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _commandResult.postValue("Sending WhatsApp message to $contactName")
        } else {
            _commandResult.postValue("Contact number for $contactName not found")
        }
    }

    private fun callPrimeContact(index: Int) {
        val primeContacts = getPrimeContacts()
        if (index in primeContacts.indices) {
            val contact = primeContacts[index]
            makeCall(contact.second)
        } else if (primeContacts.isNotEmpty()) {
            makeCall(primeContacts[0].second)
        } else {
            _commandResult.postValue("No Prime Contact set. Add one in Settings!")
        }
    }

    private fun msgPrimeContact(index: Int) {
        val primeContacts = getPrimeContacts()
        if (index in primeContacts.indices) {
            val contact = primeContacts[index]
            sendSms(contact.second, "Hey! MYRA sent this message.")
        } else if (primeContacts.isNotEmpty()) {
            sendSms(primeContacts[0].second, "Hey! MYRA sent this message.")
        } else {
            _commandResult.postValue("No Prime Contact set. Add one in Settings!")
        }
    }

    private fun getPrimeContacts(): List<Pair<String, String>> {
        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("prime_contacts_json", null)
        val list = mutableListOf<Pair<String, String>>()

        if (!jsonStr.isNull_or_empty()) {
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
            // Legacy check
            val legacyName = prefs.getString("prime_name", "") ?: ""
            val legacyNum = prefs.getString("prime_number", "") ?: ""
            if (legacyName.isNotEmpty() || legacyNum.isNotEmpty()) {
                list.add(Pair(legacyName, legacyNum))
            }
        }

        return list
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.isEmpty()

    private fun resolvePhoneNumber(nameOrNumber: String): String {
        if (nameOrNumber.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            return nameOrNumber
        }
        val context = getApplication<Application>()
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

    private fun adjustVolume(up: Boolean) {
        val context = getApplication<Application>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        _commandResult.postValue("Volume " + if (up) "increased" else "decreased")
    }

    private fun toggleFlashlight(on: Boolean) {
        val context = getApplication<Application>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
                _commandResult.postValue("Flashlight " + if (on) "turned ON" else "turned OFF")
            }
        } catch (e: Exception) {
            _commandResult.postValue("Could not toggle flashlight: ${e.message}")
        }
    }

    private fun toggleWifi() {
        val context = getApplication<Application>()
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _commandResult.postValue("Opened Wi-Fi Settings")
    }

    private fun toggleBluetooth() {
        val context = getApplication<Application>()
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _commandResult.postValue("Opened Bluetooth Settings")
    }

    fun acceptCall() {
        val context = getApplication<Application>()
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                telecomManager.acceptRingingCall()
                _commandResult.postValue("Incoming call accepted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting call: ${e.message}", e)
        }
    }

    fun rejectCall() {
        val context = getApplication<Application>()
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
                _commandResult.postValue("Incoming call rejected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting call: ${e.message}", e)
        }
    }
}
