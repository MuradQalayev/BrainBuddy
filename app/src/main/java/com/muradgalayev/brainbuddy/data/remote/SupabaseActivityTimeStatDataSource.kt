package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.ActivityTimeStatDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseActivityTimeStatDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "activity_time_stats"

    suspend fun getAll(userId: String): List<ActivityTimeStatDto> =
        supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList()

    suspend fun upsert(item: ActivityTimeStatDto) {
        supabaseClient.from(table).upsert(item)
    }
}
