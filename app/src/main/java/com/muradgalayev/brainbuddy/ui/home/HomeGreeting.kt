package com.muradgalayev.brainbuddy.ui.home

import java.time.LocalTime
import com.muradgalayev.brainbuddy.R

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
private val GreetingPool: Map<DayBand, List<Int>> = mapOf(
    DayBand.EarlyMorning to listOf(
        R.string.greet_good_morning,
        R.string.greet_early_start,
        R.string.greet_up_with_sun,
        R.string.greet_morning_slow,
        R.string.greet_quiet_hours,
    ),
    DayBand.Morning to listOf(
        R.string.greet_good_morning,
        R.string.greet_morning,
        R.string.greet_fresh_page,
        R.string.greet_lets_begin,
        R.string.greet_rise_shine,
    ),
    DayBand.Afternoon to listOf(
        R.string.greet_good_afternoon,
        R.string.greet_afternoon,
        R.string.greet_halfway,
        R.string.greet_still_going,
        R.string.greet_keep_steady,
    ),
    DayBand.Evening to listOf(
        R.string.greet_good_evening,
        R.string.greet_evening,
        R.string.greet_winding_down,
        R.string.greet_long_day,
        R.string.greet_easy_does_it,
    ),
    DayBand.Night to listOf(
        R.string.greet_good_night,
        R.string.greet_late_one,
        R.string.greet_time_to_rest,
        R.string.greet_almost_bedtime,
        R.string.greet_night,
    ),
    DayBand.LateNight to listOf(
        R.string.greet_night_owl,
        R.string.greet_still_up,
        R.string.greet_midnight_oil,
        R.string.greet_world_asleep,
        R.string.greet_rest_soon,
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
// lookup reads a template by id, Resources::getString in the app and strings.xml in the tests
fun homeGreeting(time: LocalTime, name: String?, seed: Int, lookup: (Int) -> String): String {
    val pool = GreetingPool.getValue(dayBandFor(time))
    return personalise(lookup(pool[Math.floorMod(seed, pool.size)]), name)
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
