package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.AiMessageDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAiMessageDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "ai_messages"

    suspend fun getRecent(userId: String, limit: Long = 50): List<AiMessageDto> {
        return supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.ASCENDING)
                limit(limit)
            }
            .decodeList()
    }

    // the newest messages of a conversation, oldest-first. distinct from getForConversation, which
    // returns the oldest ones. ordering descending and reversing is what makes the difference: past
    // the limit, ascending-then-limit silently returns the start of the chat and drops everything
    // recent, so re-opening a long conversation would show it frozen at some point in the past
    suspend fun getRecentForConversation(
        userId: String,
        conversationId: String,
        limit: Long = 200,
    ): List<AiMessageDto> {
        return supabaseClient.from(table)
            .select {
                filter {
                    eq("user_id", userId)
                    eq("conversation_id", conversationId)
                }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList<AiMessageDto>()
            .reversed()
    }

    // the oldest messages of a conversation. used for titling, which reads the opening exchange
    suspend fun getForConversation(
        userId: String,
        conversationId: String,
        limit: Long = 200,
    ): List<AiMessageDto> {
        return supabaseClient.from(table)
            .select {
                filter {
                    eq("user_id", userId)
                    eq("conversation_id", conversationId)
                }
                order("created_at", Order.ASCENDING)
                limit(limit)
            }
            .decodeList()
    }

    // recent messages across all of the user's conversations. we fetch a bounded window then group
    // client-side into per-conversation summaries. small histories keep this cheap, and if a log
    // grows huge the summarisation can move into an RPC later
    suspend fun getAllForSummaries(userId: String, limit: Long = 500): List<AiMessageDto> {
        return supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()
    }

    suspend fun insert(message: AiMessageDto) {
        supabaseClient.from(table).insert(message)
    }

    suspend fun deleteAllForUser(userId: String) {
        supabaseClient.from(table).delete {
            filter { eq("user_id", userId) }
        }
    }

    suspend fun deleteConversation(userId: String, conversationId: String) {
        supabaseClient.from(table).delete {
            filter {
                eq("user_id", userId)
                eq("conversation_id", conversationId)
            }
        }
    }

    // stamp the generated title onto every row of a conversation
    suspend fun updateConversationTitle(userId: String, conversationId: String, title: String) {
        supabaseClient.from(table).update(TitlePatch(title)) {
            filter {
                eq("user_id", userId)
                eq("conversation_id", conversationId)
            }
        }
    }

    @Serializable
    private data class TitlePatch(val title: String)
}
