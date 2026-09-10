package com.muradgalayev.brainbuddy.ui.home

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// what one line of typed text turned into. date and time are null when the text said nothing
// about them, and the caller decides the default: a task with no time is fine, an event needs one
data class QuickCaptureDraft(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
) {
    val hasWhen: Boolean get() = date != null || time != null
}

// pulls a date and a time out of a quick-capture line, and hands back the line with those
// words removed. deliberately not AI: capture has to land the instant the key is pressed, so
// anything with a network round-trip is disqualified, the same reasoning as the calendar's
// time suggestions.
// the rules stay narrow on purpose. only unambiguous tokens are consumed ('tomorrow', 'at
// 3pm', 'in 20 min'), and a word that could plausibly be part of the task itself is left alone.
// eating a word out of someone's task is a worse failure than missing a time they can still
// tap in, so every pattern here errs towards missing it
fun parseQuickCapture(raw: String, now: LocalDateTime = LocalDateTime.now()): QuickCaptureDraft {
    var text = raw
    var date: LocalDate? = null
    var time: LocalTime? = null
    // 'tomorrow morning' is only a nudge, an explicit 'at 11:45' in the same line wins
    var impliedTime: LocalTime? = null

    // 'in 20 min' or 'in 2h' gives both halves at once, so it wins before anything else
    RELATIVE.find(text)?.let { match ->
        val amount = match.groupValues[1].toLongOrNull() ?: 0L
        val hours = match.groupValues[2].lowercase().startsWith("h")
        val target = if (hours) now.plusHours(amount) else now.plusMinutes(amount)
        date = target.toLocalDate()
        time = target.toLocalTime().withSecond(0).withNano(0)
        text = text.removeRange(match.range)
    }

    if (date == null) {
        NEXT_WEEK.find(text)?.let { match ->
            date = now.toLocalDate().plusWeeks(1)
            text = text.removeRange(match.range)
        }
    }

    if (date == null) {
        DAY.find(text)?.let { match ->
            val (dayWord, partOfDay) = match.groupValues[1].lowercase() to match.groupValues[2].lowercase()
            date = resolveDay(dayWord, now.toLocalDate())
            impliedTime = partOfDayTime(partOfDay)
                ?: if (dayWord == "tonight") LocalTime.of(20, 0) else null
            text = text.removeRange(match.range)
        }
    }

    if (time == null) {
        text = matchTime(text) { hour, minute -> time = LocalTime.of(hour, minute) }
    }

    // a bare '9' only reads as a time next to 'at', and only when nothing else fixed the clock.
    // resolve it to whichever of 9am or 9pm is still ahead: 'call mum at 8' typed at lunchtime
    // means tonight
    if (time == null) {
        AT_HOUR.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            if (hour != null && hour in 0..23) {
                val sameDay = date == null || date == now.toLocalDate()
                val resolved = if (sameDay && hour in 1..11 && hour <= now.hour) hour + 12 else hour
                time = LocalTime.of(resolved % 24, 0)
                text = text.removeRange(match.range)
            }
        }
    }

    val title = tidy(text).ifBlank { raw.trim() }
    return QuickCaptureDraft(title = title, date = date, time = time ?: impliedTime)
}

// runs the clock patterns in specificity order, reporting the first hit
private inline fun matchTime(text: String, onFound: (hour: Int, minute: Int) -> Unit): String {
    MERIDIEM_TIME.find(text)?.let { match ->
        val hour12 = match.groupValues[1].toIntOrNull() ?: return@let
        if (hour12 !in 1..12) return@let
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (minute !in 0..59) return@let
        val pm = match.groupValues[3].lowercase().startsWith("p")
        val hour = when {
            pm && hour12 < 12 -> hour12 + 12
            !pm && hour12 == 12 -> 0
            else -> hour12
        }
        onFound(hour, minute)
        return text.removeRange(match.range)
    }
    CLOCK_TIME.find(text)?.let { match ->
        val hour = match.groupValues[1].toIntOrNull() ?: return@let
        val minute = match.groupValues[2].toIntOrNull() ?: return@let
        onFound(hour, minute)
        return text.removeRange(match.range)
    }
    return text
}

private fun resolveDay(word: String, today: LocalDate): LocalDate = when (word) {
    "today", "tonight" -> today
    "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
    else -> {
        val target = DayOfWeek.valueOf(word.uppercase())
        val delta = (target.value - today.dayOfWeek.value + 7) % 7
        today.plusDays(delta.toLong())
    }
}

private fun partOfDayTime(part: String): LocalTime? = when (part) {
    "morning" -> LocalTime.of(9, 0)
    "afternoon" -> LocalTime.of(14, 0)
    "evening" -> LocalTime.of(19, 0)
    "night" -> LocalTime.of(20, 0)
    else -> null
}

// collapses the holes left by the removals, plus the connectives that dangle after them
private fun tidy(text: String): String = text
    .replace(WHITESPACE, " ")
    .trim()
    .replace(DANGLING, "")
    .trim()
    .trim(',', '-', ':', ';', '.', '@')
    .trim()

private val WHITESPACE = Regex("""\s+""")

// 'call sam at' or 'gym on', left behind once the day or time word is gone
private val DANGLING = Regex("""\s+(at|on|in|by|@)$""", RegexOption.IGNORE_CASE)

private val RELATIVE = Regex(
    """\bin\s+(\d{1,3})\s*(minutes|minute|mins|min|m|hours|hour|hrs|hr|h)\b""",
    RegexOption.IGNORE_CASE,
)

private val NEXT_WEEK = Regex("""\bnext\s+week\b""", RegexOption.IGNORE_CASE)

// full weekday names only. three-letter forms were dropped after 'buy sun cream' lost its
// sunscreen to Sunday: the abbreviations collide with ordinary words too often to be worth
// the keystrokes they save
private val DAY = Regex(
    """\b(?:on\s+|next\s+)?""" +
        """(today|tonight|tomorrow|tmrw|tmr|monday|tuesday|wednesday|thursday|friday|saturday|sunday)""" +
        """(?:\s+(morning|afternoon|evening|night))?\b""",
    RegexOption.IGNORE_CASE,
)

private val MERIDIEM_TIME = Regex(
    """\b(?:at\s+|@\s*)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)\b""",
    RegexOption.IGNORE_CASE,
)

private val CLOCK_TIME = Regex("""\b(?:at\s+|@\s*)?([01]?\d|2[0-3]):([0-5]\d)\b""")

private val AT_HOUR = Regex("""\b(?:at|@)\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE)
