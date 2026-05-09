package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.PomodoroSessionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabasePomodoroSessionDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "pomodoro_sessions"

    suspend fun getAll(userId: String): List<PomodoroSessionDto> {
        return supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList()
    }

    suspend fun upsert(item: PomodoroSessionDto) {
        supabaseClient.from(table).upsert(item)
    }

    suspend fun delete(id: String) {
        supabaseClient.from(table).delete {
            filter { eq("id", id) }
        }
    }
}
