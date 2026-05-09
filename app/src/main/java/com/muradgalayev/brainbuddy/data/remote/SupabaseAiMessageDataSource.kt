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

    suspend fun insert(message: AiMessageDto) {
        supabaseClient.from(table).insert(message)
    }

    suspend fun deleteAllForUser(userId: String) {
        supabaseClient.from(table).delete {
            filter { eq("user_id", userId) }
        }
    }
}
