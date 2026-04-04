package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class PomodoroSessionType { FOCUS, SHORT_BREAK, LONG_BREAK }
enum class PomodoroCompletionStatus { COMPLETED, CANCELLED, RESET, IN_PROGRESS }

@Entity(tableName = "pomodoro_sessions")
data class PomodoroSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionType: String = PomodoroSessionType.FOCUS.name,
    val plannedDurationMs: Long = 25 * 60 * 1000L,
    val actualDurationMs: Long = 0L,
    val pausedDurationMs: Long = 0L,
    val extraTimeAddedMs: Long = 0L,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val completionStatus: String = PomodoroCompletionStatus.IN_PROGRESS.name,
    val resetCount: Int = 0,
    val wasInterrupted: Boolean = false,
    val focusModeEnabled: Boolean = false,
    val focusModePermissionGranted: Boolean = false,
    val focusModeActivated: Boolean = false,
    val focusModeOnTimestamp: Long = 0L,
    val focusModeOffTimestamp: Long = 0L,
    val focusModeRestoredSuccessfully: Boolean = false
)

