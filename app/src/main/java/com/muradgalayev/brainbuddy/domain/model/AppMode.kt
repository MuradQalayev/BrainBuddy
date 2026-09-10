package com.muradgalayev.brainbuddy.domain.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// a named bundle of settings the app switches to as a whole: Work, Weekend, or anything the
// user builds. the rule everything else depends on is that a mode is an overlay, never a
// write. ModeOverrides is sparse, every field nullable, and null means leave the user's own
// setting alone. activating a mode changes what the app reads, not what's stored, so leaving
// one restores everything by construction. writing into preferences would work exactly once:
// the first switch would overwrite the real settings and leave nothing to go back to
@Serializable
data class AppMode(
    val id: String,
    val name: String,
    // key into the icon table in the UI layer, so the model stays free of Compose types
    val icon: String = ICON_DEFAULT,
    // key into the accent table. null keeps the app's own theme
    val accent: String? = null,
    // built-ins can be edited and disabled but not deleted, so the picker can never end up empty
    // and someone who mangles Work gets it back by editing rather than by finding a reset button
    val isBuiltIn: Boolean = false,
    val schedule: ModeSchedule? = null,
    val overrides: ModeOverrides = ModeOverrides(),
    val sortIndex: Int = 0,
) {
    companion object {
        const val ICON_DEFAULT = "mode"
        const val ID_WORK = "work"
        const val ID_WEEKEND = "weekend"
    }
}

// when a mode turns itself on. optional, and the reason it exists: a mode you have to
// remember to switch on is a mode you forget. days uses DayOfWeek.value (Monday = 1), and an
// endMinute before startMinute means the window crosses midnight, since a 22:00-06:00 rest
// window is a normal thing to want
@Serializable
data class ModeSchedule(
    val days: Set<Int> = emptySet(),
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val enabled: Boolean = true,
) {
    val crossesMidnight: Boolean get() = endMinute <= startMinute

    fun covers(day: DayOfWeek, time: LocalTime): Boolean {
        if (!enabled || days.isEmpty()) return false
        val minute = time.hour * 60 + time.minute
        return if (crossesMidnight) {
            // the tail after midnight belongs to the previous day's window
            (day.value in days && minute >= startMinute) ||
                (day.minus(1).value in days && minute < endMinute)
        } else {
            day.value in days && minute >= startMinute && minute < endMinute
        }
    }

    companion object {
        val WEEKDAYS = setOf(1, 2, 3, 4, 5)
        val WEEKEND = setOf(6, 7)
    }
}

// what the phone does about sound while a mode is on
enum class RingerSetting { NORMAL, VIBRATE, SILENT }

// Myndora's own notifications, each independently silenceable by a mode
enum class ModeNotificationKind {
    TODO_REMINDERS,
    CALENDAR_REMINDERS,
    MEDICATION_REMINDERS,
    DAILY_SUMMARY,
    POMODORO_NUDGES,
    BREAK_REMINDERS,
}

// everything a mode may change. null means don't touch it. deliberately absent: anything the
// user consented to. AI wellness personalisation and Together sharing scopes are decisions,
// not settings, and a mode granting or revoking one silently would make the consent record
// we keep untrue
@Serializable
data class ModeOverrides(
    // sound and interruption
    val ringer: RingerSetting? = null,
    // arm the Do Not Disturb zen rule for as long as the mode is active
    val doNotDisturb: Boolean? = null,
    // removed: per-mode 'allow starred contacts' and 'allow repeat callers'. both are fixed on
    // now whenever a mode arms DND, which is what Android's own zen defaults do. they were two
    // more decisions in a screen that already had too many. old saved modes may still carry the
    // keys, ignoreUnknownKeys drops them
    // null inherits, an empty set silences all of Myndora's own
    val allowedNotifications: Set<ModeNotificationKind>? = null,

    // app surface
    val simplifiedWorkspace: Boolean? = null,
    val reduceMotion: Boolean? = null,
    val enabledNavItems: Set<String>? = null,
    val hiddenHomeWidgets: Set<String>? = null,

    // pomodoro and planning
    val pomodoroFocusMinutes: Int? = null,
    val pomodoroBreakMinutes: Int? = null,
    val autoDndOnFocusSession: Boolean? = null,
    // removed: per-mode calendar sync frequency. cadence is a property of the account, not of
    // what the user happens to be doing, and a mode that silently changed how often the calendar
    // refreshed was a setting nobody could see the effect of. the global one in Settings stays
    // bounds the time-suggestion engine, so a weekend mode stops proposing 09:00 Monday slots
    val workingHoursStartMinute: Int? = null,
    val workingHoursEndMinute: Int? = null,
) {
    // true when the mode would change nothing, used to warn before saving an empty one
    val isEmpty: Boolean
        get() = ringer == null && doNotDisturb == null && allowedNotifications == null &&
            simplifiedWorkspace == null && reduceMotion == null && enabledNavItems == null &&
            hiddenHomeWidgets == null && pomodoroFocusMinutes == null &&
            pomodoroBreakMinutes == null && autoDndOnFocusSession == null &&
            workingHoursStartMinute == null && workingHoursEndMinute == null
}

