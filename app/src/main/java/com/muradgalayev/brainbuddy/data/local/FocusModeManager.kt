package com.muradgalayev.brainbuddy.data.local

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var previousInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL
    private var isDndActiveByUs = false

    fun hasPermission(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    fun getPermissionIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    fun enableDnd(): Boolean {
        if (!hasPermission()) return false
        return try {
            previousInterruptionFilter = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            isDndActiveByUs = true
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun disableDnd(): Boolean {
        if (!hasPermission() || !isDndActiveByUs) return false
        return try {
            notificationManager.setInterruptionFilter(previousInterruptionFilter)
            isDndActiveByUs = false
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun isActiveByUs(): Boolean = isDndActiveByUs
}
