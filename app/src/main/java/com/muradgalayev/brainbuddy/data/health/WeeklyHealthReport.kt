package com.muradgalayev.brainbuddy.data.health

import java.time.LocalDate

// one day's wellness figures, as they'll appear in a row of the exported report. every field is
// nullable and stays null when the data genuinely isn't there: a day with no watch on the wrist
// must not render as '0 bpm', which would read as a measurement rather than a gap
data class DailyHealthEntry(
    val date: LocalDate,
    val steps: Long? = null,
    val averageHeartRate: Long? = null,
    val restingHeartRate: Long? = null,
    val caloriesKcal: Long? = null,
    val sleepHours: Double? = null,
    val exerciseMinutes: Long? = null,
)

// seven days of wellness data, newest last. built on demand straight from Health Connect rather
// than accumulated in our own table: Health Connect already retains the history, so
// snapshotting it daily would mean storing a second copy of health data we don't need to hold,
// and under GDPR the less special-category data we persist the better
data class WeeklyHealthReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DailyHealthEntry> = emptyList(),
) {
    val daysWithAnyData: Int
        get() = days.count {
            it.steps != null || it.averageHeartRate != null || it.caloriesKcal != null ||
                it.sleepHours != null || it.exerciseMinutes != null
        }

    val averageSteps: Long?
        get() = days.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.average()?.toLong()

    val averageHeartRate: Long?
        get() = days.mapNotNull { it.averageHeartRate }.takeIf { it.isNotEmpty() }
            ?.average()?.toLong()

    val averageRestingHeartRate: Long?
        get() = days.mapNotNull { it.restingHeartRate }.takeIf { it.isNotEmpty() }
            ?.average()?.toLong()

    val averageSleepHours: Double?
        get() = days.mapNotNull { it.sleepHours }.takeIf { it.isNotEmpty() }?.average()

    val totalExerciseMinutes: Long
        get() = days.sumOf { it.exerciseMinutes ?: 0L }

    val totalCalories: Long
        get() = days.sumOf { it.caloriesKcal ?: 0L }
}
