package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.CalendarSubtaskDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseCalendarSubtaskDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "calendar_subtasks"

    suspend fun getAll(userId: String): List<CalendarSubtaskDto> {
        return supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList()
    }

    suspend fun upsert(item: CalendarSubtaskDto) {
        supabaseClient.from(table).upsert(item)
    }

    suspend fun upsertAll(items: List<CalendarSubtaskDto>) {
        if (items.isEmpty()) return
        supabaseClient.from(table).upsert(items)
    }

    suspend fun delete(id: String) {
        supabaseClient.from(table).delete {
            filter { eq("id", id) }
        }
    }

    suspend fun deleteForEvent(eventId: String) {
        supabaseClient.from(table).delete {
            filter { eq("event_id", eventId) }
        }
    }
}