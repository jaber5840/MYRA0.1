package com.myra.assistant.ui.settings

enum class SettingsCategory(val title: String) {
    API_CONNECTION("API & Connection"),
    VOICE_MODEL("Voice & Wake Word"),
    LANGUAGE_PERSONALITY("Language, Theme & Memory"),
    PROFILE_CONTACTS("Profile & Contacts"),
    SYSTEM_PERMISSIONS("Permission Manager"),
    APP_UPDATES("App Updates")
}

data class PrimeContactItem(
    val name: String,
    val number: String
)

data class SettingsUiState(
    val apiKey: String = "",
    val userName: String = "Alex",
    val selectedModel: String = "models/gemini-2.5-flash-native-audio-preview-12-2025",
    val modelOptions: List<Pair<String, String>> = listOf(
        "Gemini 2.5 Flash Native Audio (Default)" to "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "Gemini 2.5 Flash Native Audio (09-2025)" to "models/gemini-2.5-flash-native-audio-preview-09-2025",
        "Gemini 2.5 Flash Live" to "models/gemini-2.5-flash-live-preview"
    ),
    val selectedVoice: String = "Aoede",
    val voiceOptions: List<Pair<String, String>> = listOf(
        "Aoede (Female)" to "Aoede",
        "Charon (Male)" to "Charon",
        "Kore (Female)" to "Kore",
        "Fenrir (Male)" to "Fenrir",
        "Puck (Male)" to "Puck",
        "Leda (Female)" to "Leda",
        "Orus (Male)" to "Orus",
        "Zephyr (Female)" to "Zephyr"
    ),
    val voiceResponseStyle: String = "Concise",
    val wakeWordEnabled: Boolean = false,
    val personalityMode: String = "GF",
    val languageMode: String = "Banglish",
    val themeMode: String = "DARK",
    val memoryConsentEnabled: Boolean = false,
    val primeContacts: List<PrimeContactItem> = emptyList(),
    val isMicGranted: Boolean = false,
    val isCameraGranted: Boolean = false,
    val isContactsGranted: Boolean = false,
    val isCallPhoneGranted: Boolean = false,
    val isLocationGranted: Boolean = false,
    val isWriteSettingsGranted: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isNotificationAccessEnabled: Boolean = false,
    val appVersion: String = "2.3",
    val updateServiceStatus: String = "Update Service: Ready",
    val isTestingApi: Boolean = false,
    val apiTestButtonLabel: String = "TEST GEMINI API",
    val isCheckingUpdates: Boolean = false,
    val updateButtonLabel: String = "CHECK FOR UPDATES"
)
