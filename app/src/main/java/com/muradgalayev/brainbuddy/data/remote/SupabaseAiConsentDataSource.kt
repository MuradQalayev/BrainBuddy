package com.muradgalayev.brainbuddy.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAiConsentDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "ai_consents"

    suspend fun getAll(userId: String): List<AiConsentDto> =
        supabaseClient.from(table)
            .select { filter { eq("user_id", userId) } }
            .decodeList()

    // onConflict names the unique constraint to merge on. without it the DTO carries no primary
    // key, nothing conflicts, and each save inserts another row, after which a read picks one
    // arbitrarily and consent becomes whichever duplicate came back first
    suspend fun upsert(dto: AiConsentDto) {
        supabaseClient.from(table).upsert(dto) {
            onConflict = "user_id,scope"
        }
    }
}
