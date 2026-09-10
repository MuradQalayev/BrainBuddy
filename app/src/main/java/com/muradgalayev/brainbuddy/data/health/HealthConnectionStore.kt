package com.muradgalayev.brainbuddy.data.health

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("health_connect_link", Context.MODE_PRIVATE)

    init {
        // the old keys were shared by every Myndora login on this device, so don't migrate that consent
        // to whichever account happens to be signed in
        if (!prefs.getBoolean(KEY_ACCOUNT_SCOPED, false)) {
            prefs.edit {
                remove("connected_at")
                remove("manually_disconnected")
                putBoolean(KEY_ACCOUNT_SCOPED, true)
            }
        }
    }

    fun isEnabled(userId: String): Boolean = prefs.getBoolean(enabledKey(userId), false)
    fun connectedAt(userId: String): String? = prefs.getString(connectedAtKey(userId), null)

    fun markConnected(userId: String): String {
        val timestamp = connectedAt(userId) ?: Instant.now().toString()
        prefs.edit {
            putString(connectedAtKey(userId), timestamp)
            putBoolean(enabledKey(userId), true)
        }
        return timestamp
    }

    fun prepareToConnect(userId: String) = prefs.edit { putBoolean(enabledKey(userId), true) }

    fun markDisconnected(userId: String) = prefs.edit {
        remove(connectedAtKey(userId))
        putBoolean(enabledKey(userId), false)
    }

    private fun enabledKey(userId: String) = "enabled_$userId"
    private fun connectedAtKey(userId: String) = "connected_at_$userId"

    private companion object { const val KEY_ACCOUNT_SCOPED = "account_scoped_v1" }
}
