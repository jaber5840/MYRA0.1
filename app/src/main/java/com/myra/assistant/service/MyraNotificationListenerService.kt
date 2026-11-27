package com.myra.assistant.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.myra.assistant.model.CapturedNotification

class MyraNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MyraNotifListener"

        fun isPermissionGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val componentName = ComponentName(context, MyraNotificationListenerService::class.java)
            return flat.contains(componentName.flattenToString()) ||
                flat.contains(componentName.flattenToShortString())
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListener connected")
        syncActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        val notification = toCapturedNotification(sbn) ?: return
        MyraVoiceSession.addCapturedNotification(notification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        syncActiveNotifications()
    }

    private fun syncActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            val list = active
                .filter { it.packageName != packageName }
                .mapNotNull { toCapturedNotification(it) }
                .sortedByDescending { it.timestamp }
                .take(15)
            MyraVoiceSession.replaceCapturedNotifications(list)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync active notifications: ${e.message}")
        }
    }

    private fun toCapturedNotification(sbn: StatusBarNotification): CapturedNotification? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null

        val appLabel = try {
            val ai = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            sbn.packageName.substringAfterLast('.')
        }

        return CapturedNotification(
            appName = appLabel,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )
    }
}
