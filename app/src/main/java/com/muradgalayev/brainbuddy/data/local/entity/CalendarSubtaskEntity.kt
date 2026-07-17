package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "calendar_subtasks",
    indices = [Index("eventId"), Index("userId")]
)
data class CalendarSubtaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val eventId: String,
    val title: String,
    val durationMinutes: Int,
    val orderIndex: Int,
    val completed: Boolean = false,
    val kind: String = "FOCUS",
    val syncStatus: String = SyncStatus.PENDING_INSERT.name,
    val lastModifiedAt: Long = System.currentTimeMillis()
)