package com.muradgalayev.brainbuddy.domain.scheduling

// what an event is, as far as timing is concerned. deliberately not an AI classification:
// every field here is a number a human can argue with, which is what makes a suggestion
// explainable ('dinner is usually around 19:30') instead of a black box the user has to take
// on faith. it's also free, with no tokens, no model download and no network.
// the fields split into two jobs:
// - windowStart/windowEnd are a hard clamp. however strange the user's history looks, we never
//   propose dinner at 07:00. history moves the peak inside the window, it can't escape it.
// - priorPeak/priorSpread are the untrained guess, used at full strength for a brand-new user
//   and progressively overridden as their own history accumulates.
// all times are minutes from local midnight, so 19:30 is 1170
enum class ActivityKind(
    val key: String,
    // human name used in suggestion explanations, lowercase
    val label: String,
    val windowStart: Int,
    val windowEnd: Int,
    val priorPeak: Int,
    // one standard deviation of the untrained peak, in minutes
    val priorSpread: Int,
    val typicalDurationMinutes: Int,
    // how much the activity costs the body, 0f restful to 1f maximal. drives the tiredness
    // penalty: a depleted user gets pushed away from a 90-minute gym session, never from dinner
    val demand: Float,
    // true when the body or the world expects a fixed clock time regardless of how the day is
    // going. meals, medication and appointments are anchored, deep work and chores will happily
    // slide to wherever there's room
    val anchored: Boolean,
) {
    Breakfast("breakfast", "breakfast", 300, 720, 480, 60, 30, 0.10f, true),
    Lunch("lunch", "lunch", 660, 960, 780, 50, 45, 0.10f, true),
    Dinner("dinner", "dinner", 1020, 1350, 1170, 70, 60, 0.10f, true),
    Snack("snack", "coffee break", 540, 1260, 960, 180, 20, 0.05f, false),

    Workout("workout", "workout", 300, 1290, 1080, 210, 60, 0.95f, false),
    Walk("walk", "walk", 420, 1260, 1020, 240, 30, 0.30f, false),

    DeepWork("deep_work", "focus block", 420, 1260, 600, 180, 90, 0.90f, false),
    Study("study", "study", 480, 1320, 960, 210, 60, 0.85f, false),
    Meeting("meeting", "meeting", 480, 1140, 660, 150, 45, 0.50f, false),
    Errand("errand", "errand", 540, 1140, 720, 180, 45, 0.40f, false),
    Chore("chore", "chore", 480, 1290, 1140, 210, 30, 0.35f, false),

    Appointment("appointment", "appointment", 480, 1080, 600, 150, 45, 0.40f, true),
    Medication("medication", "medication", 360, 1320, 480, 240, 5, 0.05f, true),

    Social("social", "catch-up", 660, 1320, 1200, 150, 120, 0.50f, false),
    Leisure("leisure", "downtime", 600, 1350, 1230, 180, 60, 0.15f, false),
    Commute("commute", "commute", 360, 1260, 510, 180, 30, 0.30f, true),
    SelfCare("self_care", "self-care", 360, 1380, 1290, 240, 20, 0.10f, false),
    WindDown("wind_down", "wind-down", 1140, 1410, 1320, 90, 30, 0.05f, true),

    // nothing recognisable in the title. wide open window, weak prior
    General("general", "event", 420, 1320, 900, 300, 60, 0.50f, false);

    // true for the families where 'you usually eat around X' is the whole story
    val isMeal: Boolean get() = this == Breakfast || this == Lunch || this == Dinner

    companion object {
        fun fromKey(key: String): ActivityKind? = entries.firstOrNull { it.key == key }
    }
}
