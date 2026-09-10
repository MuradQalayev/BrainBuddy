package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// where a shared session is in its life. mirrors focus_session_status
enum class FocusSessionStatus { PENDING, ACTIVE, ENDED, CANCELLED, UNKNOWN }

// this user's own answer to an invite. mirrors focus_participant_state
enum class FocusParticipantState { INVITED, JOINED, DECLINED, LEFT, UNKNOWN }

// which half of the cycle the room is in. mirrors focus_phase
enum class FocusPhase { FOCUS, BREAK }

// a session this user is part of, either an invite waiting for an answer or one that's
// running. deliberately carries no task, event or title field, and there is nowhere in the
// schema for one either, so that's structural rather than a promise
data class FocusSession(
    val id: String,
    val hostId: String,
    val hostName: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val phase: FocusPhase,
    val status: FocusSessionStatus,
    val myState: FocusParticipantState,
    val endsAt: Instant?,
    val expiresAt: Instant?,
    val joinedCount: Int,
    val pendingCount: Int,
    val participantCount: Int,
    // seconds left, as computed by Postgres. not derived from endsAt and the device clock: phone
    // clocks disagree, and two devices deriving locally produced two different countdowns for the
    // same session
    val secondsRemaining: Int,
    val secondsUntilExpiry: Int,
) {
    // waiting for my answer
    val isInvite: Boolean
        get() = status == FocusSessionStatus.PENDING && myState == FocusParticipantState.INVITED

    // I've said yes and we're waiting on someone else. distinct from isInvite: the user has
    // already acted and needs to be told the ball is elsewhere, not shown the same button again
    val isWaitingForOthers: Boolean
        get() = status == FocusSessionStatus.PENDING &&
            myState == FocusParticipantState.JOINED

    val isRunning: Boolean
        get() = status == FocusSessionStatus.ACTIVE && myState == FocusParticipantState.JOINED

}

// one person in the room, and whether they've tapped ready
data class FocusRoomMember(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val state: FocusParticipantState,
    val isHost: Boolean,
) {
    val isReady: Boolean get() = state == FocusParticipantState.JOINED
}

@Serializable
private data class FocusSessionRow(
    @SerialName("session_id") val sessionId: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("host_name") val hostName: String? = null,
    @SerialName("focus_minutes") val focusMinutes: Int,
    @SerialName("break_minutes") val breakMinutes: Int = 5,
    val phase: String = "FOCUS",
    val status: String,
    @SerialName("my_state") val myState: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("joined_count") val joinedCount: Int = 0,
    @SerialName("pending_count") val pendingCount: Int = 0,
    @SerialName("participant_count") val participantCount: Int = 0,
    @SerialName("seconds_remaining") val secondsRemaining: Int = 0,
    @SerialName("seconds_until_expiry") val secondsUntilExpiry: Int = 0,
)

@Serializable
private data class FocusRoomRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val state: String,
    @SerialName("is_host") val isHost: Boolean = false,
)

