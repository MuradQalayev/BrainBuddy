package com.muradgalayev.brainbuddy.domain.scheduling

// an interface so the engine stays pure and the tests don't need Room
fun interface HabitStatLookup {
    // already decayed to now by the caller, null when the habit is unknown
    fun stat(habitKey: String, dayType: DayType): HabitTimeStat?
}

// picks when to put a new event, from the user's history, their sleep survey, what's already
// on the day, and whatever Health Connect says they have left. no model here and no need for one:
// a lexicon, a circular prior, hard constraints and a scoring order, all exact and offline.
// returns one suggestion when the day is tight, up to three when it's genuinely open
object TimeSuggestionEngine {

    // any closer than this and two slots read as one
    private const val MIN_SEPARATION_MINUTES = 60

    // a runner-up has to be this close to the best to be worth showing
    private const val RELATIVE_CUTOFF = 0.55

    // ...and this good on its own, so we don't pad the list
    private const val ABSOLUTE_CUTOFF = 0.18

    private const val MAX_SUGGESTIONS = 3

    // breathing room either side of a suggestion
    private const val BUFFER_MINUTES = 10

    // meals and catch-ups running a bit past bedtime is normal
    private const val BEDTIME_GRACE_MINUTES = 30

    // how much better another day has to be before we suggest moving the event.
    // wide on purpose, most events belong on the day the user picked
    private const val CROSS_DAY_MARGIN = 1.35

    // suggests across days, offering a later one when it clearly beats the day being edited.
    // the single-day pass can only shuffle things around inside a day they don't fit in.
    // laterDays must have energy = null, tomorrow's step count doesn't exist yet.
    // at most one cross-day suggestion, always last
    fun suggestAcrossDays(
        title: String,
        description: String,
        selectedDay: DayContext,
        laterDays: List<DayContext>,
        lookup: HabitStatLookup,
    ): List<TimeSuggestion> {
        val onSelectedDay = suggest(title, description, selectedDay, lookup)

        val alternative = laterDays
            .mapNotNull { suggest(title, description, it, lookup).firstOrNull() }
            .maxByOrNull { it.score }

        // nothing fits on the chosen day, so the other day is the answer, not an alternative
        if (onSelectedDay.isEmpty()) {
            return listOfNotNull(alternative?.asCrossDay(selectedDay))
        }
        if (alternative == null) return onSelectedDay
        if (alternative.score < onSelectedDay.first().score * CROSS_DAY_MARGIN) {
            return onSelectedDay
        }
        // comes out of the same budget so the strip never grows past three chips
        return onSelectedDay.take(MAX_SUGGESTIONS - 1) + alternative.asCrossDay(selectedDay)
    }

    // says what's wrong with the day they're looking at, not what's right about the other one
    private fun TimeSuggestion.asCrossDay(selectedDay: DayContext): TimeSuggestion {
        val fatigue = selectedDay.fatigue ?: 0f
        val text = when {
            fatigue >= 0.35f && kind.demand >= 0.5f -> "You'll have more in the tank"
            selectedDay.freeRatio < 0.2f -> "Today has no room for this"
            else -> "A clearer run at it than today"
        }
        return copy(reason = SuggestionReason.BetterAnotherDay, reasonText = text)
    }

    fun suggest(
        title: String,
        description: String,
        context: DayContext,
        lookup: HabitStatLookup,
    ): List<TimeSuggestion> {
        val classification = ActivityClassifier.classify(title, description)
        val kind = classification.kind

        val resolved = resolveStat(title, kind, context.dayType, lookup)
        val basePrior = if (resolved.isSpecific) {
            blendWithPrior(resolved.stat, kind)
        } else {
            pooledBaseline(resolved.stat, kind)
        }
        // history first, then the survey answer bends what's left of the generic prior.
        // the other way round would let onboarding overwrite what the user actually does
        val prior = context.productiveTime.adjust(basePrior, kind)
        val duration = adjustDuration(prior.durationMinutes, kind, context)

        val busy = context.busy.filter { it.isValid }.sortedBy { it.startMinutes }

        // first pass stays inside the activity's plausible window. if nothing fits there at all,
        // a second pass opens up the whole waking day: a late dinner beats no suggestion
        val scored = scoreCandidates(kind, prior, duration, context, busy, respectKindWindow = true)
            .ifEmpty {
                scoreCandidates(kind, prior, duration, context, busy, respectKindWindow = false)
            }
        if (scored.isEmpty()) return emptyList()

        return select(scored, context).map { it.toSuggestion(kind, prior, duration, context) }
    }

