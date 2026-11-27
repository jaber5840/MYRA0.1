package com.myra.assistant.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.BuildConfig
import com.myra.assistant.R
import com.myra.assistant.databinding.ActivitySettingsBinding
import com.myra.assistant.service.AccessibilityHelperService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val primeContacts = mutableListOf<Pair<String, String>>()
    private lateinit var primeAdapter: PrimeContactAdapter

    private val defaultLiveModels = listOf(
        "Gemini 2.5 Flash Native Audio (Default)" to "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "Gemini 2.5 Flash Native Audio (09-2025)" to "models/gemini-2.5-flash-native-audio-preview-09-2025",
        "Gemini 2.5 Flash Live" to "models/gemini-2.5-flash-live-preview"
    )
    private val modelOptions = defaultLiveModels.toMutableList()
    private lateinit var modelAdapter: ArrayAdapter<String>

    private val voiceOptions = listOf(
        "Aoede (Female)" to "Aoede",
        "Charon (Male)" to "Charon",
        "Kore (Female)" to "Kore",
        "Fenrir (Male)" to "Fenrir",
        "Puck (Male)" to "Puck",
        "Leda (Female)" to "Leda",
        "Orus (Male)" to "Orus",
        "Zephyr (Female)" to "Zephyr"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupPrimeContactsRecycler()
        loadSavedSettings()
        checkAccessibilityStatus()
        refreshActiveLiveModelsSilently()

        binding.backBtn.setOnClickListener { finish() }

        binding.addPrimeBtn.setOnClickListener {
            showAddPrimeContactDialog()
        }

        binding.accessibilityCard.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.saveBtn.setOnClickListener {
            saveSettings()
        }

        binding.testApiKeyBtn.setOnClickListener {
            testGeminiApiKey()
        }
    }

    private fun getEffectiveApiKey(): String {
        var apiKey = binding.apiKeyInput.text.toString().trim()
        if (apiKey.isEmpty()) {
            apiKey = BuildConfig.GEMINI_API_KEY
        }
        return if (apiKey == "MY_GEMINI_API_KEY") "" else apiKey
    }

    private fun refreshActiveLiveModelsSilently() {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) return

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = okhttp3.Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val bodyStr = response.body?.string() ?: return
                if (!response.isSuccessful) return
                val discoveredLive = parseModelsFromResponse(bodyStr).second
                if (discoveredLive.isNotEmpty()) {
                    runOnUiThread {
                        updateLiveModelSpinner(discoveredLive)
                    }
                }
            }
        })
    }

    private fun updateLiveModelSpinner(discoveredLive: List<Pair<String, String>>) {
        val currentSelected = modelOptions.getOrNull(binding.modelSpinner.selectedItemPosition)?.second
        val merged = mutableListOf<Pair<String, String>>()
        val seenIds = mutableSetOf<String>()

        for (item in discoveredLive) {
            if (seenIds.add(item.second)) {
                merged.add(item)
            }
        }
        for (item in defaultLiveModels) {
            if (seenIds.add(item.second)) {
                merged.add(item)
            }
        }

        modelOptions.clear()
        modelOptions.addAll(merged)
        modelAdapter.clear()
        modelAdapter.addAll(modelOptions.map { it.first })
        modelAdapter.notifyDataSetChanged()

        val targetIdx = modelOptions.indexOfFirst { it.second == currentSelected }.coerceAtLeast(0)
        binding.modelSpinner.setSelection(targetIdx)
    }

    private fun parseModelsFromResponse(jsonStr: String): Pair<List<String>, List<Pair<String, String>>> {
        val chatModels = mutableListOf<String>()
        val liveModels = mutableListOf<Pair<String, String>>()
        try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("models") ?: return Pair(chatModels, liveModels)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val fullName = obj.optString("name", "") // e.g. "models/gemini-3.8-flash"
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

        // Prioritize stable & newer flash/flash-lite models first
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

        binding.testApiKeyBtn.isEnabled = false
        binding.testApiKeyBtn.text = "CHECKING ACTIVE MODELS..."

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Step 1: Discover all currently active models directly from Gemini API
        val listRequest = okhttp3.Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        client.newCall(listRequest).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    binding.testApiKeyBtn.isEnabled = true
                    binding.testApiKeyBtn.text = "TEST GEMINI API"
                    Toast.makeText(this@SettingsActivity, "Connection Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val listResponseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    runOnUiThread {
                        binding.testApiKeyBtn.isEnabled = true
                        binding.testApiKeyBtn.text = "TEST GEMINI API"
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Gemini API Key Error ❌")
                            .setMessage("Error code ${response.code}:\n$listResponseStr")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return
                }

                val (activeChatModels, activeLiveModels) = parseModelsFromResponse(listResponseStr)
                if (activeLiveModels.isNotEmpty()) {
                    runOnUiThread {
                        updateLiveModelSpinner(activeLiveModels)
                    }
                }

                val modelsToTry = if (activeChatModels.isNotEmpty()) {
                    activeChatModels.take(6)
                } else {
                    listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-3.8-flash")
                }

                runOnUiThread {
                    binding.testApiKeyBtn.text = "TESTING ${modelsToTry.first()}..."
                }
                tryModelAtIndex(client, apiKey, modelsToTry, activeLiveModels.map { it.second.removePrefix("models/") }, 0)
            }
        })
    }

    private fun tryModelAtIndex(
        client: okhttp3.OkHttpClient,
        apiKey: String,
        models: List<String>,
        activeLiveIds: List<String>,
        index: Int
    ) {
        if (index >= models.size) {
            runOnUiThread {
                binding.testApiKeyBtn.isEnabled = true
                binding.testApiKeyBtn.text = "TEST GEMINI API"
                val activeListSummary = models.joinToString("\n• ", prefix = "• ")
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("API Key Valid, Models Busy ⚠️")
                    .setMessage("Your API key is active! Discovered active models:\n$activeListSummary\n\nAll returned temporary 503/429 load errors right now. Please try again in a moment.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            return
        }

        val modelId = models[index]
        runOnUiThread {
            binding.testApiKeyBtn.text = "TESTING $modelId..."
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", "Say hello in one short sentence.")
                ))
            ))
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = okhttp3.Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    binding.testApiKeyBtn.isEnabled = true
                    binding.testApiKeyBtn.text = "TEST GEMINI API"
                    Toast.makeText(this@SettingsActivity, "Test Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseStr = response.body?.string() ?: ""
                val code = response.code
                if (response.isSuccessful) {
                    runOnUiThread {
                        binding.testApiKeyBtn.isEnabled = true
                        binding.testApiKeyBtn.text = "TEST GEMINI API"
                        val activeSummary = models.take(4).joinToString(", ")
                        val liveSummary = if (activeLiveIds.isNotEmpty()) {
                            "\n\nActive Live Audio Models:\n• " + activeLiveIds.take(4).joinToString("\n• ")
                        } else ""
                        try {
                            val json = JSONObject(responseStr)
                            val text = json.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                            AlertDialog.Builder(this@SettingsActivity)
                                .setTitle("Gemini API Active ✅")
                                .setMessage("Tested Model: $modelId\nResponse:\n$text\n\nActive Chat Models:\n$activeSummary$liveSummary")
                                .setPositiveButton("OK", null)
                                .show()
                        } catch (e: Exception) {
                            AlertDialog.Builder(this@SettingsActivity)
                                .setTitle("Gemini API Active ✅")
                                .setMessage("Connected via $modelId!\n\nActive Chat Models:\n$activeSummary$liveSummary")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                } else if ((code == 503 || code == 404 || code == 429 || code == 400) && index + 1 < models.size) {
                    tryModelAtIndex(client, apiKey, models, activeLiveIds, index + 1)
                } else {
                    runOnUiThread {
                        binding.testApiKeyBtn.isEnabled = true
                        binding.testApiKeyBtn.text = "TEST GEMINI API"
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

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
    }

    private fun setupSpinners() {
        modelAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            modelOptions.map { it.first }.toMutableList()
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.modelSpinner.adapter = modelAdapter

        val voiceAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            voiceOptions.map { it.first }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.voiceSpinner.adapter = voiceAdapter
    }

    private fun setupPrimeContactsRecycler() {
        primeAdapter = PrimeContactAdapter(primeContacts) { pos ->
            primeContacts.removeAt(pos)
            primeAdapter.notifyItemRemoved(pos)
        }
        binding.primeContactsRecycler.layoutManager = LinearLayoutManager(this)
        binding.primeContactsRecycler.adapter = primeAdapter
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        val savedKey = prefs.getString("api_key", "")?.trim() ?: ""
        val effectiveKey = if (savedKey.isEmpty() && BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            BuildConfig.GEMINI_API_KEY
        } else {
            savedKey
        }
        binding.apiKeyInput.setText(effectiveKey)
        binding.userNameInput.setText(prefs.getString("user_name", "Alex"))

        val savedModel = prefs.getString("gemini_model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
        val modelIdx = modelOptions.indexOfFirst { it.second == savedModel }.coerceAtLeast(0)
        binding.modelSpinner.setSelection(modelIdx)

        val savedVoice = prefs.getString("gemini_voice", "Aoede")
        val voiceIdx = voiceOptions.indexOfFirst { it.second == savedVoice }.coerceAtLeast(0)
        binding.voiceSpinner.setSelection(voiceIdx)

        when (prefs.getString("personality_mode", "GF")) {
            "Professional" -> binding.radioProfessional.isChecked = true
            "Assistant" -> binding.radioAssistant.isChecked = true
            else -> binding.radioGf.isChecked = true
        }

        primeContacts.clear()
        val jsonStr = prefs.getString("prime_contacts_json", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    primeContacts.add(Pair(obj.optString("name", ""), obj.optString("number", "")))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        primeAdapter.notifyDataSetChanged()
    }

    private fun checkAccessibilityStatus() {
        val isEnabled = AccessibilityHelperService.isEnabled(this)
        if (isEnabled) {
            binding.accessibilityStatusText.text = "Status: ACTIVE ✅"
            binding.accessibilityStatusText.setTextColor(resources.getColor(R.color.success_green, theme))
        } else {
            binding.accessibilityStatusText.text = "Status: DISABLED ❌ (Tap to enable)"
            binding.accessibilityStatusText.setTextColor(resources.getColor(R.color.red_accent, theme))
        }
    }

    private fun showAddPrimeContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_prime_contact, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.dialogNameInput)
        val numberInput = dialogView.findViewById<EditText>(R.id.dialogNumberInput)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("ADD") { _, _ ->
                val name = nameInput.text.toString().trim()
                val number = numberInput.text.toString().trim()
                if (name.isNotEmpty() || number.isNotEmpty()) {
                    primeContacts.add(Pair(name, number))
                    primeAdapter.notifyItemInserted(primeContacts.size - 1)
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        val apiKey = binding.apiKeyInput.text.toString().trim()
        val userName = binding.userNameInput.text.toString().trim().ifEmpty { "Alex" }
        val selectedModel = modelOptions[binding.modelSpinner.selectedItemPosition].second
        val selectedVoice = voiceOptions[binding.voiceSpinner.selectedItemPosition].second

        val personality = when {
            binding.radioProfessional.isChecked -> "Professional"
            binding.radioAssistant.isChecked -> "Assistant"
            else -> "GF"
        }

        val array = JSONArray()
        for (c in primeContacts) {
            val obj = JSONObject().apply {
                put("name", c.first)
                put("number", c.second)
            }
            array.put(obj)
        }

        prefs.edit()
            .putString("api_key", apiKey)
            .putString("user_name", userName)
            .putString("gemini_model", selectedModel)
            .putString("gemini_voice", selectedVoice)
            .putString("personality_mode", personality)
            .putString("prime_contacts_json", array.toString())
            .apply()

        Toast.makeText(this, "Settings Saved! Restarting connection...", Toast.LENGTH_SHORT).show()
        finish()
    }
}

class PrimeContactAdapter(
    private val items: List<Pair<String, String>>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<PrimeContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.primeItemName)
        val numberText: TextView = view.findViewById(R.id.primeItemNumber)
        val deleteBtn: ImageButton = view.findViewById(R.id.primeItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prime_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = if (item.first.isNotEmpty()) item.first else "Prime Contact"
        holder.numberText.text = item.second
        holder.deleteBtn.setOnClickListener { onDeleteClick(position) }
    }

    override fun getItemCount(): Int = items.size
}
