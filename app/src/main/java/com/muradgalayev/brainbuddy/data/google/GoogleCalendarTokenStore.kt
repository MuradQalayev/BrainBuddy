package com.muradgalayev.brainbuddy.data.google

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCalendarTokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
            clear()
            return null
        }
        return token
    }

    // Returns the stored token regardless of expiry. Used for revocation —
    // Google will accept a recently-expired token for /revoke, and even if it
    // doesn't, we still want to clear local state.
    fun peekRawAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun saveAccessToken(token: String, lifetimeSeconds: Long = DEFAULT_LIFETIME_SECONDS) {
        val skewMs = 60_000L
        val expiresAt = System.currentTimeMillis() + lifetimeSeconds * 1000L - skewMs
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, token)
            putLong(KEY_EXPIRES_AT, expiresAt)
        }
    }

    fun getLinkedEmail(): String? = prefs.getString(KEY_LINKED_EMAIL, null)

    fun saveLinkedEmail(email: String) {
        prefs.edit { putString(KEY_LINKED_EMAIL, email) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS_NAME = "google_calendar_token"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_EXPIRES_AT = "expires_at_ms"
        const val KEY_LINKED_EMAIL = "linked_email"
        const val DEFAULT_LIFETIME_SECONDS = 3600L
    }
}
