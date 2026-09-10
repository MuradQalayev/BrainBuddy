package com.muradgalayev.brainbuddy.ui.home

import java.time.LocalTime

// the sign-off at the foot of home. it exists to make the screen feel like it noticed you
// turned up, so a single fixed line would defeat the point, and by the third visit it reads as
// chrome. each band owns a handful of phrasings and the caller picks one per visit.
// the bands are cut finer than morning/afternoon/evening: opening the app at 02:00 is a
// different situation from opening it at 22:00, and greeting a 2am check-in with 'good evening'
// is the kind of small wrongness that makes the whole thing feel automated
enum class DayBand {
    EarlyMorning,
    Morning,
    Afternoon,
    Evening,
    Night,
    LateNight,
}

// bands are half-open, so no minute belongs to two of them
fun dayBandFor(time: LocalTime): DayBand = when (time.hour) {
    in 5..7 -> DayBand.EarlyMorning
    in 8..11 -> DayBand.Morning
    in 12..16 -> DayBand.Afternoon
    in 17..20 -> DayBand.Evening
    in 21..23 -> DayBand.Night
    else -> DayBand.LateNight
}

// {name} is a slot rather than a suffix, because the name doesn't always land at the end.
// every line is written to survive the name being missing
private val GreetingPool: Map<DayBand, List<String>> = mapOf(
    DayBand.EarlyMorning to listOf(
        "Good morning, {name}",
        "Early start, {name}",
        "Up with the sun, {name}",
        "Morning, {name} — take it slow",
        "The quiet hours, {name}",
    ),
    DayBand.Morning to listOf(
        "Good morning, {name}",
        "Morning, {name}",
        "Fresh page, {name}",
        "Hey {name}, let's begin",
        "Rise and shine, {name}",
    ),
    DayBand.Afternoon to listOf(
        "Good afternoon, {name}",
        "Afternoon, {name}",
        "Halfway there, {name}",
        "Still going, {name}",
        "Hey {name}, keep it steady",
    ),
    DayBand.Evening to listOf(
        "Good evening, {name}",
        "Evening, {name}",
        "Winding down, {name}",
        "Long day, {name}?",
        "Hey {name}, easy does it",
    ),
    DayBand.Night to listOf(
        "Good night, {name}",
        "Late one, {name}",
        "Time to rest, {name}",
        "Almost bedtime, {name}",
        "Night, {name}",
    ),
    DayBand.LateNight to listOf(
        "Night owl, {name}",
        "Still up, {name}?",
        "Burning the midnight oil, {name}",
        "The world's asleep, {name}",
        "Rest soon, {name}",
    ),
)

// drops the slot rather than leaving a hole when we don't know the name. the comma goes with
// it, since 'Good morning,' reads as a line that failed halfway
private fun personalise(template: String, name: String?): String =
    if (name.isNullOrBlank()) {
        template.replace(", {name}", "").replace(" {name}", "").replace("{name}", "")
    } else {
        template.replace("{name}", name.trim())
    }

// picks the greeting for a time, personalised with a name. seed chooses which phrasing within
// the band, and the caller holds it steady for a visit so the line doesn't reshuffle under the
// user while they're reading it. any Int works, it's folded into range here
fun homeGreeting(time: LocalTime, name: String?, seed: Int): String {
    val pool = GreetingPool.getValue(dayBandFor(time))
    return personalise(pool[Math.floorMod(seed, pool.size)], name)
}

// the greeting wants 'Murad', not 'Murad Galayev', and falls back through username then email
// local-part, since a profile can carry any one of the three
fun firstNameFrom(displayName: String?, username: String?, email: String?): String? {
    val source = displayName?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: return null
    return source.trim().split(' ', '.', '_', '-').firstOrNull { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
}
