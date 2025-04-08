package com.bebras123.notifyaod

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification


// The PackageListCache now caches both the notification data list and the packagelist_whitelist boolean.
object PackageListCache {
    private var dataCache: List<NotificationData>? = null
    private var whitelistCache: Boolean? = null

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

    private fun loadPackagelistWhitelist(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences("notificationData", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("packagelist_whitelist", false)
    }

    // Returns the cached list of NotificationData.
    fun getData(context: Context): List<NotificationData> {
        if (dataCache == null) {
            dataCache = loadNotificationData(context)
        }
        return dataCache!!
    }

    // Returns the cached value for packagelist_whitelist.
    fun getWhitelist(context: Context): Boolean {
        if (whitelistCache == null) {
            whitelistCache = loadPackagelistWhitelist(context)
        }
        return whitelistCache!!
    }

    // Invalidate both caches when there is a change in either the data list or the whitelist setting.
    fun invalidate() {
        dataCache = null
        whitelistCache = null
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

        // Use the cache object for both the data and the whitelist flag.
        val savedData = PackageListCache.getData(context)
        val packageWhitelistEnabled = PackageListCache.getWhitelist(context)

        // Check if the notification's package exists in the saved whitelist.
        val isInSavedList = savedData.any {
            it.packageName == sbn.packageName && (it.importance == importance || it.importance == -1)
        }

        log(
            "NotificationListener: Posted from ${sbn.packageName} (Channel: $channelId, Importance: $importance). " +
                    "packagelist_whitelist: $packageWhitelistEnabled"
        )

        if (packageWhitelistEnabled) {
            // When whitelist is enabled, only notifications from a whitelisted package enable AOD.
            if (isInSavedList) {
                log("NotificationListener: Whitelisted notification. Enabling AOD.")
                setAOD(true)
            } else {
                log("NotificationListener: Notification not in whitelist. Disabling AOD.")
                setAOD(false)
            }
        } else {
            // When whitelist is disabled, enable AOD for notifications not in the saved list.
            if (!isInSavedList) {
                log("NotificationListener: Notification not in saved list. Enabling AOD.")
                setAOD(true)
            } else {
                log("NotificationListener: Package-listed notification ignored.")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        val context = applicationContext

        // Get cached values.
        val savedData = PackageListCache.getData(context)
        val packageWhitelistEnabled = PackageListCache.getWhitelist(context)
        val activeNotifs = activeNotifications

        if (packageWhitelistEnabled) {
            // For whitelist-enabled mode: Check if any active notifications are from whitelisted packages.
            val hasWhitelisted = activeNotifs.any { notif ->
                val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val chId = notif.notification.channelId
                val ch = notifManager.getNotificationChannel(chId)
                val imp = ch?.importance ?: 999

                savedData.any {
                    it.packageName == notif.packageName && (it.importance == imp || it.importance == -1)
                }
            }
            if (!hasWhitelisted) {
                log("NotificationListener: No whitelisted notifications remain. Disabling AOD.")
                setAOD(false)
            }
        } else {
            // For non-whitelist mode: Check if there are any notifications not in the saved list.
            val hasUnlisted = activeNotifs.any { notif ->
                val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val chId = notif.notification.channelId
                val ch = notifManager.getNotificationChannel(chId)
                val imp = ch?.importance ?: 999

                savedData.none {
                    it.packageName == notif.packageName && (it.importance == imp || it.importance == -1)
                }
            }
            if (!hasUnlisted) {
                log("NotificationListener: Only package-listed notifications remain. Disabling AOD.")
                setAOD(false)
            }
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
