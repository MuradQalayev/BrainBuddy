package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.AiMessageDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
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

    /**
     * Recent messages across ALL of the user's conversations. We fetch a bounded
     * window then group client-side into per-conversation summaries. Small
     * histories keep this cheap; if a user's log grows huge we can move the
     * summarization into an RPC later.
     */
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
}