// the two the app ships with. they override little on purpose: a preset that changes fifteen
// things is one nobody trusts to switch on, and every override here is something the name
// already implies
object BuiltInModes {

    val work = AppMode(
        id = AppMode.ID_WORK,
        name = "Work",
        icon = "work",
        accent = "focus",
        isBuiltIn = true,
        sortIndex = 0,
        schedule = ModeSchedule(days = ModeSchedule.WEEKDAYS, startMinute = 9 * 60, endMinute = 17 * 60),
        overrides = ModeOverrides(
            ringer = RingerSetting.VIBRATE,
            doNotDisturb = true,
            // medication still gets through, a mode that silences a dose reminder causes harm not quiet
            allowedNotifications = setOf(
                ModeNotificationKind.CALENDAR_REMINDERS,
                ModeNotificationKind.MEDICATION_REMINDERS,
                ModeNotificationKind.POMODORO_NUDGES,
                ModeNotificationKind.BREAK_REMINDERS,
            ),
            autoDndOnFocusSession = true,
            workingHoursStartMinute = 9 * 60,
            workingHoursEndMinute = 17 * 60,
        ),
    )

    val weekend = AppMode(
        id = AppMode.ID_WEEKEND,
        name = "Weekend",
        icon = "weekend",
        accent = "calm",
        isBuiltIn = true,
        sortIndex = 1,
        // 24:00 is a valid exclusive end. 23:59 would leave the last minute of Saturday and Sunday
        // outside a mode described as all-day
        schedule = ModeSchedule(days = ModeSchedule.WEEKEND, startMinute = 0, endMinute = 24 * 60),
        overrides = ModeOverrides(
            ringer = RingerSetting.NORMAL,
            doNotDisturb = false,
            simplifiedWorkspace = true,
            allowedNotifications = setOf(
                ModeNotificationKind.MEDICATION_REMINDERS,
                ModeNotificationKind.CALENDAR_REMINDERS,
            ),
            workingHoursStartMinute = 10 * 60,
            workingHoursEndMinute = 20 * 60,
        ),
    )

    val all: List<AppMode> = listOf(work, weekend)
}

// the mode choice is three-state on purpose. Automatic lets schedules choose, NoMode suspends
// every schedule, Manual keeps one selected until the user asks for something else. null
// can't mean both 'follow schedules' and 'turn modes off', and that ambiguity made the old
// No mode button appear to work and then immediately reactivate a scheduled mode
sealed interface ModeSelection {
    data object Automatic : ModeSelection
    data object NoMode : ModeSelection

    data class Manual(val modeId: String) : ModeSelection {
        init {
            require(modeId.isNotBlank()) { "A manually selected mode needs an id" }
            require(modeId != NO_MODE_STORAGE_VALUE) { "Reserved mode id" }
        }
    }

    companion object {
        // kept in the existing active_mode_id preference so old stored ids stay valid and an absent
        // preference keeps its historical 'automatic' meaning
        const val NO_MODE_STORAGE_VALUE = "__mode_selection_none_v1__"

        fun fromStoredValue(value: String?): ModeSelection = when {
            value == null || value.isBlank() -> Automatic
            value == NO_MODE_STORAGE_VALUE -> NoMode
            else -> Manual(value)
        }
    }

    fun toStoredValue(): String? = when (this) {
        Automatic -> null
        NoMode -> NO_MODE_STORAGE_VALUE
        is Manual -> modeId
    }
}

// treats a manual choice deleted elsewhere as automatic, without weakening explicit No mode
fun ModeSelection.withAvailableModes(modes: List<AppMode>): ModeSelection = when (this) {
    is ModeSelection.Manual -> if (modes.any { it.id == modeId }) this else ModeSelection.Automatic
    ModeSelection.Automatic, ModeSelection.NoMode -> this
}

// which mode is in force, given the clock. a manual pick always wins: someone who chose a
// mode meant it, and a schedule overriding them minutes later would make the picker feel
// broken. NoMode wins by producing no mode at all. only Automatic consults the clock, and
// the first covering mode in sort order wins so overlaps resolve predictably, not by row order
fun resolveActiveMode(
    modes: List<AppMode>,
    selection: ModeSelection,
    day: DayOfWeek,
    time: LocalTime,
): AppMode? {
    when (val availableSelection = selection.withAvailableModes(modes)) {
        ModeSelection.NoMode -> return null
        is ModeSelection.Manual -> {
            return modes.first { it.id == availableSelection.modeId }
        }
        ModeSelection.Automatic -> Unit
    }
    return modes.sortedBy { it.sortIndex }.firstOrNull { it.schedule?.covers(day, time) == true }
}

