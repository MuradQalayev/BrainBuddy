package com.muradgalayev.brainbuddy.data.google

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class GoogleCalendarAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: GoogleCalendarTokenStore,
    private val authRepository: AuthRepository,
) {

    sealed class AuthorizationStep {
        data class AccessToken(val token: String) : AuthorizationStep()
        data class NeedsUserConsent(val pendingIntent: PendingIntent) : AuthorizationStep()
    }

    suspend fun requestAuthorization(): AuthorizationStep {
        val result = awaitAuthorization()
        return interpret(result)
    }

    // mints a fresh access token without showing UI. returns the token if the user has already
    // granted consent for this account and scope (just a refresh), or null when interactive
    // consent would be required, e.g. they revoked access or signed out of Google on the device.
    // safe to call from background contexts like a WorkManager job.
    // pass forceRefresh after Google has rejected a token: authorize() serves from the Play
    // services cache, so without evicting the dead entry it hands back the token that just 401'd
    suspend fun tryGetFreshAccessTokenSilently(forceRefresh: Boolean = false): String? {
        if (forceRefresh) invalidateCurrentToken()
        return runCatching {
            val result = awaitAuthorization()
            if (result.pendingIntent != null) null
            else interpretAndPersistToken(result)
        }.getOrNull()
    }

    // forgets the current access token locally and in the GMS cache, while keeping the account
    // link intact. the link is what lets us re-authorize silently, so it has to survive a
    // merely-expired token
    suspend fun invalidateCurrentToken() {
        val stale = tokenStore.peekRawAccessToken()
        tokenStore.invalidateAccessToken()
        if (!stale.isNullOrBlank()) clearGmsLocalCache(stale)
    }

    // accountHint pins the request to the account the user already linked. without it authorize()
    // falls back to whatever GMS considers the default account, and on a device with several
    // Google accounts (or none marked default, which is the norm here since sign-in goes through
    // Supabase rather than Credential Manager) it answers with an account-picker PendingIntent
    // instead of a token. that is the 'please sign in again' prompt
    private fun buildRequest(accountHint: String?): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_CALENDAR_EVENTS),
                    Scope(SCOPE_USERINFO_EMAIL)
                )
            )
            .apply {
                if (!accountHint.isNullOrBlank()) {
                    setAccount(Account(accountHint, GOOGLE_ACCOUNT_TYPE))
                }
            }
            .build()

    fun extractFromActivityResult(data: Intent?): String? {
        if (data == null) return null
        val result = Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(data)
        return interpretAndPersistToken(result)
    }

    suspend fun signOut() {
        val token = tokenStore.peekRawAccessToken()
        tokenStore.clear()
        if (!token.isNullOrBlank()) {
            revokeToken(token)
            clearGmsLocalCache(token)
        }
    }

    // GMS keeps its own cache of granted tokens. even after a server-side revoke the next
    // authorize() would silently reuse the cached token and skip the picker, so clearToken removes
    // the entry and forces a re-prompt
    private suspend fun clearGmsLocalCache(token: String) = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
    }

    // hits Google's revoke endpoint so it forgets the consent for this app and account. without
    // this, the next authorize() would silently return a token for the same account and skip the
    // picker
    private suspend fun revokeToken(token: String) = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://oauth2.googleapis.com/revoke?token=$token")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                )
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                conn.outputStream.use { it.write(ByteArray(0)) }
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun fetchAndStoreUserEmail(token: String): String? = withContext(Dispatchers.IO) {
        val email = runCatching {
            val url = URL("https://www.googleapis.com/oauth2/v2/userinfo")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Json.parseToJsonElement(body).jsonObject["email"]?.jsonPrimitive?.content
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        if (email != null) {
            tokenStore.saveLinkedEmail(email)
            // persist to Supabase so the link survives sign-out. failure here is non-fatal, the local link
            // still works and SyncCoordinator reconciles on the next connect
            runCatching { authRepository.setLinkedGoogleEmail(email) }
        }
        email
    }

    private fun interpret(result: AuthorizationResult): AuthorizationStep {
        val pendingIntent = result.pendingIntent
        return if (pendingIntent != null) {
            AuthorizationStep.NeedsUserConsent(pendingIntent)
        } else {
            val token = interpretAndPersistToken(result)
                ?: error("Authorization succeeded but no access token was returned")
            AuthorizationStep.AccessToken(token)
        }
    }

    private fun interpretAndPersistToken(result: AuthorizationResult): String? {
        val token = result.accessToken ?: return null
        tokenStore.saveAccessToken(token)
        // the result already carries the account, so record it even on a silent refresh. that keeps
        // the account hint alive without the extra userinfo round trip, and re-links a device whose
        // prefs were cleared
        result.toGoogleSignInAccount()?.email?.takeIf { it.isNotBlank() }?.let {
            if (it != tokenStore.getLinkedEmail()) tokenStore.saveLinkedEmail(it)
        }
        return token
    }

    private suspend fun awaitAuthorization(): AuthorizationResult {
        val hint = tokenStore.getLinkedEmail()
        if (hint == null) return authorize(buildRequest(null))
        // a pinned account that is no longer on the device makes authorize() fail outright, so retry
        // unpinned and the user still gets a picker rather than an error
        return runCatching { authorize(buildRequest(hint)) }
            .getOrElse { authorize(buildRequest(null)) }
    }

    private suspend fun authorize(request: AuthorizationRequest): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private companion object {
        const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"
        const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
