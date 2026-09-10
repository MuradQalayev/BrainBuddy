package com.muradgalayev.brainbuddy.data.notifications

enum class ReminderKind(val offsetCode: Int, val leadMillis: Long) {
    DAY_BEFORE(1, 24L * 60L * 60L * 1000L),
    MIN_30_BEFORE(2, 30L * 60L * 1000L),
    AT_START(3, 0L);

    companion object {
        fun fromName(name: String?): ReminderKind? =
            entries.firstOrNull { it.name == name }
    }
}

object ReminderCopy {

    fun titleFor(kind: ReminderKind): String = when (kind) {
        ReminderKind.DAY_BEFORE -> "Tomorrow's plan"
        ReminderKind.MIN_30_BEFORE -> "Starting soon"
        ReminderKind.AT_START -> "On your schedule now"
    }

    fun bodyFor(kind: ReminderKind, itemTitle: String, timeLabel: String): String {
        val variants = when (kind) {
            ReminderKind.DAY_BEFORE -> listOf(
                "Tomorrow at $timeLabel: $itemTitle. Anything to prep tonight?",
                "Heads up — $itemTitle lands tomorrow at $timeLabel.",
                "On the radar: $itemTitle, tomorrow at $timeLabel.",
                "Future you has $itemTitle tomorrow ($timeLabel). Want to set out a reminder?",
            )
            ReminderKind.MIN_30_BEFORE -> listOf(
                "30 min until $itemTitle. Wrap what you're doing and breathe.",
                "Coming up at $timeLabel: $itemTitle. Quick stretch and head over.",
                "Half an hour out: $itemTitle. You've got this.",
                "$itemTitle starts in 30 — water, bathroom, and you're set.",
            )
            ReminderKind.AT_START -> listOf(
                "Starting now: $itemTitle. One step at a time.",
                "It's go time — $itemTitle.",
                "$itemTitle is up now. Just begin — perfect can wait.",
            )
        }
        // stable per-item variety, so the same event keeps the same line each time
        val idx = ((itemTitle.hashCode() ushr 1) % variants.size + variants.size) % variants.size
        return variants[idx]
    }

    private val focusNudges = listOf(
        "Ready for a focus sprint?" to "One 25-minute Pomodoro. Pick a task and press start.",
        "Got 25 minutes?" to "A single focus session now beats a perfect one later. Let's go.",
        "Time to lock in" to "Start a Pomodoro — momentum comes after you begin, not before.",
        "Little push" to "Set a timer, silence the noise, and give one thing your full attention.",
    )

    fun focusNudge(seed: Int = 0): Pair<String, String> {
        val idx = ((seed % focusNudges.size) + focusNudges.size) % focusNudges.size
        return focusNudges[idx]
    }

    fun pomodoroBreakStart(): Pair<String, String> =
        "Nice work — break time" to "Step away, stretch, hydrate. You earned it."

    fun pomodoroBreakOver(): Pair<String, String> =
        "Break's over" to "Ready to line up another focus session? Just begin."

    fun morningSummary(eventTitles: List<String>): Pair<String, String> {
        if (eventTitles.isEmpty()) {
            return "Good morning" to "No events on your calendar today — go gentle on yourself."
        }
        val list = when (eventTitles.size) {
            1 -> eventTitles[0]
            2 -> "${eventTitles[0]} and ${eventTitles[1]}"
            else -> eventTitles.take(2).joinToString(", ") +
                ", and ${eventTitles.size - 2} more"
        }
        return "Good morning" to "Today: $list. Pick the one that matters most."
    }
}