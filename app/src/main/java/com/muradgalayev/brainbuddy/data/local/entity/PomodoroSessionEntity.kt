package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// two states only: you're focusing or you're not. the old SHORT_BREAK/LONG_BREAK split made the
// user pick between two things that felt identical. rows written before the merge still carry
// those strings, and fromStored folds them back in
enum class PomodoroSessionType {
    FOCUS,
    BREAK;

    companion object {
        // reads a persisted sessionType, mapping the retired break variants onto BREAK
        fun fromStored(raw: String): PomodoroSessionType = when (raw) {
            FOCUS.name -> FOCUS
            else -> BREAK
        }
    }
}
enum class PomodoroCompletionStatus { COMPLETED, CANCELLED, RESET, IN_PROGRESS }

@Entity(tableName = "pomodoro_sessions")
data class PomodoroSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
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

