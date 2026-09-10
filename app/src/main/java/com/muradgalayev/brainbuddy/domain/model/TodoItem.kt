package com.muradgalayev.brainbuddy.domain.model

data class TodoItem(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val date: String,
    val startTime: String,
    val endTime: String,
    val priority: String,
    val attendees: Int,
    val color: String,
    val category: String,
    // author's user id when a Together connection added this task to your list, null for tasks you
    // created yourself. mirrors CalendarEvent.createdByOther, see that model for the owner/author
    // split
    val createdByOther: String? = null,
)