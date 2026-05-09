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

    @SerialName("top_goal")
    val topGoal: String = "",

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

    @SerialName("survey_completed")
    val surveyCompleted: Boolean = false,

    @SerialName("survey_version")
    val surveyVersion: Int = 0,

    @SerialName("updated_at")
    val updatedAt: String? = null,
)
