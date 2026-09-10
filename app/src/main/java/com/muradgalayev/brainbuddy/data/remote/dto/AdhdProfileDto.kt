package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdhdProfileDto(
    @SerialName("user_id")
    val userId: String,

    @SerialName("age_range")
    val ageRange: String = "",

    @SerialName("diagnosis_status")
    val diagnosisStatus: String = "",

    @SerialName("primary_symptoms")
    val primarySymptoms: List<String> = emptyList(),

    // legacy single goal. still written on every save so an older build reading this row keeps
    // working, and still read when topGoals is empty
    @SerialName("top_goal")
    val topGoal: String = "",

    // N16, up to three. authoritative when non-empty
    @SerialName("top_goals")
    val topGoals: List<String> = emptyList(),

    @SerialName("productive_time")
    val productiveTime: String = "",

    @SerialName("focus_duration_minutes")
    val focusDurationMinutes: Int? = null,

    @SerialName("sleep_bedtime")
    val sleepBedtime: String = "",

    @SerialName("sleep_wake_time")
    val sleepWakeTime: String = "",

    @SerialName("medication_status")
    val medicationStatus: String = "",

    @SerialName("medication_name")
    val medicationName: String = "",

    @SerialName("coping_strategies")
    val copingStrategies: List<String> = emptyList(),

    @SerialName("pain_point")
    val painPoint: String = "",

    @SerialName("ai_tone_preference")
    val aiTonePreference: String = "",

    @SerialName("city_id")
    val cityId: String? = null,

    // intake revision (N1-N18). all default to empty so a row written before these columns existed
    // still decodes, and so does a row from a build that doesn't know about them

    @SerialName("presentation")
    val presentation: String = "",

    @SerialName("co_occurring")
    val coOccurring: List<String> = emptyList(),

    @SerialName("chronotype")
    val chronotype: String = "",

    @SerialName("sleep_schedule_origin")
    val sleepScheduleOrigin: String = "",

    @SerialName("interruption_recall")
    val interruptionRecall: String = "",

    @SerialName("capture_need")
    val captureNeed: String = "",

    @SerialName("plan_change_impact")
    val planChangeImpact: String = "",

    @SerialName("task_return_effort")
    val taskReturnEffort: String = "",

    @SerialName("impulse_areas")
    val impulseAreas: List<String> = emptyList(),

    @SerialName("nudge_tone")
    val nudgeTone: String = "",

    @SerialName("check_in_ceiling")
    val checkInCeiling: String = "",

    @SerialName("missed_task_response")
    val missedTaskResponse: String = "",

    @SerialName("body_doubling_interest")
    val bodyDoublingInterest: String = "",

    @SerialName("work_environment")
    val workEnvironment: String = "",

    @SerialName("past_strategies")
    val pastStrategies: List<String> = emptyList(),

    @SerialName("survey_completed")
    val surveyCompleted: Boolean = false,

    @SerialName("survey_version")
    val surveyVersion: Int = 0,

    @SerialName("updated_at")
    val updatedAt: String? = null,
)
