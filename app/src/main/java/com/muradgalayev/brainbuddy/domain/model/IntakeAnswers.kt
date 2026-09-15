package com.muradgalayev.brainbuddy.domain.model

import androidx.annotation.StringRes
import com.muradgalayev.brainbuddy.R

// the intake questions added from the user-profile intake proposal (N1-N18). they live here
// rather than in AdhdProfile because they roughly triple the number of enums the profile
// carries, and grouping them by what they measure beats one long file.
// every one is stored by its key, never by ordinal or label. labels are user-facing copy and
// will change, keys are in Supabase rows the moment the first person answers, and renaming one
// silently discards their answer. fromKey returning null on an unrecognised value is
// deliberate: an older or newer build writing something we don't know should degrade to
// unanswered, not crash

// N2, which ADHD presentation, when formally diagnosed
enum class AdhdPresentation(val key: String, val label: String, @StringRes val labelRes: Int) {
    Inattentive("inattentive", "Inattentive", R.string.intake_presentation_inattentive),
    HyperactiveImpulsive("hyperactive_impulsive", "Hyperactive-impulsive", R.string.intake_presentation_hyperactive),
    Combined("combined", "Combined", R.string.intake_presentation_combined),
    NotSure("not_sure", "Not sure / prefer not to say", R.string.intake_not_sure_prefer_not);

    companion object {
        fun fromKey(key: String): AdhdPresentation? = entries.firstOrNull { it.key == key }
    }
}

// N3, conditions that commonly co-occur with ADHD. a design input, not a diagnosis: it changes
// what helpful looks like, quieter defaults for sensory sensitivity, lower-pressure
// gamification for anxiety
enum class CoOccurringCondition(val key: String, val label: String, @StringRes val labelRes: Int) {
    Anxiety("anxiety", "Anxiety", R.string.intake_cond_anxiety),
    Depression("depression", "Depression", R.string.intake_cond_depression),
    Autism("autism", "Autism", R.string.intake_cond_autism),
    SensorySensitivities("sensory_sensitivities", "Sensory sensitivities", R.string.intake_cond_sensory),
    LearningDisability("learning_disability", "Learning disability", R.string.intake_cond_learning),
    NoneOfThese("none", "None of these", R.string.intake_none_of_these),
    PreferNotToSay("prefer_not_to_say", "Prefer not to say", R.string.intake_prefer_not_to_say);

    companion object {
        fun fromKey(key: String): CoOccurringCondition? = entries.firstOrNull { it.key == key }

        // answers that mean nothing to record, and can't be combined with others
        val EXCLUSIVE = setOf(NoneOfThese, PreferNotToSay)
    }
}

// N5, chronotype as a stated label. deliberately separate from ProductiveTime: that one asks
// which block of the day focus arrives in, this asks what the person is. they usually agree,
// and where they don't the disagreement is itself informative, since someone who calls
// themselves a night owl but reports peak focus at 9am is describing when they have to work
enum class Chronotype(val key: String, val label: String, @StringRes val labelRes: Int) {
    DefinitelyMorning("definitely_morning", "Definitely a morning person", R.string.intake_chrono_def_morning),
    SomewhatMorning("somewhat_morning", "Somewhat a morning person", R.string.intake_chrono_some_morning),
    SomewhatNight("somewhat_night", "Somewhat a night owl", R.string.intake_chrono_some_night),
    DefinitelyNight("definitely_night", "Definitely a night owl", R.string.intake_chrono_def_night),
    Varies("varies", "Varies a lot week to week", R.string.intake_chrono_varies);

    companion object {
        fun fromKey(key: String): Chronotype? = entries.firstOrNull { it.key == key }
    }
}

// N6, is the current sleep schedule chosen or imposed? without this the app would optimise
// around a forced, unhealthy schedule as if it were a preference. delayed sleep phase is
// common in ADHD, and 'you always go to bed at 2am' is a very different fact depending on it
enum class SleepScheduleOrigin(val key: String, val label: String, @StringRes val labelRes: Int) {
    Chosen("chosen", "It's what I'd choose naturally", R.string.intake_sleep_chosen),
    Forced("forced", "It's mostly forced by circumstances", R.string.intake_sleep_forced),
    Both("both", "A bit of both", R.string.intake_sleep_both);

