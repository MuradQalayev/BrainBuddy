// domain/model/PomodoroSession.kt
package com.muradgalayev.brainbuddy.domain.model

data class PomodoroSession(
    val id: String,
    val sessionType: String,
    val plannedDurationMs: Long,
    val actualDurationMs: Long,
    val pausedDurationMs: Long,
    val extraTimeAddedMs: Long,
    val startTime: Long,
    val endTime: Long,
    val completionStatus: String,
    val resetCount: Int,
    val wasInterrupted: Boolean,
    val focusModeEnabled: Boolean,
    val focusModePermissionGranted: Boolean,
    val focusModeActivated: Boolean,
    val focusModeOnTimestamp: Long,
    val focusModeOffTimestamp: Long,
    val focusModeRestoredSuccessfully: Boolean
)