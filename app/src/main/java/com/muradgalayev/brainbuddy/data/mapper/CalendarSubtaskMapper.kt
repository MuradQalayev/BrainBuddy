package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.local.entity.CalendarSubtaskEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.remote.dto.CalendarSubtaskDto

fun CalendarSubtaskEntity.toDto(): CalendarSubtaskDto = CalendarSubtaskDto(
    id = id,
    userId = userId,
    eventId = eventId,
    title = title,
    durationMinutes = durationMinutes,
    orderIndex = orderIndex,
    completed = completed,
    kind = kind,
)

fun CalendarSubtaskDto.toEntity(): CalendarSubtaskEntity = CalendarSubtaskEntity(
    id = id,
    userId = userId,
    eventId = eventId,
    title = title,
    durationMinutes = durationMinutes,
    orderIndex = orderIndex,
    completed = completed,
    kind = kind,
    syncStatus = SyncStatus.SYNCED.name,
    lastModifiedAt = System.currentTimeMillis(),
)