// focus-together sessions: invite a connection, work at the same time, see that they're there.
// every write goes through an RPC rather than a table. inviting requires the other person to
// be a connection and to have granted FOCUS, and a check like that is worth nothing on the
// device of the person it constrains, so create_focus_session does it in Postgres and this
// class only ever asks.
// reads fail soft and return empty. a body-doubling invite that doesn't arrive is a missed
// opportunity, an error dialog over someone's focus screen is an interruption
@Singleton
class FocusTogetherRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    // pending invites and live sessions, newest first
    suspend fun listSessions(): List<FocusSession> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest
                .rpc("list_my_focus_sessions")
                .decodeAs<List<FocusSessionRow>>()
                .map { it.toDomain() }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't list focus sessions: ${e.message}")
            emptyList()
        }
    }

    // opens a session and invites inviteeIds. anyone who hasn't granted FOCUS is dropped by the
    // RPC without complaint, so a returned session id does not mean everyone was invited: the
    // joined count on the next poll is the only honest answer to 'did they get it?'
    suspend fun createSession(
        inviteeIds: List<String>,
        focusMinutes: Int,
        breakMinutes: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.postgrest.rpc(
                "create_focus_session",
                buildJsonObject {
                    put("p_invitee_ids", kotlinx.serialization.json.JsonArray(
                        inviteeIds.map { JsonPrimitive(it) }
                    ))
                    put("p_focus_minutes", focusMinutes)
                    put("p_break_minutes", breakMinutes)
                },
            ).decodeAs<String>()
        }.onSuccess { sessionId ->
            // best-effort, and deliberately not part of the result. the session exists either way and the
            // recipient's own polling still finds it, so push makes it fast rather than making it work.
            // failing the invite because a notification didn't send would trade a working feature for a
            // broken one
            runCatching {
                supabase.functions.invoke(
                    function = "send-focus-invite",
                    body = buildJsonObject { put("sessionId", sessionId) },
                )
            }.onFailure { Log.w(TAG, "Push for $sessionId not sent: ${it.message}") }
        }.onFailure { Log.w(TAG, "Couldn't create focus session: ${it.message}") }
    }

    suspend fun join(sessionId: String): Result<Unit> = respond(sessionId, "JOIN")

    suspend fun decline(sessionId: String): Result<Unit> = respond(sessionId, "DECLINE")

    // leaves without ending it for anyone else
    suspend fun leave(sessionId: String): Result<Unit> = respond(sessionId, "LEAVE")

    private suspend fun respond(sessionId: String, answer: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.postgrest.rpc(
                    "respond_to_focus_invite",
                    buildJsonObject {
                        put("p_session_id", sessionId)
                        put("p_answer", answer)
                    },
                )
                Unit
            }.onFailure { Log.w(TAG, "Focus invite $answer failed: ${it.message}") }
        }

    // moves a finished phase along, focus into break and break into ended. called by whichever
    // device notices the clock ran out. the server ignores it if the time isn't actually up, so
    // every participant calling at once is harmless and no client has to be elected to do it
    suspend fun advancePhase(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.postgrest.rpc(
                "advance_focus_phase",
                buildJsonObject { put("p_session_id", sessionId) },
            )
            Unit
        }.onFailure { Log.w(TAG, "Couldn't advance $sessionId: ${it.message}") }
    }

    // host only, ends it for everyone
    suspend fun endSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.postgrest.rpc(
                "end_focus_session",
                buildJsonObject { put("p_session_id", sessionId) },
            )
            Unit
        }.onFailure { Log.w(TAG, "Couldn't end focus session: ${it.message}") }
    }

    private fun FocusSessionRow.toDomain() = FocusSession(
        id = sessionId,
        hostId = hostId,
        hostName = hostName?.takeIf { it.isNotBlank() } ?: "Someone",
        focusMinutes = focusMinutes,
        breakMinutes = breakMinutes,
        phase = runCatching { FocusPhase.valueOf(phase) }.getOrDefault(FocusPhase.FOCUS),
        status = runCatching { FocusSessionStatus.valueOf(status) }
            .getOrDefault(FocusSessionStatus.UNKNOWN),
        myState = runCatching { FocusParticipantState.valueOf(myState) }
            .getOrDefault(FocusParticipantState.UNKNOWN),
        endsAt = endsAt?.toInstantOrNull(),
        expiresAt = expiresAt?.toInstantOrNull(),
        joinedCount = joinedCount,
        pendingCount = pendingCount,
        participantCount = participantCount,
        secondsRemaining = secondsRemaining,
        secondsUntilExpiry = secondsUntilExpiry,
    )

    // who's in the room, host first. empty on failure, and the room shows a spinner
    suspend fun listRoom(sessionId: String): List<FocusRoomMember> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.postgrest.rpc(
                "list_focus_room",
                buildJsonObject { put("p_session_id", sessionId) },
            ).decodeAs<List<FocusRoomRow>>().map {
                FocusRoomMember(
                    userId = it.userId,
                    name = it.displayName?.takeIf { n -> n.isNotBlank() } ?: "Someone",
                    avatarUrl = it.avatarUrl,
                    state = runCatching { FocusParticipantState.valueOf(it.state) }
                        .getOrDefault(FocusParticipantState.UNKNOWN),
                    isHost = it.isHost,
                )
            }
        }.getOrElse {
            Log.w(TAG, "Couldn't read room $sessionId: ${it.message}")
            emptyList()
        }
    }

    // Postgres hands back a timestamp Instant.parse won't take: a space instead of the T, and an
    // offset without minutes
    private fun String.toInstantOrNull(): Instant? = runCatching {
        Instant.parse(replace(' ', 'T').let { if (it.endsWith("+00")) it.dropLast(3) + "Z" else it })
    }.getOrNull()

    private companion object {
        const val TAG = "FocusTogetherRepo"
    }
}
