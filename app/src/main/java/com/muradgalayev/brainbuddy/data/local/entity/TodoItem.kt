package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity(tableName = "todo_items")
data class TodoItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val date: String = LocalDate.now().toString(),
    val startTime: String,
    val endTime: String,
    val priority: String = TodoPriority.MEDIUM.name,
    val attendees: Int = 0,
    val color: String = TodoColor.LIGHT_PINK.name,
    val category: String = "personal"
)

enum class TodoPriority {
    LOW, MEDIUM, HIGH
}

enum class TodoColor {
    LIGHT_PINK,
    LIGHT_YELLOW,
    BURGUNDY,
    LIGHT_BLUE
}

