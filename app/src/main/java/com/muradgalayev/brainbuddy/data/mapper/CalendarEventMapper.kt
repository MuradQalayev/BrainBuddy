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
    completed = completed,
    // only surface authorship when it isn't the owner's own event, so the UI can treat a non-null
    // value as 'someone else added this' without comparing ids
    createdByOther = createdBy?.takeIf { it != userId },
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
    completed = completed,
    // an edit made by the owner must not steal authorship from the connection that added the event,
    // or they'd lose their own access to it
    createdBy = createdByOther ?: userId,
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
    completed = completed,
    // never null on the wire: the connection INSERT policy requires created_by = auth.uid(), and
    // owner-authored rows point at themselves
    createdBy = createdBy ?: userId,
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
    completed = completed,
    createdBy = createdBy,
    syncStatus = SyncStatus.SYNCED.name,
    lastModifiedAt = System.currentTimeMillis()
)
