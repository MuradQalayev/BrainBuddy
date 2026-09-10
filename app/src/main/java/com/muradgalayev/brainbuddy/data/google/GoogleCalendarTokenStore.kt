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

    // reactive view so Settings updates when SyncCoordinator pulls the linked email from Supabase
    // after sign-in. seeded from prefs at construction
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

    // returns the stored token regardless of expiry, for revocation: Google accepts a recently
    // expired token for /revoke, and even if it doesn't we still want to clear local state
    fun peekRawAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun saveAccessToken(token: String, lifetimeSeconds: Long = ASSUMED_LIFETIME_SECONDS) {
        val skewMs = 60_000L
        val expiresAt = System.currentTimeMillis() + lifetimeSeconds * 1000L - skewMs
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, token)
            putLong(KEY_EXPIRES_AT, expiresAt)
        }
    }

    // drop the access token but keep the link. used when Google rejects a token with a 401: the
    // grant is usually still fine and only this particular token is dead, so wiping the linked
    // email would strand the user on a 'connect your Google account' prompt for no reason
    fun invalidateAccessToken() = clearAccessTokenOnly()

    fun getLinkedEmail(): String? = _linkedEmail.value

    fun saveLinkedEmail(email: String?) {
        prefs.edit {
            if (email.isNullOrBlank()) remove(KEY_LINKED_EMAIL)
            else putString(KEY_LINKED_EMAIL, email)
        }
        _linkedEmail.value = email?.takeIf { it.isNotBlank() }
    }

    // the access token expires hourly but the user's consent doesn't. having a linked email means
    // we can attempt a silent re-authorization and mint a fresh token without UI
    fun isLinked(): Boolean = _linkedEmail.value != null

    // nuke everything, for an explicit Disconnect or an account revoke. Supabase sign-out uses
    // clearForSupabaseSignOut instead, so the link can be re-hydrated on the next sign-in
    fun clear() {
        prefs.edit { clear() }
        _linkedEmail.value = null
    }

    // on Supabase sign-out we don't want to revoke the Google grant on this device: the same user,
    // or a different Supabase account, may sign in and re-pull the link from profiles. just drop
    // the short-lived access token, and SyncCoordinator resets the linked email once a session exists
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

        // Google access tokens live an hour, but authorize() hands back whatever Play services has
        // cached, often one minted much earlier with only minutes left. assuming a full hour meant we
        // kept using dead tokens and collecting 401s, so treat them as half-hour tokens and let the
        // silent refresh top us up. GMS still returns its cached token when it really is fresh
        const val ASSUMED_LIFETIME_SECONDS = 1800L
    }
}