    // a learned row, plus whether it's about this activity or just the user's general baseline.
    // conflating the two would let us claim 'your usual time' about a title we've never seen
    private data class ResolvedStat(val stat: HabitTimeStat?, val isSpecific: Boolean)

    // picks which learned row to trust: this habit on this day type, then this habit on any day,
    // then the activity family. a day-type bucket needs real evidence first, or one Saturday gym
    // trip rewrites every weekday. titles we can't place fall through to the pooled baseline
    private fun resolveStat(
        title: String,
        kind: ActivityKind,
        dayType: DayType,
        lookup: HabitStatLookup,
    ): ResolvedStat {
        val titleKey = HabitKey.forTitle(title)
        val kindKey = HabitKey.forKind(kind)

        val candidates = buildList {
            if (titleKey != null) {
                add(lookup.stat(titleKey, dayType))
                add(lookup.stat(titleKey, DayType.Any))
            }
            // only worth consulting when the title was recognisable. everything unclassifiable lands in
            // General, and a General habit would just teach us that 'things' happen at 3pm
            if (kind != ActivityKind.General) {
                add(lookup.stat(kindKey, dayType))
                add(lookup.stat(kindKey, DayType.Any))
            }
        }

        // needs a couple of observations before it beats the any-day row behind it
        val specific = candidates.firstOrNull { it != null && it.weightSum >= 1.5 }
            ?: candidates.firstOrNull { it != null && it.hasData }
        if (specific != null) return ResolvedStat(specific, isSpecific = true)

        // a recognised kind has a real built-in prior, worth more than a scheduling average.
        // only genuinely unknown titles fall back
        if (kind != ActivityKind.General) return ResolvedStat(null, isSpecific = true)

        val pooled = lookup.stat(HabitKey.ALL_EVENTS, dayType)
            ?.takeIf { it.weightSum >= POOLED_MIN_WEIGHT }
            ?: lookup.stat(HabitKey.ALL_EVENTS, DayType.Any)
        // a handful of events is not a schedule. below this the baseline is noise with the user's
        // name on it, and the built-in prior is the better guess
        return ResolvedStat(pooled?.takeIf { it.weightSum >= POOLED_MIN_WEIGHT }, isSpecific = false)
    }

    // turns 'when does this person schedule anything' into a usable prior, held at arm's length.
    // the pooled row covers every event they've ever saved, so through a normal blend it would
    // dominate and, worse, pass itself off as a habit. hence the high prior strength, the floored
    // spread, and personalWeight forced to 0 so we never claim it's something they usually do
    private fun pooledBaseline(stat: HabitTimeStat?, kind: ActivityKind): TimingPrior {
        val blended = blendWithPrior(stat, kind, priorStrength = POOLED_PRIOR_STRENGTH)
        return blended.copy(
            spreadMinutes = maxOf(blended.spreadMinutes, POOLED_MIN_SPREAD_MINUTES),
            personalWeight = 0f,
            sampleCount = 0,
        )
    }

    // high, because the baseline averages unrelated activities and only earns its place in bulk
    private const val POOLED_PRIOR_STRENGTH = 25.0

    // two hours: the baseline points at a part of the day, not at a time
    private const val POOLED_MIN_SPREAD_MINUTES = 120

    // decayed evidence needed before we consult the pooled baseline at all
    private const val POOLED_MIN_WEIGHT = 8.0

    // shortens demanding things when the user is running down. a 90-minute focus block on four
    // hours of sleep won't happen. only ever shortens, never lengthens
    private fun adjustDuration(base: Int, kind: ActivityKind, context: DayContext): Int {
        val fatigue = context.fatigue ?: return base
        if (kind.demand < 0.5f) return base
        val shrink = 1.0 - 0.35 * fatigue * kind.demand
        return roundDuration(base * shrink, floor = 15).coerceAtMost(base)
    }

    // a candidate slot with its scoring broken out, so the reason is derivable
    private data class Scored(
        val start: Int,
        val duration: Int,
        val habitFit: Double,
        val circadian: Double,
        val energy: Double,
        val chainedTo: String?,
        val total: Double,
    )

