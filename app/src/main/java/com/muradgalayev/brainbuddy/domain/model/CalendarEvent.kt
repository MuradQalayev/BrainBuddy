package com.muradgalayev.brainbuddy.domain.model

/**
 * Calendar event. Independent from TodoItem.
 *
 * `startTime` / `endTime` are ISO-8601 local datetime strings (e.g. "2026-05-05T10:00:00").
 * The "date" of an event is implicit in `startTime`.
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val color: String,
    val link: String = "",
)
