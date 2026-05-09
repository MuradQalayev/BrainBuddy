package com.muradgalayev.brainbuddy.domain.model

/**
 * The user's ADHD self-profile. One row per user in Supabase.
 * Designed as a single bag the AI assistant can fetch in one call
 * and prepend as context to every request.
 */
data class AdhdProfile(
    val userId: String,
    val ageRange: String = "",
    val diagnosisStatus: DiagnosisStatus? = null,
    val primarySymptoms: List<AdhdSymptom> = emptyList(),
    val topGoal: TopGoal? = null,

    val productiveTime: ProductiveTime? = null,
    val focusDurationMinutes: Int? = null,
    val sleepBedtime: String = "",
    val sleepWakeTime: String = "",
    val medicationStatus: MedicationStatus? = null,
    val medications: List<Medication> = emptyList(),
    val copingStrategies: List<CopingStrategy> = emptyList(),
    val painPoint: String = "",
    val aiTonePreference: AiTone? = null,

    val cityId: String? = null,

    val surveyCompleted: Boolean = false,
    val surveyVersion: SurveyVersion = SurveyVersion.None,
)

enum class SurveyVersion(val raw: Int) {
    None(0), Quick(1), Deep(2);

    companion object {
        fun fromRaw(value: Int): SurveyVersion =
            entries.firstOrNull { it.raw == value } ?: None
    }
}

enum class DiagnosisStatus(val key: String, val label: String) {
    Diagnosed("diagnosed", "Formally diagnosed"),
    SelfIdentified("self_identified", "Self-identified"),
    Exploring("exploring", "Still exploring");

    companion object {
        fun fromKey(key: String): DiagnosisStatus? = entries.firstOrNull { it.key == key }
    }
}

enum class AdhdSymptom(val key: String, val label: String) {
    Inattention("inattention", "Inattention"),
    Hyperactivity("hyperactivity", "Hyperactivity"),
    Impulsivity("impulsivity", "Impulsivity"),
    TimeBlindness("time_blindness", "Time-blindness"),
    EmotionalDysregulation("emotional_dysregulation", "Emotional dysregulation"),
    ExecutiveDysfunction("executive_dysfunction", "Executive dysfunction");

    companion object {
        fun fromKey(key: String): AdhdSymptom? = entries.firstOrNull { it.key == key }
    }
}

enum class TopGoal(val key: String, val label: String) {
    FocusBetter("focus_better", "Focus better"),
    FinishTasks("finish_tasks", "Finish what I start"),
    StartTasks("start_tasks", "Get started on tasks"),
    ManageTime("manage_time", "Manage my time"),
    ReduceStress("reduce_stress", "Reduce stress");

    companion object {
        fun fromKey(key: String): TopGoal? = entries.firstOrNull { it.key == key }
    }
}

enum class ProductiveTime(val key: String, val label: String) {
    Morning("morning", "Morning"),
    Afternoon("afternoon", "Afternoon"),
    Evening("evening", "Evening"),
    Night("night", "Night"),
    Varies("varies", "Varies");

    companion object {
        fun fromKey(key: String): ProductiveTime? = entries.firstOrNull { it.key == key }
    }
}

enum class MedicationStatus(val key: String, val label: String) {
    Yes("yes", "Yes"),
    No("no", "No"),
    PreferNotToSay("prefer_not_to_say", "Prefer not to say");

    companion object {
        fun fromKey(key: String): MedicationStatus? = entries.firstOrNull { it.key == key }
    }
}

enum class CopingStrategy(val key: String, val label: String) {
    Pomodoro("pomodoro", "Pomodoro"),
    Lists("lists", "To-do lists"),
    Alarms("alarms", "Alarms / reminders"),
    BodyDoubling("body_doubling", "Body doubling"),
    Music("music", "Music / soundscapes"),
    None("none", "None right now");

    companion object {
        fun fromKey(key: String): CopingStrategy? = entries.firstOrNull { it.key == key }
    }
}

enum class AiTone(val key: String, val label: String) {
    Gentle("gentle", "Gentle"),
    Direct("direct", "Direct"),
    Playful("playful", "Playful"),
    Professional("professional", "Professional");

    companion object {
        fun fromKey(key: String): AiTone? = entries.firstOrNull { it.key == key }
    }
}

val AGE_RANGES: List<String> = listOf("Under 18", "18–24", "25–34", "35–44", "45–54", "55+")
