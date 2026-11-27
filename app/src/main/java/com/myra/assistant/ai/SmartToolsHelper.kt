package com.myra.assistant.ai

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object SmartToolsHelper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun getBatteryInfo(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val statusIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = statusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f
        val stateLabel = if (isCharging) "charging" else "on battery"
        return if (tempC > 0f) {
            "Battery is at $pct% ($stateLabel), temperature ${"%.1f".format(Locale.US, tempC)}°C."
        } else {
            "Battery is at $pct% ($stateLabel)."
        }
    }

    fun getDeviceInfo(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)

        val stat = StatFs(Environment.getDataDirectory().path)
        val availStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
        val totalStorageGb = (stat.blockCountLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)

        return "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}. " +
            "RAM free: ${"%.1f".format(Locale.US, availRamGb)}/${"%.1f".format(Locale.US, totalRamGb)} GB. " +
            "Storage free: ${"%.1f".format(Locale.US, availStorageGb)}/${"%.1f".format(Locale.US, totalStorageGb)} GB."
    }

    fun adjustBrightness(context: Context, deltaPercent: Int): String {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Please allow 'Modify system settings' for MYRA so I can adjust screen brightness."
        }

        return try {
            val cr = context.contentResolver
            Settings.System.putInt(
                cr,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            val step = (255f * (deltaPercent / 100f)).roundToInt()
            val updated = (current + step).coerceIn(15, 255)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, updated)
            val pct = ((updated / 255f) * 100f).roundToInt()
            "Screen brightness set to $pct%."
        } catch (e: Exception) {
            "Unable to change brightness: ${e.message}"
        }
    }

    fun dispatchMediaKey(context: Context, keyCode: Int): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime + 10, KeyEvent.ACTION_UP, keyCode, 0))
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Toggled music play/pause."
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Skipped to next track."
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Returned to previous track."
            else -> "Media command sent."
        }
    }

    fun getLocationSummary(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Location permission is not granted yet. Enable Location in MYRA Permissions to check your area."
        }

        val loc = getLastKnownLocation(context)
            ?: return "Unable to get a recent location fix right now. Make sure GPS/Location is turned on."

        val place = reverseGeocodeName(context, loc.latitude, loc.longitude)
        return if (!place.isNullOrBlank()) {
            "You are currently near $place (${"%.3f".format(Locale.US, loc.latitude)}, ${"%.3f".format(Locale.US, loc.longitude)})."
        } else {
            "Current coordinates: ${"%.4f".format(Locale.US, loc.latitude)}, ${"%.4f".format(Locale.US, loc.longitude)}."
        }
    }

    fun fetchWeather(context: Context, requestedCity: String?): String {
        return try {
            var cityLabel: String? = requestedCity?.trim()?.takeIf { it.isNotEmpty() }
            var lat: Double? = null
            var lon: Double? = null

            if (cityLabel == null) {
                val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
                val consent = prefs.getBoolean("memory_consent_enabled", false)
                if (consent) {
                    cityLabel = prefs.getString("pref_city", null)?.trim()?.takeIf { it.isNotEmpty() }
                }
            }

            if (cityLabel != null) {
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${
                    URLEncoder.encode(cityLabel, "UTF-8")
                }&count=1&language=en&format=json"
                val geoReq = Request.Builder().url(geoUrl).get().build()
                httpClient.newCall(geoReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val first = results.getJSONObject(0)
                            lat = first.getDouble("latitude")
                            lon = first.getDouble("longitude")
                            val name = first.optString("name", cityLabel)
                            val country = first.optString("country", "")
                            cityLabel = if (country.isNotBlank()) "$name, $country" else name
                        }
                    }
                }
            } else if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                val loc = getLastKnownLocation(context)
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    cityLabel = reverseGeocodeName(context, lat, lon) ?: "your area"
                }
            }

            // Fallback default to Dhaka if neither city nor location permission is available
            val finalLat = lat ?: 23.8103
            val finalLon = lon ?: 90.4125
            val finalLabel = cityLabel ?: "Dhaka"

            val weatherUrl =
                "https://api.open-meteo.com/v1/forecast?latitude=$finalLat&longitude=$finalLon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
            val req = Request.Builder().url(weatherUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return "Weather service returned HTTP ${resp.code}."
                val json = JSONObject(resp.body?.string().orEmpty())
                val current = json.getJSONObject("current")
                val temp = current.optDouble("temperature_2m", 0.0)
                val humidity = current.optInt("relative_humidity_2m", 0)
                val wind = current.optDouble("wind_speed_10m", 0.0)
                val code = current.optInt("weather_code", 0)
                val desc = describeWeatherCode(code)
                "Weather in $finalLabel: $desc, ${"%.1f".format(Locale.US, temp)}°C, humidity $humidity%, wind ${"%.0f".format(Locale.US, wind)} km/h."
            }
        } catch (e: Exception) {
            "Unable to fetch weather right now: ${e.message ?: "Network error"}"
        }
    }

    fun evaluateMath(rawExpression: String): String {
        val normalized = rawExpression.lowercase(Locale.ROOT)
            .replace("calculate", "")
            .replace("what is", "")
            .replace("koto hoy", "")
            .replace("hisab koro", "")
            .replace("plus", "+")
            .replace("jog", "+")
            .replace("minus", "-")
            .replace("biyog", "-")
            .replace("times", "*")
            .replace("multiplied by", "*")
            .replace("gun", "*")
            .replace("into", "*")
            .replace("x", "*")
            .replace("divided by", "/")
            .replace("divide by", "/")
            .replace("bhag", "/")
            .replace("vag", "/")
            .replace("percent of", "*0.01*")
            .replace("%", "*0.01")
            .replace("?", "")
            .trim()

        val cleaned = normalized.filter { it.isDigit() || it in setOf('+', '-', '*', '/', '.', '(', ')') }
        if (cleaned.isEmpty()) {
            return "Please say a math expression like 250 plus 145 or 45 times 12."
        }

        return try {
            val value = ExpressionParser(cleaned).parse()
            val df = DecimalFormat("#,##0.####")
            "Result: ${df.format(value)}"
        } catch (e: Exception) {
            "Could not calculate \"$cleaned\": ${e.message ?: "invalid expression"}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            val candidate = try {
                if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null
            } catch (_: Exception) {
                null
            } ?: continue
            if (best == null || candidate.accuracy < best.accuracy) {
                best = candidate
            }
        }
        return best
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeName(context: Context, lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull() ?: return null
            listOfNotNull(addr.subLocality, addr.locality ?: addr.subAdminArea, addr.countryName)
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Moderate weather"
    }

    private class ExpressionParser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            pos++
            ch = if (pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected character")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    eat('+'.code) -> x + parseTerm()
                    eat('-'.code) -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw IllegalArgumentException("Division by zero")
                        x /= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            val startPos = pos
            val x: Double
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Invalid number")
            }
            return x
        }
    }
}
