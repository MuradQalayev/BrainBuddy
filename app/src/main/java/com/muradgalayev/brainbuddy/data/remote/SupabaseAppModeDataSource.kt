package com.muradgalayev.brainbuddy.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAppModeDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "app_modes"

    suspend fun getAll(userId: String): List<AppModeDto> =
        supabaseClient.from(table)
            .select { filter { eq("user_id", userId) } }
            .decodeList()

    // stable mode ids are unique only inside an account, so ownership is part of the conflict target
    suspend fun upsert(dto: AppModeDto) {
        supabaseClient.from(table).upsert(dto) { onConflict = "user_id,id" }
    }

    suspend fun delete(id: String, userId: String) {
        supabaseClient.from(table).delete {
            filter {
                eq("user_id", userId)
                eq("id", id)
            }
        }
    }
}
