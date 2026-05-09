package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val description: String = "",
    // ISO-8601 local datetime strings, e.g. "2026-05-05T10:00:00"
    val startTime: String,
    val endTime: String,
    val location: String = "",
    val color: String = "blue",
    val link: String = "",
    val syncStatus: String = SyncStatus.PENDING_INSERT.name,
    val lastModifiedAt: Long = System.currentTimeMillis()
)
