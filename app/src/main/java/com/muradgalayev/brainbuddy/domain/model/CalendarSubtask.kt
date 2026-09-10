package com.muradgalayev.brainbuddy.domain.model

// a focused chunk inside a CalendarEvent. lets users break a 1-hour 'Math exam prep' block into
// smaller stations like 'review formulas 15m', 'practice problems 30m', 'flashcards 15m'.
// ordered by orderIndex and sized in minutes rather than absolute times, so reordering doesn't
// require time maths
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