package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PomodoroSessionDto(
    val id: String,

    @SerialName("user_id")
    val userId: String,

    @SerialName("session_type")
    val sessionType: String,

    @SerialName("planned_duration_ms")
    val plannedDurationMs: Long,

    @SerialName("actual_duration_ms")
    val actualDurationMs: Long,

    @SerialName("paused_duration_ms")
    val pausedDurationMs: Long = 0L,

    @SerialName("extra_time_added_ms")
    val extraTimeAddedMs: Long = 0L,

    @SerialName("start_time")
    val startTime: Long,

    @SerialName("end_time")
    val endTime: Long,

    @SerialName("completion_status")
    val completionStatus: String,

    @SerialName("reset_count")
    val resetCount: Int = 0,

    @SerialName("was_interrupted")
    val wasInterrupted: Boolean = false,

    @SerialName("focus_mode_enabled")
    val focusModeEnabled: Boolean = false
)
