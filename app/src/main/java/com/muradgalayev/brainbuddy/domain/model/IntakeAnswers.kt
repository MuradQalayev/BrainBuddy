package com.muradgalayev.brainbuddy.domain.model

// the intake questions added from the user-profile intake proposal (N1-N18). they live here
// rather than in AdhdProfile because they roughly triple the number of enums the profile
// carries, and grouping them by what they measure beats one long file.
// every one is stored by its key, never by ordinal or label. labels are user-facing copy and
// will change, keys are in Supabase rows the moment the first person answers, and renaming one
// silently discards their answer. fromKey returning null on an unrecognised value is
// deliberate: an older or newer build writing something we don't know should degrade to
// unanswered, not crash

// N2, which ADHD presentation, when formally diagnosed
enum class AdhdPresentation(val key: String, val label: String) {
    Inattentive("inattentive", "Inattentive"),
    HyperactiveImpulsive("hyperactive_impulsive", "Hyperactive-impulsive"),
    Combined("combined", "Combined"),
    NotSure("not_sure", "Not sure / prefer not to say");

    companion object {
        fun fromKey(key: String): AdhdPresentation? = entries.firstOrNull { it.key == key }
    }
}

// N3, conditions that commonly co-occur with ADHD. a design input, not a diagnosis: it changes
// what helpful looks like, quieter defaults for sensory sensitivity, lower-pressure
// gamification for anxiety
enum class CoOccurringCondition(val key: String, val label: String) {
    Anxiety("anxiety", "Anxiety"),
    Depression("depression", "Depression"),
    Autism("autism", "Autism"),
    SensorySensitivities("sensory_sensitivities", "Sensory sensitivities"),
    LearningDisability("learning_disability", "Learning disability"),
    NoneOfThese("none", "None of these"),
    PreferNotToSay("prefer_not_to_say", "Prefer not to say");

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
enum class Chronotype(val key: String, val label: String) {
    DefinitelyMorning("definitely_morning", "Definitely a morning person"),
    SomewhatMorning("somewhat_morning", "Somewhat a morning person"),
    SomewhatNight("somewhat_night", "Somewhat a night owl"),
    DefinitelyNight("definitely_night", "Definitely a night owl"),
    Varies("varies", "Varies a lot week to week");

    companion object {
        fun fromKey(key: String): Chronotype? = entries.firstOrNull { it.key == key }
    }
}

// N6, is the current sleep schedule chosen or imposed? without this the app would optimise
// around a forced, unhealthy schedule as if it were a preference. delayed sleep phase is
// common in ADHD, and 'you always go to bed at 2am' is a very different fact depending on it
enum class SleepScheduleOrigin(val key: String, val label: String) {
    Chosen("chosen", "It's what I'd choose naturally"),
    Forced("forced", "It's mostly forced by circumstances"),
    Both("both", "A bit of both");

    companion object {
        fun fromKey(key: String): SleepScheduleOrigin? = entries.firstOrNull { it.key == key }
    }
}

// N7, losing the thread after an interruption. working memory
enum class InterruptionRecall(val key: String, val label: String) {
    Never("never", "Never"),
    Rarely("rarely", "Rarely"),
    Sometimes("sometimes", "Sometimes"),
    Often("often", "Often");

    companion object {
        fun fromKey(key: String): InterruptionRecall? = entries.firstOrNull { it.key == key }
    }
}

// N8, how urgently things have to be written down before they evaporate. decides whether fast
// capture belongs front and centre or further down
enum class CaptureNeed(val key: String, val label: String) {
    Always("always", "Yes, always"),
    Often("often", "Often"),
    Sometimes("sometimes", "Sometimes"),
    Rarely("rarely", "Rarely / no");

    companion object {
        fun fromKey(key: String): CaptureNeed? = entries.firstOrNull { it.key == key }
    }
}

// N9, the cost of an unexpected change of plan. cognitive flexibility, and nothing in the
// survey measured task-switching cost before this. it should decide how much buffer the app
// leaves around scheduled items
enum class PlanChangeImpact(val key: String, val label: String) {
    Barely("barely", "Barely bothers me"),
    AdjustQuickly("adjust_quickly", "Mildly frustrating, I adjust quickly"),
    NeedTimeToReset("need_reset", "Very disruptive, I need time to reset"),
    DerailsTheDay("derails_day", "I often can't get back on track the same day");