    companion object {
        fun fromKey(key: String): SleepScheduleOrigin? = entries.firstOrNull { it.key == key }
    }
}

// N7, losing the thread after an interruption. working memory
enum class InterruptionRecall(val key: String, val label: String, @StringRes val labelRes: Int) {
    Never("never", "Never", R.string.intake_never),
    Rarely("rarely", "Rarely", R.string.intake_rarely),
    Sometimes("sometimes", "Sometimes", R.string.intake_sometimes),
    Often("often", "Often", R.string.intake_often);

    companion object {
        fun fromKey(key: String): InterruptionRecall? = entries.firstOrNull { it.key == key }
    }
}

// N8, how urgently things have to be written down before they evaporate. decides whether fast
// capture belongs front and centre or further down
enum class CaptureNeed(val key: String, val label: String, @StringRes val labelRes: Int) {
    Always("always", "Yes, always", R.string.intake_capture_always),
    Often("often", "Often", R.string.intake_often),
    Sometimes("sometimes", "Sometimes", R.string.intake_sometimes),
    Rarely("rarely", "Rarely / no", R.string.intake_capture_rarely);

    companion object {
        fun fromKey(key: String): CaptureNeed? = entries.firstOrNull { it.key == key }
    }
}

// N9, the cost of an unexpected change of plan. cognitive flexibility, and nothing in the
// survey measured task-switching cost before this. it should decide how much buffer the app
// leaves around scheduled items
enum class PlanChangeImpact(val key: String, val label: String, @StringRes val labelRes: Int) {
    Barely("barely", "Barely bothers me", R.string.intake_plan_barely),
    AdjustQuickly("adjust_quickly", "Mildly frustrating, I adjust quickly", R.string.intake_plan_adjust),
    NeedTimeToReset("need_reset", "Very disruptive, I need time to reset", R.string.intake_plan_reset),
    DerailsTheDay("derails_day", "I often can't get back on track the same day", R.string.intake_plan_derails);

    companion object {
        fun fromKey(key: String): PlanChangeImpact? = entries.firstOrNull { it.key == key }
    }
}

// N10, effort required to resume an interrupted task. pairs with InterruptionRecall and
// separates two problems that look alike: forgetting what you were doing needs a resume-state
// feature, struggling to re-enter it needs a transition ritual
enum class TaskReturnEffort(val key: String, val label: String, @StringRes val labelRes: Int) {
    Easily("easily", "Easily", R.string.intake_return_easily),
    SomeEffort("some_effort", "With some effort", R.string.intake_return_some),
    LotOfEffort("lot_of_effort", "With a lot of effort", R.string.intake_return_lot),
    UsuallyAbandon("usually_abandon", "I usually forget or abandon it", R.string.intake_return_abandon);

    companion object {
        fun fromKey(key: String): TaskReturnEffort? = entries.firstOrNull { it.key == key }
    }
}

// N11, where impulse control actually bites. a single severity slider says nothing about what
// to build: phone-checking points at focus mode, spending points at a different feature area
enum class ImpulseArea(val key: String, val label: String, @StringRes val labelRes: Int) {
    Interrupting("interrupting", "Interrupting others", R.string.intake_impulse_interrupting),
    Spending("spending", "Impulsive spending", R.string.intake_impulse_spending),
    Eating("eating", "Impulsive eating / snacking", R.string.intake_impulse_eating),
    PhoneChecking("phone_checking", "Checking phone / notifications", R.string.intake_impulse_phone),
    Blurting("blurting", "Blurting out thoughts", R.string.intake_impulse_blurting),
    NoneStandOut("none", "None of these stand out", R.string.intake_impulse_none);

    companion object {
        fun fromKey(key: String): ImpulseArea? = entries.firstOrNull { it.key == key }
        val EXCLUSIVE = setOf(NoneStandOut)
    }
}

// N12, how reminders should sound. separate from AiTone, which is how the assistant converses.
// someone can want a playful chat partner and a firm deadline-shaped reminder, and collapsing
// the two would force one to misrepresent the other
enum class NudgeTone(val key: String, val label: String, @StringRes val labelRes: Int) {
    GentleEncouraging("gentle", "Gentle and encouraging", R.string.intake_nudge_gentle),
    DirectBrief("direct", "Direct and brief", R.string.intake_nudge_direct),
    FirmDeadline("firm", "Firm, almost like a deadline", R.string.intake_nudge_firm),
    CaseByCase("case_by_case", "Let me choose case by case", R.string.intake_nudge_case);

