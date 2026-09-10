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
    val completed: Boolean = false,
    // who wrote this row, as opposed to userId, whose calendar it belongs to. they differ only when
    // a Together connection created the event for someone else. RLS keys the connection's whole
    // reach off this column, so it must always be set on insert
    @SerialName("created_by")
    val createdBy: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
