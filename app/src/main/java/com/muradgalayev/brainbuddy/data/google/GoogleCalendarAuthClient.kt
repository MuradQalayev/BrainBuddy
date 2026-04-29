package com.muradgalayev.brainbuddy.data.google

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
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
    private val tokenStore: GoogleCalendarTokenStore
) {

    sealed class AuthorizationStep {
        data class AccessToken(val token: String) : AuthorizationStep()
        data class NeedsUserConsent(val pendingIntent: PendingIntent) : AuthorizationStep()
    }

    suspend fun requestAuthorization(): AuthorizationStep {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_CALENDAR_EVENTS),
                    Scope(SCOPE_USERINFO_EMAIL)
                )
            )
            .build()

        val result = awaitAuthorization(request)
        return interpret(result)
    }

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

    // GMS keeps its own cache of granted tokens. Even after server-side revoke,
    // the next authorize() call would silently reuse the cached token and skip
    // the picker. clearToken removes the entry so authorize() must re-prompt.
    private suspend fun clearGmsLocalCache(token: String) = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
    }

    // Hits https://oauth2.googleapis.com/revoke so Google forgets the consent
    // for this app+account. Without this, the next authorize() call would
    // silently return a token for the same account and skip the picker.
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
        runCatching {
            val url = URL("https://www.googleapis.com/oauth2/v2/userinfo")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val email = Json.parseToJsonElement(body).jsonObject["email"]
                    ?.jsonPrimitive?.content
                if (!email.isNullOrBlank()) {
                    tokenStore.saveLinkedEmail(email)
                    email
                } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
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
        return token
    }

    private suspend fun awaitAuthorization(request: AuthorizationRequest): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private companion object {
        const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"
        const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    }
}
