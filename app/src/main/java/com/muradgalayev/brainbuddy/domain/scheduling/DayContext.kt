package com.muradgalayev.brainbuddy.domain.scheduling

import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

// a chunk of the day that's already taken.
// opaque ones come from a connection's shared busy hours: they block, but we can't name them
data class BusyInterval(
    val startMinutes: Int,
    val endMinutes: Int,
    val title: String,
    val opaque: Boolean = false,
) {
    // zero-length or inverted events would break the scorer
    val isValid: Boolean get() = endMinutes > startMinutes

    companion object {
        // placeholder for blocks we only know the hours of
        const val SHARED_LABEL: String = "Busy"
    }
}

// bed and wake are kept separate on purpose: wake is a hard floor,
// bed is soft, the wind-down hour still allows easy things
data class SleepWindow(
    val bedMinutes: Int,
    val wakeMinutes: Int,
) {
    // minutes before bed that count as wind-down
    val windDownStart: Int get() = wrapMinutes(bedMinutes - WIND_DOWN_MINUTES)

    // handles the wrap past midnight
    fun isAsleep(minutes: Int): Boolean {
        val m = wrapMinutes(minutes)
        return if (bedMinutes <= wakeMinutes) {
            // someone sleeping 02:00-10:00 has bed < wake
            m in bedMinutes until wakeMinutes
        } else {
            m >= bedMinutes || m < wakeMinutes
        }
    }

    // 0f before wind-down starts, 1f at bedtime
    fun windDownPressure(minutes: Int): Float {
        if (isAsleep(minutes)) return 1f
        val toBed = Math.floorMod(bedMinutes - minutes, MINUTES_PER_DAY)
        if (toBed > WIND_DOWN_MINUTES) return 0f
        return 1f - (toBed.toFloat() / WIND_DOWN_MINUTES)
    }

    companion object {
        const val WIND_DOWN_MINUTES: Int = 90

        // fallback for a skipped survey
        val DEFAULT = SleepWindow(bedMinutes = 23 * 60, wakeMinutes = 7 * 60)

        // the survey is skippable, so each side falls back on its own
        fun parse(bedtime: String?, wakeTime: String?): SleepWindow = SleepWindow(
            bedMinutes = parseHhMm(bedtime) ?: DEFAULT.bedMinutes,
            wakeMinutes = parseHhMm(wakeTime) ?: DEFAULT.wakeMinutes,
        )

        private fun parseHhMm(raw: String?): Int? {
            val parts = raw?.trim()?.split(':') ?: return null
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
    }
}

// how much the user has left in the tank, from Health Connect.
// a score, never a filter: being tired makes demanding things rank worse, it never removes a slot
data class EnergySignals(
    val stepsToday: Long? = null,
    // baseline that today gets judged against
    val stepsDailyAverage: Long? = null,
    val lastSleepHours: Double? = null,
    // the user's own normal, not 8 hours
    val sleepBaselineHours: Double? = null,
) {
    // 0f fresh to 1f empty, null when we have nothing to go on.
    // dayProgress matters: 3000 steps by 09:00 is a brisk morning, by 21:00 it's a slow day
    fun fatigue(dayProgress: Float): Float? {
        val sleepDeficit = sleepDeficit()
        val exertion = exertion(dayProgress)
        return when {
            sleepDeficit != null && exertion != null -> (0.6f * sleepDeficit + 0.4f * exertion)
            sleepDeficit != null -> sleepDeficit * 0.8f
            exertion != null -> exertion * 0.7f
            else -> null
        }?.coerceIn(0f, 1f)
    }

    // floored at a 7h target
    private fun sleepDeficit(): Float? {
        val last = lastSleepHours ?: return null
        // 0 hours means the watch wasn't worn, not a sleepless night
        if (last <= 0.5) return null
        val target = (sleepBaselineHours ?: 7.5).coerceAtLeast(7.0)
        return (((target - last) / target).coerceIn(0.0, 1.0)).toFloat()
    }

    // how far past a normal day's activity we already are
    private fun exertion(dayProgress: Float): Float? {
        val today = stepsToday ?: return null
        val average = stepsDailyAverage?.takeIf { it > 500 } ?: return null
        val expectedByNow = average * dayProgress.coerceIn(0.05f, 1f)
        if (expectedByNow < 1f) return null
        val ratio = today / expectedByNow
        // 20% over pace is still a normal day, saturates around 2.5x
        return (((ratio - 1.2) / 1.3).coerceIn(0.0, 1.0)).toFloat()
    }
}

// the caller assembles this so the engine stays pure
data class DayContext(
    val date: LocalDate,
    val now: LocalDateTime,
    val busy: List<BusyInterval>,
    val sleep: SleepWindow,
    val productiveTime: ProductiveTime?,
    val energy: EnergySignals?,
    // end is exclusive: 09:00-17:00 allows a 1h event at 16:00, not 16:30
    val workingHours: WorkingHours? = null,
) {
    val isToday: Boolean get() = date == now.toLocalDate()
    val isWeekend: Boolean
        get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    // routines differ on days off
    val dayType: DayType get() = if (isWeekend) DayType.Weekend else DayType.Weekday

    // on a future date this is just wake time, today it's now plus a bit of lead
    val earliestStart: Int
        get() {
            if (!isToday) return sleep.wakeMinutes
            val nowMinutes = now.hour * 60 + now.minute + LEAD_MINUTES
            val rounded = ((nowMinutes + SLOT_MINUTES - 1) / SLOT_MINUTES) * SLOT_MINUTES
            return maxOf(rounded, sleep.wakeMinutes)
        }

    // 1f for a past date, 0f for a future one
    val dayProgress: Float
        get() {
            if (!isToday) return if (date.isAfter(now.toLocalDate())) 0f else 1f
            val nowMinutes = now.hour * 60 + now.minute
            val awakeLength = Math.floorMod(sleep.bedMinutes - sleep.wakeMinutes, MINUTES_PER_DAY)
                .takeIf { it > 0 } ?: MINUTES_PER_DAY
            val elapsed = Math.floorMod(nowMinutes - sleep.wakeMinutes, MINUTES_PER_DAY)
            return (elapsed.toFloat() / awakeLength).coerceIn(0f, 1f)
        }

    // lazy so the scorer doesn't redo it for every candidate slot
    val fatigue: Float? by lazy { energy?.fatigue(dayProgress) }

    // a packed day shouldn't offer three near-identical leftovers
    val freeRatio: Float by lazy {
        val awakeLength = Math.floorMod(sleep.bedMinutes - sleep.wakeMinutes, MINUTES_PER_DAY)
            .takeIf { it > 0 } ?: MINUTES_PER_DAY
        val occupied = busy.filter { it.isValid }.sumOf { it.endMinutes - it.startMinutes }
        (1f - occupied.toFloat() / awakeLength).coerceIn(0f, 1f)
    }

    companion object {
        const val SLOT_MINUTES: Int = 15

        // don't propose something starting in four minutes
        const val LEAD_MINUTES: Int = 20
    }
}

// same-day window that constrains suggestions
data class WorkingHours(
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) {
            "Working-hours start must be within the day"
        }
        require(endMinute in 1..MINUTES_PER_DAY) {
            "Working-hours end must be within the day or exactly 24:00"
        }
        require(startMinute < endMinute) {
            "Working hours must end after they start"
        }
    }

    companion object {
        // mode data syncs from other clients, so bad bounds mean no override rather than a crash
        fun fromNullable(startMinute: Int?, endMinute: Int?): WorkingHours? {
            if (startMinute == null || endMinute == null) return null
            if (startMinute !in 0 until MINUTES_PER_DAY) return null
            if (endMinute !in 1..MINUTES_PER_DAY || startMinute >= endMinute) return null
            return WorkingHours(startMinute, endMinute)
        }
    }
}

