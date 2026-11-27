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

    private val modelOptions = listOf(
        "Native Audio (Human Voice) - DEFAULT" to "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "Flash Live (Fast)" to "models/gemini-2.0-flash-live-001",
        "Pro Audio Dialog" to "models/gemini-2.5-flash-preview-native-audio-dialog"
    )

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

    private fun testGeminiApiKey() {
        var apiKey = binding.apiKeyInput.text.toString().trim()
        if (apiKey.isEmpty()) {
            apiKey = BuildConfig.GEMINI_API_KEY
        }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Toast.makeText(this, "Please enter a valid Gemini API Key first!", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Testing Gemini API...", Toast.LENGTH_SHORT).show()

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", "Say hello in one short sentence.")
                ))
            ))
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = okhttp3.Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "Test Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseStr = response.body?.string() ?: ""
                runOnUiThread {
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(responseStr)
                            val text = json.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                            AlertDialog.Builder(this@SettingsActivity)
                                .setTitle("Gemini API Test Success ✅")
                                .setMessage("Response from Gemini:\n\n$text")
                                .setPositiveButton("OK", null)
                                .show()
                        } catch (e: Exception) {
                            AlertDialog.Builder(this@SettingsActivity)
                                .setTitle("Gemini API Test Success ✅")
                                .setMessage("Connected successfully, but parse error: ${e.message}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    } else {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Gemini API Test Failed ❌")
                            .setMessage("Error code ${response.code}:\n$responseStr")
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
        val modelAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            modelOptions.map { it.first }
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

        binding.apiKeyInput.setText(prefs.getString("api_key", ""))
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
