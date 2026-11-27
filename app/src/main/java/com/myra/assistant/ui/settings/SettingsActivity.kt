package com.myra.assistant.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myra.assistant.BuildConfig
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.update.GitHubUpdateChecker
import com.myra.assistant.update.RemoteConfigUpdateManager
import com.myra.assistant.update.UpdateDialogHelper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    private val defaultLiveModels = listOf(
        "Gemini 2.5 Flash Native Audio (Default)" to "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "Gemini 2.5 Flash Native Audio (09-2025)" to "models/gemini-2.5-flash-native-audio-preview-09-2025",
        "Gemini 2.5 Flash Live" to "models/gemini-2.5-flash-live-preview"
    )

    private var uiState by mutableStateOf(SettingsUiState(modelOptions = defaultLiveModels))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        loadSavedSettings()
        refreshActiveLiveModelsSilently()

        setContent {
            MyraSettingsScreen(
                state = uiState,
                onApiKeyChange = { newKey ->
                    uiState = uiState.copy(apiKey = newKey)
                },
                onUserNameChange = { newName ->
                    uiState = uiState.copy(userName = newName)
                },
                onModelSelected = { modelId ->
                    uiState = uiState.copy(selectedModel = modelId)
                    persistCurrentSettings(showToast = false)
                },
                onVoiceSelected = { voiceId ->
                    uiState = uiState.copy(selectedVoice = voiceId)
                    persistCurrentSettings(showToast = false)
                },
                onPersonalitySelected = { mode ->
                    uiState = uiState.copy(personalityMode = mode)
                    persistCurrentSettings(showToast = false)
                },
                onLanguageSelected = { lang ->
                    uiState = uiState.copy(languageMode = lang)
                    persistCurrentSettings(showToast = false)
                },
                onAddPrimeContact = { name, number ->
                    uiState = uiState.copy(
                        primeContacts = uiState.primeContacts + PrimeContactItem(name, number)
                    )
                    persistCurrentSettings(showToast = false)
                },
                onDeletePrimeContact = { index ->
                    if (index in uiState.primeContacts.indices) {
                        val updated = uiState.primeContacts.toMutableList().apply { removeAt(index) }
                        uiState = uiState.copy(primeContacts = updated)
                        persistCurrentSettings(showToast = false)
                    }
                },
                onTestApiClick = {
                    persistCurrentSettings(showToast = false)
                    testGeminiApiKey()
                },
                onOpenAccessibilityClick = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onCheckUpdatesClick = {
                    checkForUpdatesManually()
                },
                onSaveClick = {
                    persistCurrentSettings(showToast = true)
                    finish()
                },
                onCloseClick = {
                    persistCurrentSettings(showToast = false)
                    finish()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        uiState = uiState.copy(
            isAccessibilityEnabled = AccessibilityHelperService.isEnabled(this)
        )
    }

    private fun getEffectiveApiKey(): String {
        var apiKey = uiState.apiKey.trim()
        if (apiKey.isEmpty()) {
            apiKey = BuildConfig.GEMINI_API_KEY
        }
        return if (apiKey == "MY_GEMINI_API_KEY") "" else apiKey
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        val savedKey = prefs.getString("api_key", "")?.trim() ?: ""
        val effectiveKey = if (savedKey.isEmpty() && BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            BuildConfig.GEMINI_API_KEY
        } else {
            savedKey
        }

        val savedContacts = mutableListOf<PrimeContactItem>()
        val jsonStr = prefs.getString("prime_contacts_json", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    savedContacts.add(
                        PrimeContactItem(
                            name = obj.optString("name", ""),
                            number = obj.optString("number", "")
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        uiState = uiState.copy(
            apiKey = effectiveKey,
            userName = prefs.getString("user_name", "Alex") ?: "Alex",
            selectedModel = prefs.getString("gemini_model", defaultLiveModels.first().second)
                ?: defaultLiveModels.first().second,
            selectedVoice = prefs.getString("gemini_voice", "Aoede") ?: "Aoede",
            personalityMode = prefs.getString("personality_mode", "GF") ?: "GF",
            languageMode = prefs.getString("language_mode", "Banglish") ?: "Banglish",
            primeContacts = savedContacts,
            isAccessibilityEnabled = AccessibilityHelperService.isEnabled(this),
            appVersion = BuildConfig.VERSION_NAME,
            updateServiceStatus = RemoteConfigUpdateManager.getCurrentRemoteSummary()
        )
    }

    private fun persistCurrentSettings(showToast: Boolean) {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val array = JSONArray()
        for (c in uiState.primeContacts) {
            array.put(JSONObject().apply {
                put("name", c.name)
                put("number", c.number)
            })
        }

        prefs.edit()
            .putString("api_key", uiState.apiKey.trim())
            .putString("user_name", uiState.userName.trim().ifEmpty { "Alex" })
            .putString("gemini_model", uiState.selectedModel)
            .putString("gemini_voice", uiState.selectedVoice)
            .putString("personality_mode", uiState.personalityMode)
            .putString("language_mode", uiState.languageMode)
            .putString("prime_contacts_json", array.toString())
            .apply()

        if (showToast) {
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshActiveLiveModelsSilently() {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) return

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val bodyStr = response.body?.string() ?: return
                if (!response.isSuccessful) return
                val discoveredLive = parseModelsFromResponse(bodyStr).second
                if (discoveredLive.isNotEmpty()) {
                    runOnUiThread {
                        mergeLiveModels(discoveredLive)
                    }
                }
            }
        })
    }

    private fun mergeLiveModels(discoveredLive: List<Pair<String, String>>) {
        val merged = mutableListOf<Pair<String, String>>()
        val seenIds = mutableSetOf<String>()

        for (item in discoveredLive) {
            if (seenIds.add(item.second)) merged.add(item)
        }
        for (item in defaultLiveModels) {
            if (seenIds.add(item.second)) merged.add(item)
        }
        uiState = uiState.copy(modelOptions = merged)
    }

    private fun parseModelsFromResponse(jsonStr: String): Pair<List<String>, List<Pair<String, String>>> {
        val chatModels = mutableListOf<String>()
        val liveModels = mutableListOf<Pair<String, String>>()
        try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("models") ?: return Pair(chatModels, liveModels)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val fullName = obj.optString("name", "")
                val displayName = obj.optString("displayName", fullName.removePrefix("models/"))
                val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue

                var supportsGenerate = false
                var supportsBidi = false
                for (m in 0 until methods.length()) {
                    when (methods.optString(m)) {
                        "generateContent" -> supportsGenerate = true
                        "bidiGenerateContent" -> supportsBidi = true
                    }
                }

                val shortId = fullName.removePrefix("models/")
                val lowerId = shortId.lowercase()

                if (supportsBidi && lowerId.contains("gemini")) {
                    liveModels.add("$displayName (Active Live)" to fullName)
                }

                if (supportsGenerate &&
                    lowerId.startsWith("gemini-") &&
                    !lowerId.contains("embedding") &&
                    !lowerId.contains("tts") &&
                    !lowerId.contains("image") &&
                    !lowerId.contains("robotics") &&
                    !lowerId.contains("computer-use") &&
                    !lowerId.contains("customtools")
                ) {
                    chatModels.add(shortId)
                }
            }
        } catch (_: Exception) {}

        val sortedChat = chatModels.sortedWith(
            compareByDescending<String> { it.contains("flash") && !it.contains("exp") }
                .thenByDescending { it.contains("flash-lite") || it.contains("flash") }
                .thenByDescending { it }
        )

        return Pair(sortedChat, liveModels)
    }

    private fun testGeminiApiKey() {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please enter a valid Gemini API Key first!", Toast.LENGTH_SHORT).show()
            return
        }

        uiState = uiState.copy(isTestingApi = true, apiTestButtonLabel = "CHECKING ACTIVE MODELS...")

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val listRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        client.newCall(listRequest).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    uiState = uiState.copy(isTestingApi = false, apiTestButtonLabel = "TEST GEMINI API")
                    Toast.makeText(this@SettingsActivity, "Connection Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val listResponseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    runOnUiThread {
                        uiState = uiState.copy(isTestingApi = false, apiTestButtonLabel = "TEST GEMINI API")
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Gemini API Key Error ❌")
                            .setMessage("Error code ${response.code}:\n$listResponseStr")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return
                }

                val (activeChatModels, activeLiveModels) = parseModelsFromResponse(listResponseStr)
                if (activeChatModels.isNotEmpty()) {
                    getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("cached_active_chat_models", JSONArray(activeChatModels.take(6)).toString())
                        .apply()
                }
                if (activeLiveModels.isNotEmpty()) {
                    runOnUiThread { mergeLiveModels(activeLiveModels) }
                }

                val modelsToTry = if (activeChatModels.isNotEmpty()) {
                    activeChatModels.take(6)
                } else {
                    listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-3.8-flash")
                }

                tryModelAtIndex(client, apiKey, modelsToTry, activeLiveModels.map { it.second.removePrefix("models/") }, 0)
            }
        })
    }

    private fun tryModelAtIndex(
        client: OkHttpClient,
        apiKey: String,
        models: List<String>,
        activeLiveIds: List<String>,
        index: Int
    ) {
        if (index >= models.size) {
            runOnUiThread {
                uiState = uiState.copy(isTestingApi = false, apiTestButtonLabel = "TEST GEMINI API")
                val activeListSummary = models.joinToString("\n• ", prefix = "• ")
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("API Key Valid, Models Busy ⚠️")
                    .setMessage("Your API key is active! Discovered active models:\n$activeListSummary\n\nAll returned temporary 503/429 load errors right now.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            return
        }

        val modelId = models[index]
        runOnUiThread {
            uiState = uiState.copy(apiTestButtonLabel = "TESTING $modelId...")
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", "Say hello in one short sentence.")
                ))
            ))
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    uiState = uiState.copy(isTestingApi = false, apiTestButtonLabel = "TEST GEMINI API")
                    Toast.makeText(this@SettingsActivity, "Test Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseStr = response.body?.string() ?: ""
                val code = response.code
                if (response.isSuccessful) {
                    runOnUiThread {
                        uiState = uiState.copy(isTestingApi = false, apiTestButtonLabel = "TEST GEMINI API")
                        val activeSummary = models.take(4).joinToString(", ")
                        val liveSummary = if (activeLiveIds.isNotEmpty()) {
                            "\n\nActive Live Audio Models:\n• " + activeLiveIds.take(4).joinToString("\n• ")
                        } else ""
                        val text = try {
                            JSONObject(responseStr)
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                        } catch (_: Exception) {
                            "Connected successfully!"
                        }
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Gemini API Active ✅")
                            .setMessage("Tested Model: $modelId\nResponse:\n$text\n\nActive Chat Models:\n$activeSummary$liveSummary")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else if ((code == 503 || code == 404 || code == 429 || code == 400) && index + 1 < models.size) {
                    tryModelAtIndex(client, apiKey, models, activeLiveIds, index + 1)
                } else {
                    runOnUiThread {
                        uiState = uiState.copy(isTestingApi = false, apiTestButtonLabel = "TEST GEMINI API")
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Gemini API Test Failed ❌")
                            .setMessage("Model: $modelId\nError code $code:\n$responseStr")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        })
    }

    private fun checkForUpdatesManually() {
        val repo = GitHubUpdateChecker.getConfiguredRepo(this)
        uiState = uiState.copy(
            isCheckingUpdates = true,
            updateButtonLabel = "CHECKING FOR UPDATES...",
            updateServiceStatus = "Update Service: Checking..."
        )

        RemoteConfigUpdateManager.checkForUpdates(this, forceRefresh = true, repoOverride = repo) { outcome ->
            runOnUiThread {
                val syncLabel = if (outcome.remoteConfigSynced) "Active" else "Ready"
                uiState = uiState.copy(
                    isCheckingUpdates = false,
                    updateButtonLabel = "CHECK FOR UPDATES",
                    updateServiceStatus = "Update Service: $syncLabel (Latest v${outcome.remoteLatestVersion.removePrefix("v").removePrefix("V")})"
                )

                when (val result = outcome.checkResult) {
                    is GitHubUpdateChecker.CheckResult.UpdateAvailable -> {
                        UpdateDialogHelper.showUpdateDialog(
                            activity = this@SettingsActivity,
                            release = result.release,
                            isMandatory = outcome.isMandatory
                        )
                    }
                    is GitHubUpdateChecker.CheckResult.UpToDate -> {
                        val latestNote = if (result.latestTag != null) " (${result.latestTag})" else ""
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Up to Date ✅")
                            .setMessage("MYRA v${result.currentVersion} is already the latest version$latestNote.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    is GitHubUpdateChecker.CheckResult.Error -> {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Update Check Info ⚠️")
                            .setMessage(result.message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }
}