    private fun scoreCandidates(
        kind: ActivityKind,
        prior: TimingPrior,
        duration: Int,
        context: DayContext,
        busy: List<BusyInterval>,
        respectKindWindow: Boolean,
    ): List<Scored> {
        val workingHours = context.workingHours
        val lowerBound = maxOf(
            context.earliestStart,
            if (respectKindWindow) kind.windowStart else 0,
            workingHours?.startMinute ?: 0,
        )
        val upperBound = minOf(
            if (respectKindWindow) kind.windowEnd else MINUTES_PER_DAY - 1,
            // the window end is exclusive and covers the whole event, not just its start:
            // a 60-minute suggestion in 09:00-17:00 can start no later than 16:00
            workingHours?.let { it.endMinute - duration } ?: (MINUTES_PER_DAY - 1),
        )

        val out = mutableListOf<Scored>()
        var start = ((lowerBound + DayContext.SLOT_MINUTES - 1) / DayContext.SLOT_MINUTES) *
            DayContext.SLOT_MINUTES
        while (start <= upperBound) {
            val end = start + duration
            if (fitsAwake(start, duration, context) && !overlapsBusy(start, end, busy)) {
                out += score(start, duration, kind, prior, context, busy)
            }
            start += DayContext.SLOT_MINUTES
        }
        return out
    }

    // has to sit inside the single waking stretch, bar a little grace
    private fun fitsAwake(start: Int, duration: Int, context: DayContext): Boolean {
        val sleep = context.sleep
        if (sleep.isAsleep(start)) return false
        val awakeLength = Math.floorMod(sleep.bedMinutes - sleep.wakeMinutes, MINUTES_PER_DAY)
            .takeIf { it > 0 } ?: return true
        val fromWake = Math.floorMod(start - sleep.wakeMinutes, MINUTES_PER_DAY)
        return fromWake + duration <= awakeLength + BEDTIME_GRACE_MINUTES
    }

    private fun overlapsBusy(start: Int, end: Int, busy: List<BusyInterval>): Boolean =
        busy.any { start < it.endMinutes + BUFFER_MINUTES && it.startMinutes - BUFFER_MINUTES < end }

    private fun score(
        start: Int,
        duration: Int,
        kind: ActivityKind,
        prior: TimingPrior,
        context: DayContext,
        busy: List<BusyInterval>,
    ): Scored {
        val habitFit = timeFit(start, prior.peakMinutes, prior.spreadMinutes)
        val circadian = circadianFit(start, context.productiveTime, kind.demand)
        val energy = energyFactor(start, duration, kind, context)

        // butting a new event up against an existing one keeps the day in blocks instead of confetti,
        // which matters more with ADHD than a slightly better time of day
        val previous = busy.lastOrNull {
            it.endMinutes <= start && start - it.endMinutes <= CHAIN_WINDOW_MINUTES
        }
        // a borrowed block still earns the chaining bonus but must not be named. this is the only
        // place a busy interval's title reaches the user, so it's the only place that has to hold it
        val chainedTo = previous?.takeUnless { it.opaque }?.title
        val next = busy.firstOrNull {
            it.startMinutes >= start + duration &&
                it.startMinutes - (start + duration) <= CHAIN_WINDOW_MINUTES
        }
        var contextFactor = 1.0
        if (previous != null) contextFactor *= 1.10
        if (next != null) contextFactor *= 1.05

        // leaving a five-minute orphan either side is worse than not fitting there
        previous?.let { if (start - it.endMinutes in 1 until ORPHAN_MINUTES) contextFactor *= 0.92 }
        next?.let {
            if (it.startMinutes - (start + duration) in 1 until ORPHAN_MINUTES) contextFactor *= 0.92
        }

        // people pick round times. 19:00 over 18:45 for a hair's difference in fit is what makes
        // the suggestion feel considered
        contextFactor *= when {
            start % 60 == 0 -> 1.06
            start % 30 == 0 -> 1.03
            else -> 1.0
        }

        return Scored(
            start = start,
            duration = duration,
            habitFit = habitFit,
            circadian = circadian,
            energy = energy,
            chainedTo = chainedTo,
            total = habitFit * circadian * energy * contextFactor,
        )
    }

