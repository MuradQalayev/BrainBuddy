package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.data.remote.dto.TodoItemDto
import com.muradgalayev.brainbuddy.domain.model.TodoItem

// Room entity to domain model
fun TodoItemEntity.toDomain(): TodoItem {
    return TodoItem(
        id = id,
        title = title,
        description = description,
        isCompleted = isCompleted,
        date = date,
        startTime = startTime,
        endTime = endTime,
        priority = priority,
        attendees = attendees,
        color = color,
        category = category,
        // only surface authorship when it isn't the owner's own task, so callers can treat non-null as
        // 'someone else added this' without comparing ids
        createdByOther = createdBy?.takeIf { it != userId },
    )
}

// domain model to Room entity
fun TodoItem.toEntity(userId: String): TodoItemEntity {
    return TodoItemEntity(
        id = id,
        userId = userId,
        title = title,
        description = description,
        isCompleted = isCompleted,
        date = date,
        startTime = startTime,
        endTime = endTime,
        priority = priority,
        attendees = attendees,
        color = color,
        category = category,
        // an edit by the owner must not steal authorship from the connection that added the task, or
        // they'd lose access to the row they created
        createdBy = createdByOther ?: userId,
        syncStatus = SyncStatus.PENDING_INSERT.name,
        lastModifiedAt = System.currentTimeMillis()
    )
}

// Room entity to Supabase DTO
fun TodoItemEntity.toDto(userId: String): TodoItemDto {
    return TodoItemDto(
        id = id,
        userId = userId,
        title = title,
        description = description,
        isCompleted = isCompleted,
        date = date,
        startTime = startTime,
        endTime = endTime,
        priority = priority,
        attendees = attendees,
        color = color,
        category = category,
        // never null on the wire, see CalendarEventMapper for the reasoning
        createdBy = createdBy ?: userId,
    )
}

// Supabase DTO to Room entity
fun TodoItemDto.toEntity(): TodoItemEntity {
    return TodoItemEntity(
        id = id,
        userId = userId,
        title = title,
        description = description,
        isCompleted = isCompleted,
        date = date,
        startTime = startTime,
        endTime = endTime,
        priority = priority,
        attendees = attendees,
        color = color,
        category = category,
        createdBy = createdBy,
        syncStatus = SyncStatus.SYNCED.name,
        lastModifiedAt = System.currentTimeMillis()
    )
}
