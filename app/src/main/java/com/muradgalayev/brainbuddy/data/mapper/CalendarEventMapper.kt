package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.local.entity.CalendarEventEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.remote.dto.CalendarEventDto
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent

fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
    id = id,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    location = location,
    color = color,
    link = link,
)

fun CalendarEvent.toEntity(userId: String): CalendarEventEntity = CalendarEventEntity(
    id = id,
    userId = userId,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    location = location,
    color = color,
    link = link,
    syncStatus = SyncStatus.PENDING_INSERT.name,
    lastModifiedAt = System.currentTimeMillis()
)

fun CalendarEventEntity.toDto(userId: String): CalendarEventDto = CalendarEventDto(
    id = id,
    userId = userId,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    location = location,
    color = color,
    link = link,
)

fun CalendarEventDto.toEntity(): CalendarEventEntity = CalendarEventEntity(
    id = id,
    userId = userId,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    location = location,
    color = color,
    link = link,
    syncStatus = SyncStatus.SYNCED.name,
    lastModifiedAt = System.currentTimeMillis()
)
