package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.TodoItemDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTodoDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "todo_items"

    // Fetch only items that belong to the specified userId to avoid mixing data
    // between different Supabase users when syncing.
    suspend fun getAll(userId: String): List<TodoItemDto> {
        // use the actual DB column name (user_id) used by the DTO (@SerialName)
        return supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList()
    }

    suspend fun upsert(item: TodoItemDto) {
        supabaseClient.from(table)
            .upsert(item)
    }

    suspend fun upsertAll(items: List<TodoItemDto>) {
        if (items.isEmpty()) return
        supabaseClient.from(table)
            .upsert(items)
    }

    suspend fun delete(id: String) {
        supabaseClient.from(table)
            .delete {
                filter { eq("id", id) }
            }
    }
}
