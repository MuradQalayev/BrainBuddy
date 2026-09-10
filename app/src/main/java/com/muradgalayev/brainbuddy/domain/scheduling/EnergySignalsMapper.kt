package com.muradgalayev.brainbuddy.domain.scheduling

import com.muradgalayev.brainbuddy.data.health.HealthConnectUiState

// Health Connect's view of how much the user has left, or null when it isn't connected or has
// nothing recorded.
// null is the important case. an absent signal has to read as unknown, never as well rested:
// the engine only ever uses energy to penalise demanding slots, so a false rested silently
// removes a protection the user can't see was there.
// shared by the calendar screen and SuggestTimeUseCase so the assistant and the chips read the
// same body. two copies would be two different definitions of a tired day
fun HealthConnectUiState.toEnergySignalsOrNull(): EnergySignals? {
    if (!connected) return null
    // zero-hour nights are unworn-watch nights rather than sleepless ones, and averaging them in
    // would invent a chronic sleep debt out of missing data
    val nights = sleepLast7Days.map { it.hours }.filter { it > 0.5 }
    val signals = EnergySignals(
        stepsToday = todaySteps,
        stepsDailyAverage = stepsLast7Days?.let { it / 7 },
        lastSleepHours = nights.lastOrNull() ?: lastSleepHours,
        sleepBaselineHours = nights.takeIf { it.isNotEmpty() }?.average(),
    )
    return signals.takeIf { todaySteps != null || it.lastSleepHours != null }
}
