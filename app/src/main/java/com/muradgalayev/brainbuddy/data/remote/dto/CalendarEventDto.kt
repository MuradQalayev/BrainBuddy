package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEventDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val description: String = "",
    @SerialName("start_time")
    val startTime: String,
    @SerialName("end_time")
    val endTime: String,
    val location: String = "",
    val color: String = "blue",
    val link: String = "",
    @SerialName("updated_at")
    val updatedAt: String? = null
)