    companion object {
        fun fromKey(key: String): PlanChangeImpact? = entries.firstOrNull { it.key == key }
    }
}

// N10, effort required to resume an interrupted task. pairs with InterruptionRecall and
// separates two problems that look alike: forgetting what you were doing needs a resume-state
// feature, struggling to re-enter it needs a transition ritual
enum class TaskReturnEffort(val key: String, val label: String) {
    Easily("easily", "Easily"),
    SomeEffort("some_effort", "With some effort"),
    LotOfEffort("lot_of_effort", "With a lot of effort"),
    UsuallyAbandon("usually_abandon", "I usually forget or abandon it");

    companion object {
        fun fromKey(key: String): TaskReturnEffort? = entries.firstOrNull { it.key == key }
    }
}

// N11, where impulse control actually bites. a single severity slider says nothing about what
// to build: phone-checking points at focus mode, spending points at a different feature area
enum class ImpulseArea(val key: String, val label: String) {
    Interrupting("interrupting", "Interrupting others"),
    Spending("spending", "Impulsive spending"),
    Eating("eating", "Impulsive eating / snacking"),
    PhoneChecking("phone_checking", "Checking phone / notifications"),
    Blurting("blurting", "Blurting out thoughts"),
    NoneStandOut("none", "None of these stand out");

    companion object {
        fun fromKey(key: String): ImpulseArea? = entries.firstOrNull { it.key == key }
        val EXCLUSIVE = setOf(NoneStandOut)
    }
}

// N12, how reminders should sound. separate from AiTone, which is how the assistant converses.
// someone can want a playful chat partner and a firm deadline-shaped reminder, and collapsing
// the two would force one to misrepresent the other
enum class NudgeTone(val key: String, val label: String) {
    GentleEncouraging("gentle", "Gentle and encouraging"),
    DirectBrief("direct", "Direct and brief"),
    FirmDeadline("firm", "Firm, almost like a deadline"),
    CaseByCase("case_by_case", "Let me choose case by case");

    companion object {
        fun fromKey(key: String): NudgeTone? = entries.firstOrNull { it.key == key }
    }
}

// N13, the ceiling on check-ins, not a target. an upper bound the app must not exceed even
// where behaviour suggests more nudging would help. notification fatigue is the commonest
// complaint in ADHD-app reviews, and a user who set a limit and watched it be overridden has
// been given a reason to uninstall
enum class CheckInCeiling(val key: String, val label: String) {
    Rare("rare", "Rarely — a couple of times a day at most"),
    Moderate("moderate", "A moderate amount, as needed"),
    Frequent("frequent", "Frequent check-ins help me stay on track");

    companion object {
        fun fromKey(key: String): CheckInCeiling? = entries.firstOrNull { it.key == key }
    }
}

// N14, what should happen when something is missed. directly about rejection-sensitive
// dysphoria: 'you missed 3 days' mechanics motivate some people and actively harm others, and
// the app has no way to tell which without asking
enum class MissedTaskResponse(val key: String, val label: String) {
    DontMention("dont_mention", "Don't mention it, just move on"),
    NoteGently("note_gently", "Note it gently, no judgment"),
    ReMotivate("re_motivate", "Actively try to re-motivate me");

    companion object {
        fun fromKey(key: String): MissedTaskResponse? = entries.firstOrNull { it.key == key }
    }
}

// N15, appetite for body-doubling or co-working. a roadmap signal
enum class BodyDoublingInterest(val key: String, val label: String) {
    Yes("yes", "Yes, definitely"),
    Maybe("maybe", "Maybe, curious"),
    No("no", "No, I prefer working solo");

    companion object {
        fun fromKey(key: String): BodyDoublingInterest? = entries.firstOrNull { it.key == key }
    }
}

// N17, where work actually happens, which sets the interruption budget
enum class WorkEnvironment(val key: String, val label: String) {
    QuietDedicated("quiet_dedicated", "A quiet, dedicated space"),
    SharedNoisy("shared_noisy", "A shared or noisy space"),
    Varies("varies", "It varies a lot"),
    Public("public", "Public spaces (cafés, libraries…)");

    companion object {
        fun fromKey(key: String): WorkEnvironment? = entries.firstOrNull { it.key == key }
    }
}

// N18, what has already been tried. exists so the app stops recommending things that have
// already failed for this person, which is a reliable way to lose them
enum class PastStrategy(val key: String, val label: String) {
    Planners("planners", "Planners or planning apps"),
    OtherAdhdApps("other_adhd_apps", "Other ADHD-focused apps"),
    TherapyOrCoaching("therapy_coaching", "Therapy or coaching"),
    MedicationOnly("medication_only", "Medication only, no other tools"),
    BodyDoubling("body_doubling", "Body doubling"),
    NothingYet("nothing_yet", "Nothing formal yet");

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
