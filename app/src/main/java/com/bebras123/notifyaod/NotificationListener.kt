package com.bebras123.notifyaod

import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.provider.Settings

private fun loadNotificationData(context: Context): List<NotificationData> {
    val sharedPref = context.getSharedPreferences("notificationData", Context.MODE_PRIVATE)
    val jsonStr = sharedPref.getString("data", "[]") ?: "[]"
    val jsonArray = org.json.JSONArray(jsonStr)
    val result = mutableListOf<NotificationData>()
    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        result.add(
            NotificationData(
                packageName = jsonObject.getString("packageName"),
                importance = jsonObject.getInt("importance")
            )
        )
    }
    return result
}

object WhitelistCache {
    private var cache: List<NotificationData>? = null

    fun get(context: Context): List<NotificationData> {
        if (cache == null) {
            log("loading saved")
            cache = loadNotificationData(context)
        }
        return cache!!
    }

    // Call this method whenever the whitelist changes (e.g., after adding or removing entries)
    fun invalidate() {
        cache = null
    }
}


class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val context = applicationContext
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = sbn.notification.channelId
        val channel = notificationManager.getNotificationChannel(channelId)
        val importance = channel?.importance ?: 999

        // Use cached data instead of reloading from SharedPreferences each time
        val savedData = WhitelistCache.get(context)

        val isInSavedList = savedData.any {
            it.packageName == sbn.packageName && (it.importance == importance || it.importance == -1)
        }

        log(
            "NotificationListener: Notification posted from ${sbn.packageName}. " +
                    "Notification channel ID: $channelId. Importance: $importance"
        )

        if (!isInSavedList) {
            log("NotificationListener: enabling AOD")
            setAOD(true)
        } else {
            log("NotificationListener: Whitelisted notification ignored.")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        val context = applicationContext
        val savedData = WhitelistCache.get(context)
        val activeNotifications = activeNotifications

        val hasUnlisted = activeNotifications.any { notif ->
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chId = notif.notification.channelId
            val ch = notifManager.getNotificationChannel(chId)
            val imp = ch?.importance ?: 999

            savedData.none {
                it.packageName == notif.packageName && (it.importance == imp || it.importance == -1)
            }
        }

        if (!hasUnlisted) {
            log("NotificationListener: Only whitelisted notifications remain. Disabling AOD.")
            setAOD(false)
        }
    }

    private fun setAOD(enabled: Boolean) {
        try {
            val value = if (enabled) 1 else 0
            Settings.Secure.putInt(contentResolver, "doze_always_on", value)
            log("NotificationListener: AOD set to $enabled")
        } catch (e: Exception) {
            log(
                "NotificationListener: Failed to change AOD setting. " +
                        "Ensure your app is a system app or the device is rooted. Exception: ${e.message}"
            )
        }
    }
}
