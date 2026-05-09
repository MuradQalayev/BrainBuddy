package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.CalendarEventDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseCalendarEventDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "calendar_events"

    suspend fun getAll(userId: String): List<CalendarEventDto> {
        return supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList()
    }

    suspend fun upsert(item: CalendarEventDto) {
        supabaseClient.from(table).upsert(item)
    }

    suspend fun delete(id: String) {
        supabaseClient.from(table).delete {
            filter { eq("id", id) }
        }
    }
}
