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

    suspend fun getAll(): List<TodoItemDto> {
        return supabaseClient.from(table)
            .select()
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
