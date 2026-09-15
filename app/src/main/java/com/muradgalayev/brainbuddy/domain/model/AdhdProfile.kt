package com.muradgalayev.brainbuddy.domain.model

import androidx.annotation.StringRes
import com.muradgalayev.brainbuddy.R

// the user's ADHD self-profile, one row per user in Supabase. designed as a single bag the
// assistant can fetch in one call and prepend as context to every request
data class AdhdProfile(
    val userId: String,
    val ageRange: String = "",
    val diagnosisStatus: DiagnosisStatus? = null,
    val primarySymptoms: List<AdhdSymptom> = emptyList(),
    // N16, up to MAX_TOP_GOALS. was a single goal until the intake revision, see primaryGoal for
    // the one-value view older code still reads
    val topGoals: List<TopGoal> = emptyList(),

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

    // intake revision (N1-N18). every one is nullable or empty by default: they arrived after
    // people had already completed the survey, and an existing user has to read as 'hasn't
    // answered this yet' rather than being assigned a default they never chose

    // N2, only meaningful when diagnosisStatus is Diagnosed
    val presentation: AdhdPresentation? = null,
    /** N3 */
    val coOccurring: List<CoOccurringCondition> = emptyList(),
    /** N5 */
    val chronotype: Chronotype? = null,
    /** N6 */
    val sleepScheduleOrigin: SleepScheduleOrigin? = null,
    /** N7 */
    val interruptionRecall: InterruptionRecall? = null,
    /** N8 */
    val captureNeed: CaptureNeed? = null,
    /** N9 */
    val planChangeImpact: PlanChangeImpact? = null,
    /** N10 */
    val taskReturnEffort: TaskReturnEffort? = null,
    /** N11 */
    val impulseAreas: List<ImpulseArea> = emptyList(),
    /** N12 */
    val nudgeTone: NudgeTone? = null,
    /** N13 */
    val checkInCeiling: CheckInCeiling? = null,
    /** N14 */
    val missedTaskResponse: MissedTaskResponse? = null,
    /** N15 */
    val bodyDoublingInterest: BodyDoublingInterest? = null,
    /** N17 */
    val workEnvironment: WorkEnvironment? = null,
    /** N18 */
    val pastStrategies: List<PastStrategy> = emptyList(),

    val surveyCompleted: Boolean = false,
    val surveyVersion: SurveyVersion = SurveyVersion.None,
) {
    // the single headline goal, for the places that can only show one: the AI system prompt line,
    // the profile summary card. null when nothing is chosen
    val primaryGoal: TopGoal? get() = topGoals.firstOrNull()

    companion object {
        // N16 caps at three. more than that isn't a priority, it's a wish list
        const val MAX_TOP_GOALS = 3
    }
}

enum class SurveyVersion(val raw: Int) {
    None(0), Quick(1), Deep(2);

    companion object {
        fun fromRaw(value: Int): SurveyVersion =
            entries.firstOrNull { it.raw == value } ?: None
    }
}

// N1. drives how confidently the app may speak ('your ADHD' for a diagnosed user, 'what you're
// describing' for everyone else) and whether suggesting professional support is useful or
// patronising. Exploring is retired from the picker but kept in the enum: it is already
// stored against real accounts, and dropping it would blank their answer on the next read
enum class DiagnosisStatus(
    val key: String,
    val label: String,
    @StringRes val labelRes: Int,
    // false for values kept only so existing rows still parse
    val selectable: Boolean = true,
) {
    Diagnosed("diagnosed", "Yes, formally diagnosed", R.string.intake_diag_yes),
    SelfIdentified("self_identified", "I strongly suspect it but haven't been diagnosed", R.string.intake_diag_suspect),
    ProfessionalMentioned(
        "professional_mentioned",
        "A professional mentioned it but I haven't pursued it", R.string.intake_diag_professional,
    ),
    GeneralSupport("general_support", "No — I'm here for general focus/organisation support", R.string.intake_diag_general),
    Exploring("exploring", "Still exploring", R.string.intake_diag_exploring, selectable = false);

    // true where the app may name ADHD as a fact about this person
    val isConfirmed: Boolean get() = this == Diagnosed

    companion object {
        fun fromKey(key: String): DiagnosisStatus? = entries.firstOrNull { it.key == key }

        // what the survey offers, in order
        val OPTIONS: List<DiagnosisStatus> = entries.filter { it.selectable }
    }
}

