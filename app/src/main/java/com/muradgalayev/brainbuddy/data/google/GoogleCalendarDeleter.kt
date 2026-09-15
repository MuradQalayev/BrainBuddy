package com.muradgalayev.brainbuddy.data.google

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

// Google Calendar event ids must match [a-v0-9]{5,1024}. a UUID without hyphens is hex, so
// it's valid and stable per event. export and delete both go through this so the ids always match
internal fun googleCalendarEventId(eventId: String): String =
    eventId.lowercase().replace("-", "").take(1024).padEnd(5, '0')

// mirrors in-app deletes onto the linked Google Calendar. we only ever export, never import, so
// an id built from one of our events can only point at an event Myndora put there
@Singleton
class GoogleCalendarDeleter @Inject constructor(
    @ApplicationContext context: Context,
    private val tokenStore: GoogleCalendarTokenStore,
    private val authClient: GoogleCalendarAuthClient,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    // queued before the call, so no signal or a killed process still leaves it for the next sync
    fun deleteLater(eventId: String) {
        if (!tokenStore.isLinked()) return
        addPending(eventId)
        scope.launch { flushPending() }
    }

    // called at the start of every export too, which is how failed deletes get retried
    suspend fun flushPending() = withContext(Dispatchers.IO) {
        lock.withLock {
            val pending = pendingIds()
            if (pending.isEmpty() || !tokenStore.isLinked()) return@withLock
            var token = tokenStore.getAccessToken()
                ?: authClient.tryGetFreshAccessTokenSilently()
                ?: return@withLock
            var refreshed = false
            for (id in pending) {
                var result = delete(id, token)
                if (result == DeleteResult.Unauthorized && !refreshed) {
                    refreshed = true
                    token = authClient.tryGetFreshAccessTokenSilently(forceRefresh = true) ?: return@withLock
                    result = delete(id, token)
                }
                when (result) {
                    DeleteResult.Gone -> removePending(id)
                    // the grant is gone, the export that follows will surface the reconnect prompt
                    DeleteResult.Unauthorized -> return@withLock
                    DeleteResult.Failed -> Unit
                }
            }
        }
    }

    private fun delete(eventId: String, accessToken: String): DeleteResult = try {
        val googleId = googleCalendarEventId(eventId)
        val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events/$googleId?sendUpdates=none")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val code = conn.responseCode
        conn.disconnect()
        when {
            // 404 never exported, 410 already deleted on Google's side. either way nothing is left to do
            code in 200..299 || code == 404 || code == 410 -> DeleteResult.Gone
            code == 401 -> DeleteResult.Unauthorized
            else -> {
                Log.w(TAG, "Delete failed for event id=$eventId: HTTP $code")
                DeleteResult.Failed
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Delete threw for event id=$eventId: ${e.message}")
        DeleteResult.Failed
    }

    private fun pendingIds(): Set<String> = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().toSet()

    @Synchronized
    private fun addPending(eventId: String) {
        prefs.edit { putStringSet(KEY_PENDING, pendingIds() + eventId) }
    }

    @Synchronized
    private fun removePending(eventId: String) {
        prefs.edit { putStringSet(KEY_PENDING, pendingIds() - eventId) }
    }

    private enum class DeleteResult { Gone, Unauthorized, Failed }

    private companion object {
        const val TAG = "GCalDelete"
        const val PREFS_NAME = "google_calendar_deletes"
        const val KEY_PENDING = "pending_event_ids"
    }
}
