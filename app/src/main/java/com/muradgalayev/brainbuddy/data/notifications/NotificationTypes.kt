package com.muradgalayev.brainbuddy.data.notifications

// which stream of scheduled reminders a lead-time preference applies to. the two are tuned
// independently: a user might want a day-before heads-up for calendar events but only an
// at-start nudge for to-dos
enum class ReminderCategory { TODO, CALENDAR }

// how often the 'come focus' nudge fires each day. ONCE at the chosen time, TWICE adds a
// second one later in the day
enum class PomodoroNudgeFrequency { OFF, ONCE, TWICE }
