package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

enum class SyncStatus {
    SYNCED,
    PENDING_INSERT,
    PENDING_UPDATE,
    PENDING_DELETE
}

@Entity(tableName = "todo_items")
data class TodoItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val date: String = LocalDate.now().toString(),
    val startTime: String,
    val endTime: String,
    val priority: String = TodoPriority.MEDIUM.name,
    val attendees: Int = 0,
    val color: String = TodoColor.LIGHT_PINK.name,
    val category: String = "personal",
    // see CalendarEventEntity.createdBy, same owner/author split
    val createdBy: String? = null,
    val syncStatus: String = SyncStatus.PENDING_INSERT.name,
    val lastModifiedAt: Long = System.currentTimeMillis()
)
enum class TodoPriority {
    LOW, MEDIUM, HIGH
}

enum class TodoColor {
    LIGHT_PINK,
}

