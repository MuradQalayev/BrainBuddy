package com.muradgalayev.brainbuddy.data.google

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCalendarTokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Reactive view so the Settings UI updates when SyncCoordinator pulls the linked
    // email from Supabase after sign-in. Seeded from prefs at construction.
    private val _linkedEmail = MutableStateFlow(prefs.getString(KEY_LINKED_EMAIL, null))
    val linkedEmail: StateFlow<String?> = _linkedEmail.asStateFlow()

    fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
            clearAccessTokenOnly()
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

    fun getLinkedEmail(): String? = _linkedEmail.value

    fun saveLinkedEmail(email: String?) {
        prefs.edit {
            if (email.isNullOrBlank()) remove(KEY_LINKED_EMAIL)
            else putString(KEY_LINKED_EMAIL, email)
        }
        _linkedEmail.value = email?.takeIf { it.isNotBlank() }
    }

    // The access token expires hourly, but the user's *consent* doesn't —
    // having a linked email means we can attempt a silent re-authorization
    // to mint a fresh access token without UI.
    fun isLinked(): Boolean = _linkedEmail.value != null

    /**
     * Nuke everything — used only for an explicit "Disconnect Google Calendar" action
     * or account revoke. Supabase sign-out uses [clearForSupabaseSignOut] instead so
     * the link can be re-hydrated on next sign-in.
     */
    fun clear() {
        prefs.edit { clear() }
        _linkedEmail.value = null
    }

    /**
     * On Supabase sign-out we don't want to revoke the Google grant on this device —
     * the same user (or a different Supabase account) may sign in and re-pull the link
     * from the profiles table. We just drop the short-lived access token; the linked
     * email will be reset by SyncCoordinator when a session next exists.
     */
    fun clearForSupabaseSignOut() {
        clearAccessTokenOnly()
        prefs.edit { remove(KEY_LINKED_EMAIL) }
        _linkedEmail.value = null
    }

    private fun clearAccessTokenOnly() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_EXPIRES_AT)
        }
    }

    private companion object {
        const val PREFS_NAME = "google_calendar_token"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_EXPIRES_AT = "expires_at_ms"
        const val KEY_LINKED_EMAIL = "linked_email"
        const val DEFAULT_LIFETIME_SECONDS = 3600L
    }
}
