package com.muradgalayev.brainbuddy.domain.scheduling

import java.time.LocalDate

// why a time was proposed. an enum rather than a baked string, so the wording lives in one
// place and the reason itself stays testable
enum class SuggestionReason {
    // the user's own history for this exact habit points here
    UsualTime,

    // it sits right up against something already on the day
    AfterEvent,

    // shortened or moved because Health Connect says today has been heavy
    EnergyAware,

    // inside the productive window they picked during onboarding
    FocusWindow,

    // late, restful, and safely before bedtime
    BeforeBed,

    // no personal history yet, this is where this kind of thing normally goes
    TypicalForActivity,

    // nothing clever to say: it's simply the clearest space in the day
    ClearGap,

    // clear on both calendars, chosen around a connection's shared free/busy as well as the
    // user's own day. says only that the hour is open; what fills the hours around it is never
    // known to us, let alone shown
    FreeForBoth,

    // a different day altogether beats the one being edited, either the user is spent or today
    // has no room. always the last chip, and never the only one unless the selected day genuinely
    // has nowhere to put this
    BetterAnotherDay,
}

// one proposed slot, ready for a chip in the add-event dialog. reasonText is not decoration:
// a suggestion the user doesn't understand is one they'll ignore, and for an ADHD app a
// wrong-feeling nudge with no stated basis is worse than no nudge at all
data class TimeSuggestion(
    val date: LocalDate,
    val startMinutes: Int,
    val endMinutes: Int,
    val kind: ActivityKind,
    val reason: SuggestionReason,
    val reasonText: String,
    // raw engine score, only meaningful relative to the other suggestions
    val score: Double,
) {
    val startLabel: String get() = formatHhMm(startMinutes)
    val endLabel: String get() = formatHhMm(endMinutes)
    val durationMinutes: Int get() = endMinutes - startMinutes

    // short prefix naming the day, or null when the suggestion is for the day the user already
    // has open, where a label would be noise. anything that moves the event to a different date
    // has to say so on the chip itself, since a time alone would silently reschedule what they
    // are writing.
    // two different dates are in play and conflating them was a real bug: relativeTo is the day
    // the dialog is writing to, while 'tomorrow' is a word about today. editing an event on the
    // 25th and being offered the 26th used to read Tomorrow, which a reader on the 24th
    // understands as the 25th, the very day they were trying to move off. so the relative words
    // are only used when they are true of today, and every other day states its date outright
    fun dayLabel(relativeTo: LocalDate, today: LocalDate): String? = when {
        date == relativeTo -> null
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        // inside the week the weekday is the fastest thing to read, but never on its own: 'Fri'
        // alone is the same ambiguity one week out
        date.isBefore(today.plusDays(7)) -> "%s %d".format(
            date.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            ),
            date.dayOfMonth,
        )
        else -> "%d %s".format(
            date.dayOfMonth,
            date.month.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            ),
        )
    }

    fun movesDay(relativeTo: LocalDate): Boolean = date != relativeTo
}

fun formatHhMm(minutes: Int): String {
    val m = wrapMinutes(minutes)
    return "%02d:%02d".format(m / 60, m % 60)
}
