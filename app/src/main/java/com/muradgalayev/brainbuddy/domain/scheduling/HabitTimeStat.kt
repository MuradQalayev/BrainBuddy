package com.muradgalayev.brainbuddy.domain.scheduling

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// every time in this package is minutes from local midnight
const val MINUTES_PER_DAY: Int = 24 * 60

// shortest distance between two times of day, wrap included: 23:50 and 00:10 are 20 minutes apart
fun circularDistance(a: Int, b: Int): Int {
    val raw = Math.floorMod(a - b, MINUTES_PER_DAY)
    return minOf(raw, MINUTES_PER_DAY - raw)
}

// wraps any minute value into 0 until MINUTES_PER_DAY
fun wrapMinutes(minutes: Int): Int = Math.floorMod(minutes, MINUTES_PER_DAY)

// signed shortest offset from `from` to `to`, in -720..720, negative meaning earlier in the day.
// used for nudging one time part-way toward another. vector-averaging is the obvious tool but
// goes numerically unstable as the two times approach opposite sides of the clock, where a
// hair's difference in weight flips the result by twelve hours. walking the short arc is stable
fun signedCircularDelta(to: Int, from: Int): Int {
    val forward = Math.floorMod(to - from, MINUTES_PER_DAY)
    return if (forward <= MINUTES_PER_DAY / 2) forward else forward - MINUTES_PER_DAY
}

// moves `from` a fraction of the way toward `to` along the shorter arc
fun nudgeToward(from: Int, to: Int, fraction: Double): Int =
    wrapMinutes(from + Math.round(signedCircularDelta(to, from) * fraction).toInt())

// what we've learned about when the user does one particular thing.
// times of day are angles, not numbers: someone who eats at 23:40 and 00:20 eats at midnight,
// but averaging 1420 and 20 arithmetically gives 12:00, the exact opposite time. so
// observations accumulate as unit vectors on a circle (sinSum, cosSum) and the mean comes back
// out of atan2. standard circular statistics, four doubles per habit.
// the sums are recency-weighted: every update decays what came before, so a dinner that
// drifted from 18:00 to 21:00 over a month converges on 21:00 instead of averaging to a time
// they never eat. see decayedTo().
// weightSum is the effective sample size, used to decide how far to trust this over the prior.
// durationWeightedSum / weightSum gives the mean duration. sampleCount is raw and never
// decayed, only for display. lastObservedAt is the anchor the decay is measured from
data class HabitTimeStat(
    val sinSum: Double = 0.0,
    val cosSum: Double = 0.0,
    val weightSum: Double = 0.0,
    val durationWeightedSum: Double = 0.0,
    val sampleCount: Int = 0,
    val lastObservedAt: Long = 0L,
) {
    // true once there is anything at all worth reading out of this
    val hasData: Boolean get() = weightSum > 1e-6

    // the habit's centre in minutes from midnight, or null when empty. the resultant vector can be
    // degenerate (two observations exactly 12h apart), and then there genuinely is no mean
    val meanMinutes: Int?
        get() {
            if (!hasData) return null
            if (hypot(sinSum, cosSum) < 1e-9) return null
            val angle = atan2(sinSum, cosSum)
            return wrapMinutes(Math.round(angle / (2 * PI) * MINUTES_PER_DAY).toInt())
        }

    // how tightly the observations cluster, 0f scattered across the whole day to 1f always the
    // same minute. this is the circular resultant length R
    val consistency: Float
        get() = if (!hasData) 0f else (hypot(sinSum, cosSum) / weightSum).coerceIn(0.0, 1.0).toFloat()

    // circular standard deviation in minutes, how wide a net to cast around meanMinutes. floored
    // at 15 minutes (nobody is that precise, and a zero-width peak would reject every candidate
    // slot) and capped at 4 hours, past which the habit is telling us nothing useful
    val spreadMinutes: Int
        get() {
            val r = consistency.toDouble()
            if (r <= 1e-6) return 240
            val radians = sqrt(-2.0 * ln(r.coerceAtMost(0.999999)))
            return (radians / (2 * PI) * MINUTES_PER_DAY).toInt().coerceIn(15, 240)
        }

    // mean observed duration in minutes, or null when nothing is recorded
    val meanDurationMinutes: Int?
        get() = if (!hasData) null else (durationWeightedSum / weightSum).toInt().coerceAtLeast(5)

    // folds one observation in. weight is the caller's confidence in it: a completed event is
    // stronger evidence than one merely typed in, and a suggestion the user overrode is stronger
    // still, because they went out of their way to say no
    fun observe(
        startMinutes: Int,
        durationMinutes: Int,
        atMillis: Long,
        weight: Double = 1.0,
        halfLifeDays: Double = DEFAULT_HALF_LIFE_DAYS,
    ): HabitTimeStat {
        val decayed = decayedTo(atMillis, halfLifeDays)
        val angle = wrapMinutes(startMinutes) / MINUTES_PER_DAY.toDouble() * 2 * PI
        return decayed.copy(
            sinSum = decayed.sinSum + sin(angle) * weight,
            cosSum = decayed.cosSum + cos(angle) * weight,
            weightSum = decayed.weightSum + weight,
            durationWeightedSum = decayed.durationWeightedSum + durationMinutes * weight,
            sampleCount = sampleCount + 1,
            lastObservedAt = maxOf(atMillis, lastObservedAt),
        )
    }

    // ages the accumulated evidence forward to nowMillis. applied on read as well as on write, so
    // a habit last touched six months ago can't out-vote one recorded last week just because it
    // happened to be written to the database first
    fun decayedTo(nowMillis: Long, halfLifeDays: Double = DEFAULT_HALF_LIFE_DAYS): HabitTimeStat {
        if (!hasData || lastObservedAt <= 0L || nowMillis <= lastObservedAt) return this
        val days = (nowMillis - lastObservedAt) / MILLIS_PER_DAY
        val factor = 0.5.pow(days / halfLifeDays)
        // below this the row is noise, so zero it out, stop it competing with the prior, and let it prune
        if (factor < 1e-4) return HabitTimeStat(sampleCount = sampleCount, lastObservedAt = lastObservedAt)
        return copy(
            sinSum = sinSum * factor,
            cosSum = cosSum * factor,
            weightSum = weightSum * factor,
            durationWeightedSum = durationWeightedSum * factor,
            lastObservedAt = nowMillis,
        )
    }

    companion object {
        // 45 days. long enough that a habit survives a holiday, short enough that a genuine change of
        // routine takes hold within about two months
        const val DEFAULT_HALF_LIFE_DAYS: Double = 45.0
        private const val MILLIS_PER_DAY: Double = 24 * 60 * 60 * 1000.0
    }
}

