package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand
import java.util.Locale

object CommandParser {

    fun parse(text: String?): AppCommand? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase(Locale.ROOT).trim()

        // Screen share / screen capture controls (checked before open/close app rules)
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

        // System controls
        if (lower.contains("volume up") || lower.contains("volume badhao") || lower.contains("volume aawaz badhao")) {
            return AppCommand(AppCommand.TYPE_VOLUME_UP)
        }
        if (lower.contains("volume down") || lower.contains("volume kam karo") || lower.contains("volume ghataye")) {
            return AppCommand(AppCommand.TYPE_VOLUME_DOWN)
        }
        if (lower.contains("torch on") || lower.contains("flashlight on") || lower.contains("torch jalaye") || lower.contains("torch chalu")) {
            return AppCommand(AppCommand.TYPE_FLASHLIGHT_ON)
        }
        if (lower.contains("torch off") || lower.contains("flashlight off") || lower.contains("torch band")) {
            return AppCommand(AppCommand.TYPE_FLASHLIGHT_OFF)
        }
        if (lower.contains("wifi on") || lower.contains("wifi chalu")) {
            return AppCommand(AppCommand.TYPE_WIFI_ON)
        }
        if (lower.contains("wifi off") || lower.contains("wifi band")) {
            return AppCommand(AppCommand.TYPE_WIFI_OFF)
        }
        if (lower.contains("bluetooth on") || lower.contains("bluetooth chalu")) {
            return AppCommand(AppCommand.TYPE_BLUETOOTH_ON)
        }
        if (lower.contains("bluetooth off") || lower.contains("bluetooth band")) {
            return AppCommand(AppCommand.TYPE_BLUETOOTH_OFF)
        }

        // Prime contact voice commands
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

        // Close app
        if (lower.contains("band karo") || lower.contains("close app") || lower.contains("close ")) {
            val appName = extractAppNameForClose(lower)
            if (appName.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_CLOSE_APP, mapOf("app_name" to appName))
            }
        }

        // Open app
        if (lower.contains("kholo") || lower.contains("open ") || lower.contains("chalu karo")) {
            val appName = extractAppNameForOpen(lower)
            if (appName.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_OPEN_APP, mapOf("app_name" to appName))
            }
        }

        // WhatsApp call / msg
        if (lower.contains("whatsapp karo") || lower.contains("whatsapp message") || lower.contains("whatsapp msg")) {
            val (name, msg) = extractNameAndMessage(lower, "whatsapp")
            return AppCommand(AppCommand.TYPE_WHATSAPP_MSG, mapOf("name" to name, "message" to msg))
        }

        // Call
        if (lower.contains("call karo") || lower.contains("call ") || lower.contains("phone karo")) {
            val name = extractNameForCall(lower)
            if (name.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_CALL, mapOf("name" to name))
            }
        }

        // SMS
        if (lower.contains("sms bhejo") || lower.contains("message bhejo") || lower.contains("send sms")) {
            val (name, msg) = extractNameAndMessage(lower, "sms")
            return AppCommand(AppCommand.TYPE_SMS, mapOf("name" to name, "message" to msg))
        }

        return null
    }

    private fun extractAppNameForOpen(lower: String): String {
        return lower
            .replace("kholo", "")
            .replace("open", "")
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
            .replace("call", "")
            .replace("ko", "")
            .replace("phone karo", "")
            .replace("please", "")
            .trim()
    }

    private fun extractNameAndMessage(lower: String, prefix: String): Pair<String, String> {
        val clean = lower
            .replace("whatsapp karo", "")
            .replace("whatsapp message", "")
            .replace("sms bhejo", "")
            .replace("message bhejo", "")
            .replace("send sms", "")
            .trim()

        val parts = clean.split(" text ", " msg ", " message ", " ko ")
        return if (parts.size >= 2) {
            Pair(parts[0].trim(), parts[1].trim())
        } else {
            Pair(clean, "Hello")
        }
    }
}
