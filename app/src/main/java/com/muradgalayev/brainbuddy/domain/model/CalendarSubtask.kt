package com.muradgalayev.brainbuddy.domain.model

/**
 * A focused chunk inside a CalendarEvent. Lets users break a 1-hour
 * "Math exam prep" block into smaller stations like "review formulas (15m)"
 * → "practice problems (30m)" → "flashcards (15m)".
 *
 * Subtasks are ordered (orderIndex) and sized in minutes, not absolute times,
 * so reordering doesn't require time math.
 */
data class CalendarSubtask(
    val id: String,
    val eventId: String,
    val title: String,
    val durationMinutes: Int,
    val orderIndex: Int,
    val completed: Boolean,
    val kind: SubtaskKind = SubtaskKind.FOCUS,
)

enum class SubtaskKind { FOCUS, BREAK }