// merges a learned habit with the built-in prior for its ActivityKind. the blend is shrinkage:
// with no history the prior stands alone, with one or two observations it's nudged, and past
// roughly six the user's own pattern dominates. that gradient is the whole point, the feature
// has to be useful on day one and personal by month two, with no visible switch between them
data class TimingPrior(
    val peakMinutes: Int,
    val spreadMinutes: Int,
    val durationMinutes: Int,
    // 0f = entirely the built-in prior, 1f = entirely the user's own history
    val personalWeight: Float,
    val sampleCount: Int,
)

// stat is the user's history for this habit, already decayed to now. kind supplies the fallback
// peak, spread and duration. priorStrength is the effective sample size the built-in prior is
// worth, and higher means trust the textbook for longer
fun blendWithPrior(
    stat: HabitTimeStat?,
    kind: ActivityKind,
    priorStrength: Double = 3.0,
): TimingPrior {
    val mean = stat?.meanMinutes
    if (stat == null || mean == null || !stat.hasData) {
        return TimingPrior(
            peakMinutes = kind.priorPeak,
            spreadMinutes = kind.priorSpread,
            durationMinutes = kind.typicalDurationMinutes,
            personalWeight = 0f,
            sampleCount = 0,
        )
    }

    // weighted by precision, not just by count. two things decide how far the user's own history
    // should override the textbook: how much of it there is, and how tightly it clusters. counting
    // alone got this wrong in both directions, eight identical 16:00 appointments were still being
    // dragged ninety minutes toward a generic 10:00, while eight scattered across the day were
    // trusted just as much as eight identical ones.
    // a single observation has no measurable spread, so it would look infinitely precise. the floor
    // below starts it at the prior's own width and tightens as the square root of the evidence,
    // which is the standard way to stop one data point behaving like certainty
    val personalSpread = maxOf(
        stat.spreadMinutes.toDouble(),
        kind.priorSpread / sqrt(stat.weightSum.coerceAtLeast(1.0)),
    )
    val personalPrecision = stat.weightSum / (personalSpread * personalSpread)
    val priorPrecision = priorStrength / (kind.priorSpread.toDouble() * kind.priorSpread)
    val w = (personalPrecision / (personalPrecision + priorPrecision)).coerceIn(0.0, 1.0)

    // blend the two peaks as vectors too, the same midnight-wrap problem applies to combining
    // 23:00 with a 22:00 prior as it does to averaging observations
    val personalAngle = mean / MINUTES_PER_DAY.toDouble() * 2 * PI
    val priorAngle = kind.priorPeak / MINUTES_PER_DAY.toDouble() * 2 * PI
    val s = sin(personalAngle) * w + sin(priorAngle) * (1 - w)
    val c = cos(personalAngle) * w + cos(priorAngle) * (1 - w)
    val blendedPeak = wrapMinutes(
        Math.round(atan2(s, c) / (2 * PI) * MINUTES_PER_DAY).toInt()
    )

    val blendedSpread = (stat.spreadMinutes * w + kind.priorSpread * (1 - w)).toInt().coerceAtLeast(15)
    val blendedDuration = (
        (stat.meanDurationMinutes ?: kind.typicalDurationMinutes) * w +
            kind.typicalDurationMinutes * (1 - w)
        ).toInt().coerceAtLeast(5)

    return TimingPrior(
        peakMinutes = clampIntoWindow(blendedPeak, kind),
        spreadMinutes = blendedSpread,
        durationMinutes = blendedDuration,
        personalWeight = w.toFloat(),
        sampleCount = stat.sampleCount,
    )
}

// pulls a peak back inside its kind's plausible window. one mistyped 04:00 dinner shouldn't
// teach the app that dinner is a pre-dawn meal. history moves the peak within the window,
// the window itself is not negotiable
fun clampIntoWindow(minutes: Int, kind: ActivityKind): Int {
    if (minutes in kind.windowStart..kind.windowEnd) return minutes
    val toStart = circularDistance(minutes, kind.windowStart)
    val toEnd = circularDistance(minutes, kind.windowEnd)
    return if (toStart <= toEnd) kind.windowStart else kind.windowEnd
}

// gaussian fit of a candidate time against a peak, wrapping at midnight
fun timeFit(candidateMinutes: Int, peakMinutes: Int, spreadMinutes: Int): Double {
    val d = circularDistance(candidateMinutes, peakMinutes).toDouble()
    val s = spreadMinutes.coerceAtLeast(1).toDouble()
    return exp(-0.5 * (d / s) * (d / s))
}
