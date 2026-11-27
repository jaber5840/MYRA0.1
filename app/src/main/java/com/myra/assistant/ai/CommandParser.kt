package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand
import java.util.Locale
import java.util.regex.Pattern

object CommandParser {

    private val WAKE_WORD_PREFIX = Regex("^(hey|hello|hi|ok|oye)?\\s*(myra|mira)[,:\\s]+", RegexOption.IGNORE_CASE)

    fun parseAll(text: String?): List<AppCommand> {
        if (text.isNullOrBlank()) return emptyList()
        val stripped = text.trim().replace(WAKE_WORD_PREFIX, "").trim()
        if (stripped.isEmpty()) return emptyList()

        // If the phrase contains a message body ("saying ...", "message ..."), keep it intact as one command
        val lowerFull = stripped.lowercase(Locale.ROOT)
        if (lowerFull.contains(" saying ") || lowerFull.contains(" whatsapp ") || lowerFull.contains(" sms ")) {
            return listOfNotNull(parseSingle(stripped))
        }

        // Split multi-step commands sequentially ("turn on flashlight and open camera")
        val segments = stripped.split(
            Regex("\\s+(?:and\\s+then|then|and|tarpor|ebong|ar)\\s+|\\s*,\\s*", RegexOption.IGNORE_CASE)
        )
        val commands = mutableListOf<AppCommand>()
        for (segment in segments) {
            val cmd = parseSingle(segment.trim())
            if (cmd != null) commands.add(cmd)
        }
        return commands
    }

    fun parse(text: String?): AppCommand? {
        return parseAll(text).firstOrNull()
    }

    private fun parseSingle(text: String): AppCommand? {
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.ROOT).trim()

        // 1. Confirmation / Cancellation (used when a Call / WhatsApp / SMS confirmation is pending)
        if (lower in setOf("yes", "confirm", "send it", "send", "ok confirm", "haan", "ha", "pathao", "call koro", "do it", "proceed")) {
            return AppCommand(AppCommand.TYPE_CONFIRM_ACTION)
        }
        if (lower in setOf("no", "cancel", "cancel it", "abort", "na", "bad dao", "don't send", "do not send", "stop it")) {
            return AppCommand(AppCommand.TYPE_CANCEL_ACTION)
        }

        // 2. Screen share / screen capture controls (checked before open/close app rules)
        if (lower.contains("screen share off") ||
            lower.contains("screen share band") ||
            lower.contains("screen share bondo") ||
            lower.contains("screen share stop") ||
            lower.contains("stop screen share") ||
            lower.contains("screen sharing off") ||
            lower.contains("screen sharing stop") ||
            lower.contains("screen sharing band") ||
            lower.contains("screen capture off") ||
            lower.contains("screen capture stop") ||
            lower.contains("screen share close") ||
            lower.contains("screen off koro")
        ) {
            return AppCommand(AppCommand.TYPE_SCREEN_SHARE_OFF)
        }
        if (lower.contains("screen share on") ||
            lower.contains("screen share chalu") ||
            lower.contains("screen share start") ||
            lower.contains("start screen share") ||
            lower.contains("share my screen") ||
            lower.contains("share screen") ||
            lower.contains("screen share kor") ||
            lower.contains("screen sharing on") ||
            lower.contains("screen sharing start") ||
            lower.contains("screen capture on") ||
            lower.contains("screen capture start") ||
            lower.contains("screen capture") ||
            lower.contains("amar screen dekho") ||
            lower.contains("mobile screen dekho") ||
            lower.contains("screen dekho") ||
            lower.contains("see my screen")
        ) {
            return AppCommand(AppCommand.TYPE_SCREEN_SHARE_ON)
        }

        // 3. Screenshot & Camera / Photo controls
        if (lower.contains("screenshot") || lower.contains("screen shot")) {
            return AppCommand(AppCommand.TYPE_TAKE_SCREENSHOT)
        }
        if (lower.contains("take a photo") || lower.contains("take photo") || lower.contains("capture photo") ||
            lower.contains("chobi tolo") || lower.contains("photo tolo") || lower.contains("click a picture")
        ) {
            val useFront = lower.contains("selfie") || lower.contains("front")
            return AppCommand(AppCommand.TYPE_TAKE_PHOTO, mapOf("front" to useFront.toString()))
        }
        if (lower.contains("front camera") || lower.contains("selfie camera") || lower.contains("samner camera")) {
            return AppCommand(AppCommand.TYPE_OPEN_CAMERA_FRONT)
        }
        if (lower.contains("back camera") || lower.contains("rear camera") || lower.contains("open camera") || lower.contains("camera kholo")) {
            return AppCommand(AppCommand.TYPE_OPEN_CAMERA_BACK)
        }

