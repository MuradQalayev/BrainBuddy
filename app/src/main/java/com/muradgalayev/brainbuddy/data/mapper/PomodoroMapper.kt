package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionEntity
import com.muradgalayev.brainbuddy.domain.model.PomodoroSession

fun PomodoroSessionEntity.toDomain(): PomodoroSession {
    return PomodoroSession(
        id = id,
        sessionType = sessionType,
        plannedDurationMs = plannedDurationMs,
        actualDurationMs = actualDurationMs,
        pausedDurationMs = pausedDurationMs,
        extraTimeAddedMs = extraTimeAddedMs,
        startTime = startTime,
        endTime = endTime,
        completionStatus = completionStatus,
        resetCount = resetCount,
        wasInterrupted = wasInterrupted,
        focusModeEnabled = focusModeEnabled,
        focusModePermissionGranted = focusModePermissionGranted,
        focusModeActivated = focusModeActivated,
        focusModeOnTimestamp = focusModeOnTimestamp,
        focusModeOffTimestamp = focusModeOffTimestamp,
        focusModeRestoredSuccessfully = focusModeRestoredSuccessfully
    )
}

fun PomodoroSession.toEntity(): PomodoroSessionEntity {
    return PomodoroSessionEntity(
        id = id,
        sessionType = sessionType,
        plannedDurationMs = plannedDurationMs,
        actualDurationMs = actualDurationMs,
        pausedDurationMs = pausedDurationMs,
        extraTimeAddedMs = extraTimeAddedMs,
        startTime = startTime,
        endTime = endTime,
        completionStatus = completionStatus,
        resetCount = resetCount,
        wasInterrupted = wasInterrupted,
        focusModeEnabled = focusModeEnabled,
        focusModePermissionGranted = focusModePermissionGranted,
        focusModeActivated = focusModeActivated,
        focusModeOnTimestamp = focusModeOnTimestamp,
        focusModeOffTimestamp = focusModeOffTimestamp,
        focusModeRestoredSuccessfully = focusModeRestoredSuccessfully
    )
}