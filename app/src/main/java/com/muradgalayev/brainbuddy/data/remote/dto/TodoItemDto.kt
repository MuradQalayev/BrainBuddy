package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TodoItemDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val description: String = "",
    @SerialName("is_completed")
    val isCompleted: Boolean = false,
    val date: String,
    @SerialName("start_time")
    val startTime: String,
    @SerialName("end_time")
    val endTime: String,
    val priority: String = "MEDIUM",
    val attendees: Int = 0,
    val color: String = "LIGHT_PINK",
    val category: String = "personal",
    // see CalendarEventDto.createdBy, same owner/author split
    @SerialName("created_by")
    val createdBy: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
