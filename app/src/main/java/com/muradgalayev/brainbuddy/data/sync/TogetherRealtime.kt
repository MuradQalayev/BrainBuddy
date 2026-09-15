package com.muradgalayev.brainbuddy.data.sync

import android.util.Log
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

// live changes to this user's own calendar and list while the app is on screen, so what a connection
// adds or removes shows up at once. the push covers a closed app; a data message to a dozing phone
// can't be trusted to land in seconds, a socket held while the app is open can
@Singleton
class TogetherRealtime @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val calendarRepository: CalendarRepository,
    private val todoRepository: TodoRepository,
    private val syncCoordinator: SyncCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var channel: RealtimeChannel? = null
    private var connectedUserId: String? = null

    @Volatile
    private var foreground = false

    init {
        // sign-in and sign-out happen without leaving the app, so follow the session as well as the screen
        scope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (!loggedIn) disconnect() else if (foreground) connect()
            }
        }
    }

    fun onForeground() {
        foreground = true
        connect()
    }

    // an open socket in the background is battery the push already makes unnecessary
    fun onBackground() {
        foreground = false
        disconnect()
    }

    @Synchronized
    private fun connect() {
        val userId = authRepository.getCurrentUserId() ?: return
        if (job?.isActive == true && connectedUserId == userId) return
        disconnect()
        connectedUserId = userId
        job = scope.launch {
            val ch = supabase.channel("owner-changes-$userId")
            channel = ch
            // flows have to exist before subscribe, or the server never hears about them
            val events = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "calendar_events"
                filter("user_id", FilterOperator.EQ, userId)
            }
            val todos = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "todo_items"
                filter("user_id", FilterOperator.EQ, userId)
            }
            launch { events.collect { onChange(it, userId, KIND_CALENDAR) } }
            launch { todos.collect { onChange(it, userId, KIND_TODO) } }
            runCatching { ch.subscribe() }
                .onFailure { Log.w(TAG, "Realtime subscribe failed: ${it.message}") }
        }
    }

    @Synchronized
    private fun disconnect() {
        job?.cancel()
        job = null
        connectedUserId = null
        val ch = channel ?: return
        channel = null
        scope.launch { runCatching { supabase.realtime.removeChannel(ch) } }
    }

    private suspend fun onChange(action: PostgresAction, userId: String, kind: String) {
        when (action) {
            // a delete only carries the id, so it's applied whoever did it. removing a row this phone
            // already removed is a no-op
            is PostgresAction.Delete -> {
                val id = action.oldRecord["id"]?.jsonPrimitive?.contentOrNull ?: return
                if (kind == KIND_TODO) todoRepository.applyRemoteDelete(id)
                else calendarRepository.applyRemoteDelete(id)
            }
            is PostgresAction.Insert ->
                if (writtenByOther(action.record, userId)) syncCoordinator.syncAfterTogetherChange(kind)
            is PostgresAction.Update ->
                if (writtenByOther(action.record, userId)) syncCoordinator.syncAfterTogetherChange(kind)
            else -> Unit
        }
    }

    // this phone's own writes echo back too, and those are already in Room
    private fun writtenByOther(record: JsonObject, userId: String): Boolean {
        val author = record["created_by"]?.jsonPrimitive?.contentOrNull
        return author != null && author != userId
    }

    private companion object {
        const val TAG = "TogetherRealtime"
        // must match what SyncCoordinator.syncAfterTogetherChange expects
        const val KIND_CALENDAR = "calendar"
        const val KIND_TODO = "todo"
    }
}
