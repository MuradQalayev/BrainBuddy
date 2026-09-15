package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.CalendarEventDto
import com.muradgalayev.brainbuddy.data.remote.dto.ChallengeResponseDto
import com.muradgalayev.brainbuddy.data.remote.dto.ConnectionDto
import com.muradgalayev.brainbuddy.data.remote.dto.ContactMatchDto
import com.muradgalayev.brainbuddy.data.remote.dto.FreeBusySlotDto
import com.muradgalayev.brainbuddy.data.remote.dto.IncomingRequestDto
import com.muradgalayev.brainbuddy.data.remote.dto.InvitePreviewDto
import com.muradgalayev.brainbuddy.data.remote.dto.InviteTokenDto
import com.muradgalayev.brainbuddy.data.remote.dto.OutgoingRequestDto
import com.muradgalayev.brainbuddy.data.remote.dto.SharedWellnessDto
import com.muradgalayev.brainbuddy.data.remote.dto.TodoItemDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

// thin RPC surface for Myndora Together. every call maps to one function in the 20260808
// migration. reads and writes go through RPCs rather than plain table access because the rules
// are asymmetric: the addressee of a request must not be able to read the row they're
// answering, and permissions must not be grantable to strangers. putting those checks in
// SECURITY DEFINER functions keeps them out of reach of a hand-rolled REST call
@Singleton
class SupabaseTogetherDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun listConnections(): List<ConnectionDto> =
        supabase.postgrest.rpc("list_my_connections").decodeAs()

    // which of my connections are deactivated right now. scoped to my own connections server-side
    suspend fun listDeactivatedConnections(): List<String> =
        supabase.postgrest.rpc("list_deactivated_connections")
            .decodeAs<List<com.muradgalayev.brainbuddy.data.remote.dto.DeactivatedUserDto>>()
            .map { it.userId }

    suspend fun listIncomingRequests(): List<IncomingRequestDto> =
        supabase.postgrest.rpc("list_incoming_connection_requests").decodeAs()

    suspend fun listOutgoingRequests(): List<OutgoingRequestDto> =
        supabase.postgrest.rpc("list_outgoing_connection_requests").decodeAs()

    suspend fun matchContacts(phoneHashes: List<String>): List<ContactMatchDto> =
        supabase.postgrest.rpc(
            "match_myndora_contacts",
            buildJsonObject {
                put("p_phone_hashes", JsonArray(phoneHashes.map(::JsonPrimitive)))
            },
        ).decodeAs()

    suspend fun respondToRequest(requestId: String, answer: String): ChallengeResponseDto =
        supabase.postgrest.rpc(
            "respond_to_connection_request",
            buildJsonObject {
                put("p_request_id", requestId)
                put("p_answer", answer)
            },
        ).decodeAs()

    suspend fun declineRequest(requestId: String) {
        supabase.postgrest.rpc(
            "decline_connection_request",
            buildJsonObject { put("p_request_id", requestId) },
        )
    }

    // withdraws a request I sent. an RPC rather than a plain DELETE: PostgREST reports success
    // when RLS filters every row out, which made a failed withdraw look like it worked while the
    // request stayed on screen
    suspend fun cancelOutgoingRequest(requestId: String) {
        supabase.postgrest.rpc(
            "cancel_connection_request",
            buildJsonObject { put("p_request_id", requestId) },
        )
    }

    // invite links

    // mints a single-use invite. the raw token is returned exactly once, to its creator, and is
    // never selectable from the table afterwards
    suspend fun createInvite(relation: String, validHours: Int): InviteTokenDto =
        supabase.postgrest.rpc(
            "create_connection_invite",
            buildJsonObject {
                put("p_relation", relation)
                put("p_hours", validHours)
            },
        ).decodeList<InviteTokenDto>().first()

    // who's inviting, without consuming the invite, for the confirmation sheet
    suspend fun peekInvite(token: String): InvitePreviewDto? =
        supabase.postgrest.rpc(
            "peek_connection_invite",
            buildJsonObject { put("p_token", token) },
        ).decodeList<InvitePreviewDto>().firstOrNull()

    suspend fun redeemInvite(token: String) {
        supabase.postgrest.rpc(
            "redeem_connection_invite",
            buildJsonObject { put("p_token", token) },
        )
    }

    suspend fun revokeInvites() {
        supabase.postgrest.rpc("revoke_connection_invites")
    }

    suspend fun setPermission(granteeId: String, scope: String, granted: Boolean) {
        supabase.postgrest.rpc(
            "set_connection_permission",
            buildJsonObject {
                put("p_grantee", granteeId)
                put("p_scope", scope)
                put("p_granted", granted)
            },
        )
    }

    suspend fun removeConnection(otherUserId: String) {
        supabase.postgrest.rpc(
            "remove_connection",
            buildJsonObject { put("p_other", otherUserId) },
        )
    }

    // events I put on someone else's calendar. RLS already narrows this to created_by = me, so
    // there's no way to widen it into their whole calendar
    suspend fun listEventsICreatedFor(ownerId: String): List<CalendarEventDto> =
        supabase.postgrest.rpc(
            "list_events_i_created_for",
            buildJsonObject { put("p_owner", ownerId) },
        ).decodeAs()

    suspend fun listTodosICreatedFor(ownerId: String): List<TodoItemDto> =
        supabase.postgrest.rpc(
            "list_todos_i_created_for",
            buildJsonObject { put("p_owner", ownerId) },
        ).decodeAs()

    // writes straight into the owner's table, and the INSERT policy vets the grant
    suspend fun insertEventForOther(event: CalendarEventDto) {
        supabase.from("calendar_events").insert(event)
    }

    suspend fun insertTodoForOther(todo: TodoItemDto) {
        supabase.from("todo_items").insert(todo)
    }

    // returns whose calendar it was, so the owner can be told to sync. null when nothing was deleted
    suspend fun deleteEventIAuthored(eventId: String): String? =
        supabase.from("calendar_events").delete {
            select(Columns.list("user_id"))
            filter { eq("id", eventId) }
        }.decodeList<OwnerRow>().firstOrNull()?.userId

    suspend fun deleteTodoIAuthored(todoId: String): String? =
        supabase.from("todo_items").delete {
            select(Columns.list("user_id"))
            filter { eq("id", todoId) }
        }.decodeList<OwnerRow>().firstOrNull()?.userId

    // server checks ownerId is one of my connections before pushing, see notify-together-change
    suspend fun notifyTogetherChange(ownerId: String, kind: String) {
        supabase.functions.invoke(
            function = "notify-together-change",
            body = buildJsonObject {
                put("ownerId", ownerId)
                put("kind", kind)
            },
        )
    }

    @Serializable
    private data class OwnerRow(@SerialName("user_id") val userId: String)

    // when a connection is already busy, between two dates. the RPC raises when AVAILABILITY isn't
    // granted rather than returning an empty list, and that distinction is load-bearing: 'no
    // blocks' and 'no permission' must not look alike to a caller whose next move is to treat
    // every hour as free
    suspend fun freeBusy(ownerId: String, from: String, to: String): List<FreeBusySlotDto> =
        supabase.postgrest.rpc(
            "myndora_free_busy",
            buildJsonObject {
                put("p_owner", ownerId)
                put("p_from", from)
                put("p_to", to)
            },
        ).decodeAs()

    // the wellness snapshot a connection shared with me. null when the WELLNESS grant is off: the
    // RLS policy stops the row being selectable at all, so there's nothing to filter client-side
    suspend fun getSharedWellness(ownerId: String): SharedWellnessDto? =
        supabase.from("profile_shares")
            .select {
                filter { eq("patient_id", ownerId) }
                limit(1)
            }
            .decodeList<SharedWellnessDto>()
            .firstOrNull()
}