enum class DayType(val key: String) {
    Any("any"), Weekday("weekday"), Weekend("weekend");

    companion object {
        fun fromKey(key: String): DayType = entries.firstOrNull { it.key == key } ?: Any
    }
}

// null means 'it varies'
fun chronotypePeak(productiveTime: ProductiveTime?): Int? = when (productiveTime) {
    ProductiveTime.Morning -> 10 * 60
    ProductiveTime.Afternoon -> 15 * 60
    ProductiveTime.Evening -> 19 * 60
    ProductiveTime.Night -> 22 * 60
    ProductiveTime.Varies, null -> null
}

// nudges the prior toward when the user says they work best.
// done at the prior level rather than as a score multiplier, otherwise the survey answer
// could only ever break ties and answering it would look like it did nothing.
// only applies to energy-dependent activities that are free to move, and it fades as real history builds up
fun ProductiveTime?.adjust(prior: TimingPrior, kind: ActivityKind): TimingPrior {
    val target = chronotypePeak(this) ?: return prior
    if (kind.anchored || kind.demand < 0.5f) return prior
    val strength = CHRONOTYPE_STRENGTH * kind.demand * (1f - prior.personalWeight)
    if (strength < 0.02f) return prior
    return prior.copy(
        peakMinutes = clampIntoWindow(
            nudgeToward(prior.peakMinutes, target, strength.toDouble()),
            kind,
        ),
    )
}

// under half on purpose, the activity keeps its own character
private const val CHRONOTYPE_STRENGTH = 0.45f

// gentle +/-15% on top of adjust(), centred on 1.0 so an unanswered survey changes nothing
fun circadianFit(minutes: Int, productiveTime: ProductiveTime?, demand: Float): Double {
    val peak = chronotypePeak(productiveTime) ?: return 1.0
    if (demand < 0.5f) return 1.0
    val fit = timeFit(minutes, peak, spreadMinutes = 210)
    val amplitude = 0.15 * demand
    return 1.0 - amplitude + 2 * amplitude * fit
}

// nearest 5 minutes
fun roundDuration(minutes: Double, floor: Int = 15): Int =
    ((minutes / 5.0).roundToInt() * 5).coerceAtLeast(floor)
