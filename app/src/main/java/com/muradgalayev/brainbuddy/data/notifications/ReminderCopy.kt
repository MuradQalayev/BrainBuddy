package com.muradgalayev.brainbuddy.data.notifications

enum class ReminderKind(val offsetCode: Int) {
    DAY_BEFORE(1),
    MIN_30_BEFORE(2),
    AT_START(3);

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
        // Stable per-item variety so the same event keeps the same line each time.
        val idx = ((itemTitle.hashCode() ushr 1) % variants.size + variants.size) % variants.size
        return variants[idx]
    }

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