package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.data.remote.dto.TodoItemDto
import com.muradgalayev.brainbuddy.domain.model.TodoItem

/**
 * Maps Room Entity to Domain Model
 */
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
        category = category
    )
}

/**
 * Maps Domain Model to Room Entity
 */
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
        syncStatus = SyncStatus.PENDING_INSERT.name,
        lastModifiedAt = System.currentTimeMillis()
    )
}

/**
 * Maps Room Entity to Supabase DTO
 */
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
        category = category
    )
}

/**
 * Maps Supabase DTO to Room Entity
 */
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
        syncStatus = SyncStatus.SYNCED.name,
        lastModifiedAt = System.currentTimeMillis()
    )
}
