package com.muradgalayev.brainbuddy.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabasePreferencesDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "user_preferences"

    suspend fun get(userId: String): PreferencesDto? {
        val list = supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList<PreferencesDto>()
        return list.firstOrNull()
    }

    suspend fun upsert(dto: PreferencesDto) {
        supabaseClient.from(table)
            .upsert(dto)
    }
}