    companion object {
        fun fromKey(key: String): NudgeTone? = entries.firstOrNull { it.key == key }
    }
}

// N13, the ceiling on check-ins, not a target. an upper bound the app must not exceed even
// where behaviour suggests more nudging would help. notification fatigue is the commonest
// complaint in ADHD-app reviews, and a user who set a limit and watched it be overridden has
// been given a reason to uninstall
enum class CheckInCeiling(val key: String, val label: String, @StringRes val labelRes: Int) {
    Rare("rare", "Rarely — a couple of times a day at most", R.string.intake_ceiling_rare),
    Moderate("moderate", "A moderate amount, as needed", R.string.intake_ceiling_moderate),
    Frequent("frequent", "Frequent check-ins help me stay on track", R.string.intake_ceiling_frequent);

    companion object {
        fun fromKey(key: String): CheckInCeiling? = entries.firstOrNull { it.key == key }
    }
}

// N14, what should happen when something is missed. directly about rejection-sensitive
// dysphoria: 'you missed 3 days' mechanics motivate some people and actively harm others, and
// the app has no way to tell which without asking
enum class MissedTaskResponse(val key: String, val label: String, @StringRes val labelRes: Int) {
    DontMention("dont_mention", "Don't mention it, just move on", R.string.intake_missed_dont_mention),
    NoteGently("note_gently", "Note it gently, no judgment", R.string.intake_missed_gently),
    ReMotivate("re_motivate", "Actively try to re-motivate me", R.string.intake_missed_remotivate);

    companion object {
        fun fromKey(key: String): MissedTaskResponse? = entries.firstOrNull { it.key == key }
    }
}

// N15, appetite for body-doubling or co-working. a roadmap signal
enum class BodyDoublingInterest(val key: String, val label: String, @StringRes val labelRes: Int) {
    Yes("yes", "Yes, definitely", R.string.intake_body_yes),
    Maybe("maybe", "Maybe, curious", R.string.intake_body_maybe),
    No("no", "No, I prefer working solo", R.string.intake_body_no);

    companion object {
        fun fromKey(key: String): BodyDoublingInterest? = entries.firstOrNull { it.key == key }
    }
}

// N17, where work actually happens, which sets the interruption budget
enum class WorkEnvironment(val key: String, val label: String, @StringRes val labelRes: Int) {
    QuietDedicated("quiet_dedicated", "A quiet, dedicated space", R.string.intake_env_quiet),
    SharedNoisy("shared_noisy", "A shared or noisy space", R.string.intake_env_shared),
    Varies("varies", "It varies a lot", R.string.intake_env_varies),
    Public("public", "Public spaces (cafés, libraries…)", R.string.intake_env_public);

    companion object {
        fun fromKey(key: String): WorkEnvironment? = entries.firstOrNull { it.key == key }
    }
}

// N18, what has already been tried. exists so the app stops recommending things that have
// already failed for this person, which is a reliable way to lose them
enum class PastStrategy(val key: String, val label: String, @StringRes val labelRes: Int) {
    Planners("planners", "Planners or planning apps", R.string.intake_past_planners),
    OtherAdhdApps("other_adhd_apps", "Other ADHD-focused apps", R.string.intake_past_other_apps),
    TherapyOrCoaching("therapy_coaching", "Therapy or coaching", R.string.intake_past_therapy),
    MedicationOnly("medication_only", "Medication only, no other tools", R.string.intake_past_medication),
    BodyDoubling("body_doubling", "Body doubling", R.string.intake_body_doubling),
    NothingYet("nothing_yet", "Nothing formal yet", R.string.intake_past_nothing);

    companion object {
        fun fromKey(key: String): PastStrategy? = entries.firstOrNull { it.key == key }
        val EXCLUSIVE = setOf(NothingYet)
    }
}

// applies the 'this answer cancels the others' rule for multi-selects. picking 'none of these'
// alongside 'anxiety' is not a state the user meant to be in, and letting them reach it
// produces answers no downstream code can read
fun <T> toggleWithExclusives(current: List<T>, value: T, exclusives: Set<T>): List<T> = when {
    value in current -> current - value
    value in exclusives -> listOf(value)
    else -> (current - exclusives) + value
}