    // tiredness and bedtime penalty, 0..1. two effects, both scaled by how demanding the activity
    // is: a flat cost to hard things when the body is spent, and a steeper one as the slot creeps
    // into wind-down. restful things are near-immune, so dinner at 21:00 isn't hit like a gym trip
    private fun energyFactor(
        start: Int,
        duration: Int,
        kind: ActivityKind,
        context: DayContext,
    ): Double {
        val demand = kind.demand.toDouble()
        val windDown = context.sleep.windDownPressure(start + duration).toDouble()
        var factor = 1.0 - 0.5 * demand * windDown

        val fatigue = context.fatigue?.toDouble()
        if (fatigue != null) {
            factor *= 1.0 - 0.45 * fatigue * demand
            factor *= 1.0 - 0.35 * fatigue * demand * windDown
        }
        return factor.coerceIn(0.05, 1.0)
    }

    // greedy non-maximum suppression, then a quality gate. suppression first, because the raw top
    // 3 of a 15-minute grid is always 19:00 / 19:15 / 18:45. the gate is where 'sometimes one,
    // sometimes three' comes from, rather than a fixed count
    private fun select(scored: List<Scored>, context: DayContext): List<Scored> {
        val ranked = scored.sortedByDescending { it.total }
        val picked = mutableListOf<Scored>()
        for (candidate in ranked) {
            if (picked.size >= MAX_SUGGESTIONS) break
            if (picked.any { circularDistance(it.start, candidate.start) < MIN_SEPARATION_MINUTES }) {
                continue
            }
            picked += candidate
        }
        if (picked.isEmpty()) return picked

        val best = picked.first().total
        // a packed day shouldn't scrape the barrel for a third option
        val cap = when {
            context.freeRatio < 0.25f -> 1
            context.freeRatio < 0.5f -> 2
            else -> MAX_SUGGESTIONS
        }
        return picked
            .filterIndexed { index, s ->
                index == 0 || (s.total >= best * RELATIVE_CUTOFF && s.total >= ABSOLUTE_CUTOFF)
            }
            .take(cap)
    }

    private fun Scored.toSuggestion(
        kind: ActivityKind,
        prior: TimingPrior,
        fullDuration: Int,
        context: DayContext,
    ): TimeSuggestion {
        val (reason, text) = explain(kind, prior, fullDuration, context)
        return TimeSuggestion(
            date = context.date,
            startMinutes = start,
            endMinutes = start + duration,
            kind = kind,
            reason = reason,
            reasonText = text,
            score = total,
        )
    }

    // picks the single most useful thing to say about a slot: their own history first,
    // then something about their day, then generic timing advice
    private fun Scored.explain(
        kind: ActivityKind,
        prior: TimingPrior,
        fullDuration: Int,
        context: DayContext,
    ): Pair<SuggestionReason, String> {
        val nearUsual = circularDistance(start, prior.peakMinutes) <=
            maxOf(30, prior.spreadMinutes / 2)
        val fatigue = context.fatigue ?: 0f

        return when {
            prior.personalWeight >= 0.4f && nearUsual -> SuggestionReason.UsualTime to
                if (prior.sampleCount >= 4) {
                    "Your usual ${kind.label} time"
                } else {
                    "Close to when you last did this"
                }

            chainedTo != null -> SuggestionReason.AfterEvent to "Right after ${chainedTo.trim()}"

            fatigue >= 0.35f && kind.demand >= 0.5f -> SuggestionReason.EnergyAware to
                if (duration < fullDuration) {
                    "Shorter session — today's been a heavy one"
                } else {
                    "Earlier, while you've still got energy"
                }

            circadian >= 1.04 -> SuggestionReason.FocusWindow to "Your best focus window"

            context.sleep.windDownPressure(start + duration) > 0.25f && kind.demand < 0.35f ->
                SuggestionReason.BeforeBed to
                    "Winds down before ${formatHhMm(context.sleep.bedMinutes)}"

            // above the generic advice: when a slot had to clear two calendars, that's what earned it.
            // only claimable when they actually had something on, otherwise 'free for you both' is an
            // inference dressed up as a fact
            context.busy.any { it.opaque } ->
                SuggestionReason.FreeForBoth to "Free for you both"

            prior.personalWeight < 0.4f && kind != ActivityKind.General ->
                SuggestionReason.TypicalForActivity to "Typical ${kind.label} time"

            else -> SuggestionReason.ClearGap to "The clearest gap in your day"
        }
    }

    // how close counts as back to back
    private const val CHAIN_WINDOW_MINUTES = 30

    // a gap smaller than this is dead time, not a break
    private const val ORPHAN_MINUTES = 20
}