        // 4. WhatsApp controls (Prepare message, open chat, or open WhatsApp)
        if (lower.contains("whatsapp")) {
            if (lower == "open whatsapp" || lower == "whatsapp kholo" || lower == "whatsapp") {
                return AppCommand(AppCommand.TYPE_OPEN_APP, mapOf("app_name" to "whatsapp"))
            }
            if ((lower.contains("open") || lower.contains("kholo")) && (lower.contains("chat") || lower.contains("conversation")) && !lower.contains("saying") && !lower.contains("message")) {
                val target = extractWhatsAppChatTarget(text)
                if (target.isNotEmpty()) {
                    return AppCommand(AppCommand.TYPE_WHATSAPP_CHAT, mapOf("name" to target))
                }
            }
            val (name, msg) = extractWhatsAppTargetAndBody(text)
            if (name.isNotEmpty()) {
                return if (msg.isNotEmpty()) {
                    AppCommand(AppCommand.TYPE_WHATSAPP_MSG, mapOf("name" to name, "message" to msg))
                } else {
                    AppCommand(AppCommand.TYPE_WHATSAPP_CHAT, mapOf("name" to name))
                }
            }
        }

        // 5. System Hardware & Device Controls
        if (lower.contains("volume up") || lower.contains("increase volume") || lower.contains("volume badhao") || lower.contains("sound barao")) {
            return AppCommand(AppCommand.TYPE_VOLUME_UP)
        }
        if (lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("volume kam") || lower.contains("sound komao")) {
            return AppCommand(AppCommand.TYPE_VOLUME_DOWN)
        }
        if (lower.contains("brightness up") || lower.contains("increase brightness") || lower.contains("brightness barao")) {
            return AppCommand(AppCommand.TYPE_BRIGHTNESS_UP)
        }
        if (lower.contains("brightness down") || lower.contains("decrease brightness") || lower.contains("lower brightness") || lower.contains("brightness komao")) {
            return AppCommand(AppCommand.TYPE_BRIGHTNESS_DOWN)
        }
        if (lower.contains("torch on") || lower.contains("flashlight on") || lower.contains("turn on flashlight") || lower.contains("torch jalaye") || lower.contains("torch chalu") || lower.contains("light on koro")) {
            return AppCommand(AppCommand.TYPE_FLASHLIGHT_ON)
        }
        if (lower.contains("torch off") || lower.contains("flashlight off") || lower.contains("turn off flashlight") || lower.contains("torch band") || lower.contains("light off koro")) {
            return AppCommand(AppCommand.TYPE_FLASHLIGHT_OFF)
        }
        if (lower.contains("wifi on") || lower.contains("wifi chalu") || lower.contains("open wifi") || lower.contains("wifi settings")) {
            return AppCommand(AppCommand.TYPE_WIFI_ON)
        }
        if (lower.contains("wifi off") || lower.contains("wifi band")) {
            return AppCommand(AppCommand.TYPE_WIFI_OFF)
        }
        if (lower.contains("bluetooth on") || lower.contains("bluetooth chalu") || lower.contains("open bluetooth") || lower.contains("bluetooth settings")) {
            return AppCommand(AppCommand.TYPE_BLUETOOTH_ON)
        }
        if (lower.contains("bluetooth off") || lower.contains("bluetooth band")) {
            return AppCommand(AppCommand.TYPE_BLUETOOTH_OFF)
        }
        if (lower.contains("phone settings") || lower.contains("open settings") || lower.contains("device settings")) {
            return AppCommand(AppCommand.TYPE_OPEN_SETTINGS)
        }
        if (lower.contains("open dialer") || lower.contains("phone dialer") || lower.contains("dialer kholo")) {
            return AppCommand(AppCommand.TYPE_OPEN_DIALER)
        }
        if (lower.contains("open contacts") || lower.contains("show contacts") || lower.contains("contacts kholo")) {
            return AppCommand(AppCommand.TYPE_OPEN_CONTACTS)
        }

        // 6. Media playback controls
        if (lower.contains("play music") || lower.contains("pause music") || lower.contains("resume music") || lower.contains("stop music") || lower.contains("gaan chalu") || lower.contains("gaan bondho")) {
            return AppCommand(AppCommand.TYPE_MUSIC_PLAY_PAUSE)
        }
        if (lower.contains("next song") || lower.contains("next track") || lower.contains("skip song")) {
            return AppCommand(AppCommand.TYPE_MUSIC_NEXT)
        }
        if (lower.contains("previous song") || lower.contains("previous track") || lower.contains("last song")) {
            return AppCommand(AppCommand.TYPE_MUSIC_PREV)
        }

