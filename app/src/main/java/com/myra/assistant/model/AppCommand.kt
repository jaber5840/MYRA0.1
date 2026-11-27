package com.myra.assistant.model

data class AppCommand(
    val type: String,
    val params: Map<String, String> = emptyMap()
) {
    companion object {
        const val TYPE_OPEN_APP = "OPEN_APP"
        const val TYPE_CLOSE_APP = "CLOSE_APP"
        const val TYPE_CALL = "CALL"
        const val TYPE_OPEN_DIALER = "OPEN_DIALER"
        const val TYPE_OPEN_CONTACTS = "OPEN_CONTACTS"
        const val TYPE_SMS = "SMS"
        const val TYPE_WHATSAPP_MSG = "WHATSAPP_MSG"
        const val TYPE_WHATSAPP_CHAT = "WHATSAPP_CHAT"
        const val TYPE_WHATSAPP_CALL = "WHATSAPP_CALL"
        const val TYPE_PRIME_CALL = "PRIME_CALL"
        const val TYPE_PRIME_MSG = "PRIME_MSG"
        const val TYPE_VOLUME_UP = "VOLUME_UP"
        const val TYPE_VOLUME_DOWN = "VOLUME_DOWN"
        const val TYPE_BRIGHTNESS_UP = "BRIGHTNESS_UP"
        const val TYPE_BRIGHTNESS_DOWN = "BRIGHTNESS_DOWN"
        const val TYPE_FLASHLIGHT_ON = "FLASHLIGHT_ON"
        const val TYPE_FLASHLIGHT_OFF = "FLASHLIGHT_OFF"
        const val TYPE_WIFI_ON = "WIFI_ON"
        const val TYPE_WIFI_OFF = "WIFI_OFF"
        const val TYPE_BLUETOOTH_ON = "BLUETOOTH_ON"
        const val TYPE_BLUETOOTH_OFF = "BLUETOOTH_OFF"
        const val TYPE_OPEN_SETTINGS = "OPEN_SETTINGS"
        const val TYPE_OPEN_CAMERA_BACK = "OPEN_CAMERA_BACK"
        const val TYPE_OPEN_CAMERA_FRONT = "OPEN_CAMERA_FRONT"
        const val TYPE_TAKE_PHOTO = "TAKE_PHOTO"
        const val TYPE_TAKE_SCREENSHOT = "TAKE_SCREENSHOT"
        const val TYPE_SCREEN_SHARE_ON = "SCREEN_SHARE_ON"
        const val TYPE_SCREEN_SHARE_OFF = "SCREEN_SHARE_OFF"
        const val TYPE_WEATHER = "WEATHER"
        const val TYPE_TIME_DATE = "TIME_DATE"
        const val TYPE_SET_ALARM = "SET_ALARM"
        const val TYPE_SET_TIMER = "SET_TIMER"
        const val TYPE_ADD_CALENDAR_EVENT = "ADD_CALENDAR_EVENT"
        const val TYPE_CALCULATE = "CALCULATE"
        const val TYPE_WEB_SEARCH = "WEB_SEARCH"
        const val TYPE_MUSIC_PLAY_PAUSE = "MUSIC_PLAY_PAUSE"
        const val TYPE_MUSIC_NEXT = "MUSIC_NEXT"
        const val TYPE_MUSIC_PREV = "MUSIC_PREV"
        const val TYPE_BATTERY_INFO = "BATTERY_INFO"
        const val TYPE_DEVICE_INFO = "DEVICE_INFO"
        const val TYPE_LOCATION_INFO = "LOCATION_INFO"
        const val TYPE_READ_NOTIFICATIONS = "READ_NOTIFICATIONS"
        const val TYPE_REMEMBER_PREF = "REMEMBER_PREF"
        const val TYPE_CONFIRM_ACTION = "CONFIRM_ACTION"
        const val TYPE_CANCEL_ACTION = "CANCEL_ACTION"
    }
}

data class CommandHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val commandType: String,
    val inputSummary: String,
    val resultSummary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val succeeded: Boolean = true
)

data class PendingConfirmation(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val description: String,
    val confirmButtonLabel: String,
    val command: AppCommand
)

data class CapturedNotification(
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)
