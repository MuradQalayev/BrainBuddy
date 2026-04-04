package com.muradgalayev.brainbuddy.data.mapper


import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.domain.model.TodoItem

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

fun TodoItem.toEntity(): TodoItemEntity {
    return TodoItemEntity(
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