// source-compatible bridge for callers written before the explicit three-state choice
fun resolveActiveMode(
    modes: List<AppMode>,
    manualModeId: String?,
    day: DayOfWeek,
    time: LocalTime,
): AppMode? = resolveActiveMode(
    modes = modes,
    selection = manualModeId?.let(ModeSelection::Manual) ?: ModeSelection.Automatic,
    day = day,
    time = time,
)

// the first instant after `after` at which any enabled schedule starts or ends. a pure
// calculation on purpose: ModeManager can sleep once until this instead of polling the clock,
// and the awkward overnight and week-wrap cases stay unit-testable
fun nextModeScheduleBoundary(
    modes: List<AppMode>,
    after: LocalDateTime,
): LocalDateTime? = scheduleBoundaryLocalDateTimes(modes, after.toLocalDate())
    .filter { it.isAfter(after) }
    .minOrNull()

// zoned counterpart for the live scheduler. schedule times are wall-clock, but the sleep
// until one has to be measured on the instant timeline: a local 01:30-03:30 span is one real
// hour when DST starts in Europe/Rome and three when it ends, and subtracting two
// LocalDateTimes would say two in both cases.
// ambiguous times during the autumn overlap are emitted once per valid offset, and a time
// inside the spring gap resolves to the transition instant. the offset transition is a
// wake-up candidate too, since a repeated or skipped wall-clock range can change which
// schedule covers without ever reaching an explicit start or end
fun nextModeScheduleBoundary(
    modes: List<AppMode>,
    after: ZonedDateTime,
): ZonedDateTime? {
    val zone = after.zone
    val afterInstant = after.toInstant()
    val candidates = scheduleBoundaryLocalDateTimes(modes, after.toLocalDate())
        .flatMap { it.atScheduleBoundaries(zone) }
        .filter { it.toInstant().isAfter(afterInstant) }
        .toMutableList()

    if (modes.any(AppMode::hasUsableSchedule)) {
        zone.rules.nextTransition(afterInstant)?.instant
            ?.takeIf { transition -> transition.isAfter(afterInstant) }
            ?.atZone(zone)
            ?.let(candidates::add)
    }

    return candidates.minByOrNull(ZonedDateTime::toInstant)
}

private fun scheduleBoundaryLocalDateTimes(
    modes: List<AppMode>,
    anchorDate: LocalDate,
): List<LocalDateTime> = buildList {
    // yesterday is needed for the end of an overnight window, and seven days ahead for when
    // today's only boundary has passed and its next occurrence is next week
    for (dayOffset in -1L..7L) {
        val startDate = anchorDate.plusDays(dayOffset)
        for (mode in modes) {
            val schedule = mode.schedule ?: continue
            if (!schedule.enabled || startDate.dayOfWeek.value !in schedule.days) continue
            if (schedule.startMinute !in 0 until MINUTES_PER_DAY ||
                schedule.endMinute !in 0..MINUTES_PER_DAY
            ) continue

            val start = startDate.atTime(schedule.startMinute.asLocalTime())
            val end = when {
                schedule.endMinute == MINUTES_PER_DAY -> startDate.plusDays(1).atStartOfDay()
                schedule.crossesMidnight ->
                    startDate.plusDays(1).atTime(schedule.endMinute.asLocalTime())
                else -> startDate.atTime(schedule.endMinute.asLocalTime())
            }
            add(start)
            add(end)
        }
    }
}

private fun AppMode.hasUsableSchedule(): Boolean = schedule?.let {
    it.enabled && it.days.isNotEmpty() &&
        it.startMinute in 0 until MINUTES_PER_DAY && it.endMinute in 0..MINUTES_PER_DAY
} == true

private fun LocalDateTime.atScheduleBoundaries(zone: ZoneId): List<ZonedDateTime> {
    val validOffsets = zone.rules.getValidOffsets(this)
    if (validOffsets.isNotEmpty()) {
        // two offsets means the wall time repeats during the autumn overlap. both crossings matter
        // to a resolver whose source of truth is local day plus local time
        return validOffsets.map { offset -> ZonedDateTime.ofLocal(this, zone, offset) }
    }

    // no valid offset means this local time was skipped by a spring-forward gap. collapse every
    // boundary in the gap onto the transition instant rather than shifting 02:30 to 03:30, which
    // would leave a 02:30-starting mode half an hour late
    val transitionInstant: Instant = zone.rules.getTransition(this)?.instant
        ?: return listOf(atZone(zone))
    return listOf(transitionInstant.atZone(zone))
}

private const val MINUTES_PER_DAY = 24 * 60

private fun Int.asLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