enum class AdhdSymptom(val key: String, val label: String, @StringRes val labelRes: Int) {
    Inattention("inattention", "Inattention", R.string.intake_sym_inattention),
    Hyperactivity("hyperactivity", "Hyperactivity", R.string.intake_sym_hyperactivity),
    Impulsivity("impulsivity", "Impulsivity", R.string.intake_sym_impulsivity),
    TimeBlindness("time_blindness", "Time-blindness", R.string.intake_sym_time_blindness),
    EmotionalDysregulation("emotional_dysregulation", "Emotional dysregulation", R.string.intake_sym_emotional),
    ExecutiveDysfunction("executive_dysfunction", "Executive dysfunction", R.string.intake_sym_executive);

    companion object {
        fun fromKey(key: String): AdhdSymptom? = entries.firstOrNull { it.key == key }
    }
}

// N16. listed in the order the intake proposal gives, running from the most concrete (starting
// tasks) to the most diffuse (feeling less overwhelmed), because people pick more accurately
// when the specific options come first. FocusBetter isn't in the proposal's list but predates
// it and is already chosen by existing users, so it stays
enum class TopGoal(val key: String, val label: String, @StringRes val labelRes: Int) {
    StartTasks("start_tasks", "Starting tasks", R.string.intake_goal_start),
    FinishTasks("finish_tasks", "Finishing what I start", R.string.intake_goal_finish),
    RememberThings("remember_things", "Remembering things", R.string.intake_goal_remember),
    ManageTime("manage_time", "Managing my time", R.string.intake_goal_time),
    FocusBetter("focus_better", "Focusing better", R.string.intake_goal_focus),
    ReduceStress("reduce_stress", "Staying calm under stress", R.string.intake_goal_stress),
    LessImpulsive("less_impulsive", "Being less impulsive", R.string.intake_goal_impulsive),
    SleepBetter("sleep_better", "Sleeping better", R.string.intake_goal_sleep),
    LessOverwhelmed("less_overwhelmed", "Feeling less overwhelmed generally", R.string.intake_goal_overwhelmed);

    companion object {
        fun fromKey(key: String): TopGoal? = entries.firstOrNull { it.key == key }
    }
}

enum class ProductiveTime(val key: String, val label: String, @StringRes val labelRes: Int) {
    Morning("morning", "Morning", R.string.intake_time_morning),
    Afternoon("afternoon", "Afternoon", R.string.intake_time_afternoon),
    Evening("evening", "Evening", R.string.intake_time_evening),
    Night("night", "Night", R.string.intake_time_night),
    Varies("varies", "Varies", R.string.intake_time_varies);

    companion object {
        fun fromKey(key: String): ProductiveTime? = entries.firstOrNull { it.key == key }
    }
}

enum class MedicationStatus(val key: String, val label: String, @StringRes val labelRes: Int) {
    Yes("yes", "Yes", R.string.common_yes),
    No("no", "No", R.string.common_no),
    PreferNotToSay("prefer_not_to_say", "Prefer not to say", R.string.intake_prefer_not_to_say);

    companion object {
        fun fromKey(key: String): MedicationStatus? = entries.firstOrNull { it.key == key }
    }
}

enum class CopingStrategy(val key: String, val label: String, @StringRes val labelRes: Int) {
    Pomodoro("pomodoro", "Pomodoro", R.string.intake_coping_pomodoro),
    Lists("lists", "To-do lists", R.string.intake_coping_lists),
    Alarms("alarms", "Alarms / reminders", R.string.intake_coping_alarms),
    BodyDoubling("body_doubling", "Body doubling", R.string.intake_body_doubling),
    Music("music", "Music / soundscapes", R.string.intake_coping_music),
    None("none", "None right now", R.string.intake_coping_none);

    companion object {
        fun fromKey(key: String): CopingStrategy? = entries.firstOrNull { it.key == key }
    }
}

enum class AiTone(val key: String, val label: String, @StringRes val labelRes: Int) {
    Gentle("gentle", "Gentle", R.string.intake_tone_gentle),
    Direct("direct", "Direct", R.string.intake_tone_direct),
    Playful("playful", "Playful", R.string.intake_tone_playful),
    Professional("professional", "Professional", R.string.intake_tone_professional);

    companion object {
        fun fromKey(key: String): AiTone? = entries.firstOrNull { it.key == key }
    }
}

val AGE_RANGES: List<String> = listOf("Under 18", "18–24", "25–34", "35–44", "45–54", "55+")
