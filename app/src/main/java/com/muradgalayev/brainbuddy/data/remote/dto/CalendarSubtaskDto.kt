package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarSubtaskDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("event_id")
    val eventId: String,
    val title: String,
    @SerialName("duration_minutes")
    val durationMinutes: Int,
    @SerialName("order_index")
    val orderIndex: Int,
    val completed: Boolean = false,
    val kind: String = "FOCUS",
    @SerialName("updated_at")
    val updatedAt: String? = null,
)