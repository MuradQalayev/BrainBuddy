package com.muradgalayev.brainbuddy.domain.scheduling

import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import java.time.LocalDateTime
import java.time.LocalTime

// turns the app's two kinds of commitment into the one thing the suggestion engine
// understands: a slice of a day that is already spoken for. lives in the domain layer rather
// than beside a screen because both the user's own calendar and the add-to-a-connection's-day
// flow need exactly this reduction, and two copies would drift, one of them silently deciding
// an untimed to-do blocks an hour while the other says half

// a start with no end still means 'I'm doing something then'
private const val UNTIMED_BLOCK_MINUTES = 30

fun CalendarEvent.toBusyIntervalOrNull(): BusyInterval? {
    val start = parseIsoOrNull(startTime) ?: return null
    val end = parseIsoOrNull(endTime) ?: return null
    val startMinutes = start.hour * 60 + start.minute
    val endMinutes = end.hour * 60 + end.minute
    return BusyInterval(
        startMinutes = startMinutes,
        // an event running past midnight blocks the rest of this day rather than wrapping to a
        // negative-length interval the scorer would ignore
        endMinutes = if (endMinutes > startMinutes) endMinutes else MINUTES_PER_DAY - 1,
        title = title,
    )
}

// a to-do as a blocked slice of the day, or null when it doesn't occupy one. two kinds are
// deliberately ignored: a finished to-do is no longer a commitment, and an untimed one is
// 'sometime today' rather than a slot, so treating those as blockers would let a long backlog
// swallow the whole day and leave the suggester nowhere to put anything
fun TodoItem.toBusyIntervalOrNull(): BusyInterval? {
    if (isCompleted) return null
    val start = parseHourMinuteOrNull(startTime) ?: return null
    val startMinutes = start.hour * 60 + start.minute
    val endMinutes = parseHourMinuteOrNull(endTime)
        ?.let { it.hour * 60 + it.minute }
        // half an hour is the honest guess for a to-do with only a start time: enough to stop an event
        // landing on top of it, short enough not to fence off an evening over a five-minute call
        ?.takeIf { it > startMinutes }
        ?: (startMinutes + UNTIMED_BLOCK_MINUTES)
    return BusyInterval(
        startMinutes = startMinutes,
        endMinutes = endMinutes.coerceAtMost(MINUTES_PER_DAY - 1),
        title = title,
    )
}

private fun parseIsoOrNull(value: String): LocalDateTime? = runCatching {
    // stored values are sometimes minute-precision, which the ISO parser accepts, and sometimes
    // not, so pad rather than reject
    LocalDateTime.parse(if (value.length == 16 && value.contains('T')) "$value:00" else value)
}.getOrNull()

private fun parseHourMinuteOrNull(hhmm: String): LocalTime? = runCatching {
    val (h, m) = hhmm.split(":").map { it.trim().toInt() }
    LocalTime.of(h, m)
}.getOrNull()