        // 7. Smart Information & Utility Commands
        if (lower.contains("weather") || lower.contains("abohawa") || lower.contains("temperature outside")) {
            val city = extractCityFromWeather(text)
            return AppCommand(AppCommand.TYPE_WEATHER, if (city.isNotEmpty()) mapOf("city" to city) else emptyMap())
        }
        if (lower.contains("what time") || lower.contains("current time") || lower.contains("koto baje") || lower.contains("today's date") || lower.contains("today date") || lower.contains("koto tarikh") || lower == "time" || lower == "date") {
            return AppCommand(AppCommand.TYPE_TIME_DATE)
        }
        if (lower.contains("set alarm") || lower.contains("wake me up") || lower.contains("alarm dao")) {
            val (hour, minute) = extractTimeForAlarm(lower)
            return AppCommand(
                AppCommand.TYPE_SET_ALARM,
                mapOf("hour" to hour.toString(), "minute" to minute.toString(), "message" to "MYRA Alarm")
            )
        }
        if (lower.contains("set timer") || lower.contains("timer for")) {
            val seconds = extractTimerSeconds(lower)
            return AppCommand(AppCommand.TYPE_SET_TIMER, mapOf("seconds" to seconds.toString(), "message" to "MYRA Timer"))
        }
        if (lower.contains("calendar event") || lower.contains("add event") || lower.contains("schedule meeting") || lower.contains("add reminder")) {
            val title = text.replace(Regex("(add|create|schedule)\\s+(a\\s+)?(calendar\\s+event|event|meeting|reminder)(\\s+for|\\s+about)?", RegexOption.IGNORE_CASE), "").trim()
            return AppCommand(AppCommand.TYPE_ADD_CALENDAR_EVENT, mapOf("title" to title.ifEmpty { "MYRA Reminder" }))
        }
        if (lower.contains("calculate") || lower.contains("hisab") || Regex(".*\\d+\\s*[+\\-*/]\\s*\\d+.*").matches(lower)) {
            return AppCommand(AppCommand.TYPE_CALCULATE, mapOf("expression" to text))
        }
        if (lower.contains("search for ") || lower.contains("google search ") || lower.startsWith("search ")) {
            val query = text.replace(Regex("^(google\\s+)?search(\\s+for)?\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (query.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_WEB_SEARCH, mapOf("query" to query))
            }
        }
        if (lower.contains("battery") || lower.contains("charge koto")) {
            return AppCommand(AppCommand.TYPE_BATTERY_INFO)
        }
        if (lower.contains("device info") || lower.contains("phone info") || lower.contains("ram ") || lower.contains("storage")) {
            return AppCommand(AppCommand.TYPE_DEVICE_INFO)
        }
        if (lower.contains("my location") || lower.contains("where am i") || lower.contains("amar location")) {
            return AppCommand(AppCommand.TYPE_LOCATION_INFO)
        }
        if (lower.contains("read notification") || lower.contains("read my notification") || lower.contains("check notification") || lower.contains("notification poro")) {
            return AppCommand(AppCommand.TYPE_READ_NOTIFICATIONS)
        }
        if (lower.startsWith("remember ") || lower.startsWith("mone rekho ")) {
            val note = text.replace(Regex("^(remember|mone rekho)\\s+(that\\s+)?", RegexOption.IGNORE_CASE), "").trim()
            if (note.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_REMEMBER_PREF, mapOf("note" to note))
            }
        }

        // 8. Prime contact voice commands
        if (lower.contains("close friend ko call") || lower.contains("meri jaan ko call") ||
            lower.contains("my love ko call") || lower.contains("call close friend") || lower.contains("call my love")) {
            return AppCommand(AppCommand.TYPE_PRIME_CALL, mapOf("index" to "0"))
        }
        if (lower.contains("second contact ko call") || lower.contains("call my second contact")) {
            return AppCommand(AppCommand.TYPE_PRIME_CALL, mapOf("index" to "1"))
        }
        if (lower.contains("close friend ko message") || lower.contains("meri jaan ko msg") ||
            lower.contains("meri jaan ko message") || lower.contains("message my love")) {
            return AppCommand(AppCommand.TYPE_PRIME_MSG, mapOf("index" to "0"))
        }

