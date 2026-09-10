package com.muradgalayev.brainbuddy.ui.onboarding

import com.muradgalayev.brainbuddy.domain.model.SurveyVersion

/** The single source of truth for page completion and reminder progress. */
fun questionnaireCompletion(
    state: OnboardingUiState,
    version: SurveyVersion,
): List<Boolean> {
    val usernameDone = state.usernameAvailability == UsernameAvailability.Available ||
        (state.usernameAvailability == UsernameAvailability.Idle && state.username.isNotBlank())

    val quick = listOf(
        usernameDone && state.firstName.isNotBlank(),
        state.cityId != null,
        state.ageRange.isNotBlank(),
        state.diagnosisStatus != null,
        state.primarySymptoms.isNotEmpty(),
        state.topGoals.isNotEmpty(),
    )
    if (version == SurveyVersion.Quick) return quick

    return listOf(
        /*  0 */ usernameDone && state.firstName.isNotBlank(),
        /*  1 */ state.cityId != null,
        /*  2 */ state.ageRange.isNotBlank(),
        /*  3 */ state.diagnosisStatus != null,
        /*  4 */ true, // co-occurring conditions are optional
        /*  5 */ state.primarySymptoms.isNotEmpty(),
        /*  6 */ state.topGoals.isNotEmpty(),
        /*  7 */ state.productiveTime != null,
        /*  8 */ state.chronotype != null,
        /*  9 */ true, // focus stretch has a default
        /* 10 */ state.sleepBedtime.isNotBlank() && state.sleepWakeTime.isNotBlank(),
        /* 11 */ state.sleepScheduleOrigin != null,
        /* 12 */ state.medicationStatus != null,
        /* 13 */ state.interruptionRecall != null,
        /* 14 */ state.captureNeed != null,
        /* 15 */ state.planChangeImpact != null,
        /* 16 */ state.taskReturnEffort != null,
        /* 17 */ state.impulseAreas.isNotEmpty(),
        /* 18 */ state.nudgeTone != null,
        /* 19 */ state.checkInCeiling != null,
        /* 20 */ state.missedTaskResponse != null,
        /* 21 */ state.bodyDoublingInterest != null,
        /* 22 */ state.workEnvironment != null,
        /* 23 */ state.pastStrategies.isNotEmpty(),
        /* 24 */ state.copingStrategies.isNotEmpty(),
        /* 25 */ state.painPoint.isNotBlank(),
        /* 26 */ state.aiTone != null,
    )
}