        // 9. Close app
        if (lower.contains("band karo") || lower.contains("close app") || lower.startsWith("close ")) {
            val appName = extractAppNameForClose(lower)
            if (appName.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_CLOSE_APP, mapOf("app_name" to appName))
            }
        }

        // 10. Open app
        if (lower.contains("kholo") || lower.startsWith("open ") || lower.contains(" open ") || lower.contains("chalu karo") || lower.contains("launch ")) {
            val appName = extractAppNameForOpen(lower)
            if (appName.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_OPEN_APP, mapOf("app_name" to appName))
            }
        }

        // 11. Call
        if (lower.contains("call karo") || lower.contains("call koro") || lower.startsWith("call ") || lower.contains("call ") || lower.contains("phone karo")) {
            val name = extractNameForCall(lower)
            if (name.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_CALL, mapOf("name" to name))
            }
        }

        // 12. SMS
        if (lower.contains("sms") || lower.contains("message bhejo") || lower.contains("text ")) {
            val (name, msg) = extractSmsTargetAndBody(text)
            if (name.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_SMS, mapOf("name" to name, "message" to msg.ifEmpty { "Hello" }))
            }
        }

        return null
    }

    private fun extractWhatsAppTargetAndBody(original: String): Pair<String, String> {
        val patterns = listOf(
            Pattern.compile("(?:send\\s+(?:a\\s+)?whatsapp\\s+(?:message\\s+)?to|whatsapp\\s+(?:message\\s+)?to|whatsapp)\\s+(.+?)\\s+(?:saying|message|text|that|je|bolo)\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.+?)\\s+(?:ke|ko)\\s+whatsapp\\s+(?:message\\s+|msg\\s+|karo\\s+|koro\\s+)?(?:saying|je|bolo)?\\s*(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("whatsapp\\s+(.+?)\\s+(?:text|msg|message)\\s+(.+)", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(original.trim())
            if (m.find()) {
                val name = m.group(1)?.trim().orEmpty()
                val msg = m.group(2)?.trim().orEmpty()
                if (name.isNotEmpty()) return Pair(name, msg)
            }
        }
        val clean = original.replace(Regex("(send|a|whatsapp|message|msg|to|karo|koro|open|chat|with)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim()
        return Pair(clean, "")
    }

    private fun extractWhatsAppChatTarget(original: String): String {
        return original.replace(Regex("(open|whatsapp|chat|conversation|with|kholo)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    private fun extractSmsTargetAndBody(original: String): Pair<String, String> {
        val p = Pattern.compile("(?:send\\s+(?:an\\s+)?sms\\s+to|text|sms|message\\s+bhejo)\\s+(.+?)\\s+(?:saying|message|text|that)\\s+(.+)", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(original.trim())
        if (m.find()) {
            return Pair(m.group(1)?.trim().orEmpty(), m.group(2)?.trim().orEmpty())
        }
        val clean = original.replace(Regex("(send|an|sms|bhejo|message|text|to)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim()
        val parts = clean.split(" ko ", " ke ")
        return if (parts.size >= 2) Pair(parts[0].trim(), parts[1].trim()) else Pair(clean, "")
    }

    private fun extractCityFromWeather(original: String): String {
        val p = Pattern.compile("(?:weather|temperature)\\s+(?:in|of|at|for)\\s+([a-zA-Z\\s]+)", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(original)
        return if (m.find()) m.group(1)?.trim().orEmpty() else ""
    }

    private fun extractTimeForAlarm(lower: String): Pair<Int, Int> {
        val m = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").matcher(lower)
        if (m.find()) {
            var h = m.group(1)?.toIntOrNull() ?: 7
            val min = m.group(2)?.toIntOrNull() ?: 0
            val amPm = m.group(3)
            if (amPm == "pm" && h in 1..11) h += 12
            if (amPm == "am" && h == 12) h = 0
            return Pair(h.coerceIn(0, 23), min.coerceIn(0, 59))
        }
        return Pair(7, 0)
    }

    private fun extractTimerSeconds(lower: String): Int {
        val m = Pattern.compile("(\\d+)\\s*(second|sec|minute|min|hour|hr)").matcher(lower)
        if (m.find()) {
            val amount = m.group(1)?.toIntOrNull() ?: 1
            return when {
                m.group(2)?.startsWith("hour") == true || m.group(2)?.startsWith("hr") == true -> amount * 3600
                m.group(2)?.startsWith("min") == true -> amount * 60
                else -> amount
            }.coerceAtLeast(5)
        }
        return 60
    }

    private fun extractAppNameForOpen(lower: String): String {
        return lower
            .replace("kholo", "")
            .replace("open", "")
            .replace("launch", "")
            .replace("chalu karo", "")
            .replace("app", "")
            .trim()
    }

    private fun extractAppNameForClose(lower: String): String {
        return lower
            .replace("band karo", "")
            .replace("close", "")
            .replace("app", "")
            .trim()
    }

    private fun extractNameForCall(lower: String): String {
        return lower
            .replace("call karo", "")
            .replace("call koro", "")
            .replace("call", "")
            .replace("phone karo", "")
            .replace("please", "")
            .replace("ko", "")
            .replace("ke", "")
            .trim()
    }
}
