package com.muradgalayev.brainbuddy.ui.home

import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class AgendaKind { Event, Task }

// one thing on today, whichever list it came from. start is null for a task with no time on
// it: those are real work but can't be counted down to or drawn on a timeline, so everything
// downstream has to handle them
data class HomeAgendaItem(
    val id: String,
    val title: String,
    val kind: AgendaKind,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val completed: Boolean,
    val colorKey: String,
    // blank for tasks, and for events with no place on them
    val location: String = "",
)

// where the day ribbon begins and ends. anything outside is clamped onto the edges
val RibbonStart: LocalTime = LocalTime.of(6, 0)
val RibbonEnd: LocalTime = LocalTime.MIDNIGHT.minusMinutes(1)

fun CalendarEvent.toAgendaItem(): HomeAgendaItem? {
    val start = runCatching { LocalDateTime.parse(startTime) }.getOrNull() ?: return null
    return HomeAgendaItem(
        id = id,
        title = title,
        kind = AgendaKind.Event,
        start = start,
        end = runCatching { LocalDateTime.parse(endTime) }.getOrNull(),
        completed = completed,
        colorKey = color,
        location = location,
    )
}

fun TodoItem.toAgendaItem(day: LocalDate): HomeAgendaItem {
    val start = startTime.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?.let { day.atTime(it) }
    val end = endTime.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?.let { day.atTime(it) }
    return HomeAgendaItem(
        id = id,
        title = title,
        kind = AgendaKind.Task,
        start = start,
        end = end,
        completed = isCompleted,
        colorKey = color,
    )
}

// the one thing to put in front of the user. anything already running wins, 'you are in this
// right now' beats what comes after it. otherwise the next start, and only if the day holds
// nothing timed does an untimed task get the slot. something recently missed still shows,
// because the item you blew past twenty minutes ago is exactly the one worth surfacing; older
// than that and it's nagging, so it drops back into the list
fun selectNextUp(items: List<HomeAgendaItem>, now: LocalDateTime): HomeAgendaItem? {
    val open = items.filterNot { it.completed }
    val timed = open.filter { it.start != null }

    val running = timed.filter { item ->
        val start = item.start ?: return@filter false
        val end = item.end ?: return@filter false
        !start.isAfter(now) && end.isAfter(now)
    }.minByOrNull { it.start!! }
    if (running != null) return running

    val upcoming = timed.filter { it.start!!.isAfter(now) }.minByOrNull { it.start!! }
    if (upcoming != null) return upcoming

    val justMissed = timed.filter { item ->
        val reference = item.end ?: item.start!!
        !reference.isAfter(now) && Duration.between(reference, now).toMinutes() <= LATE_GRACE_MINUTES
    }.maxByOrNull { it.start!! }
    if (justMissed != null) return justMissed

    return open.firstOrNull { it.start == null }
}

private const val LATE_GRACE_MINUTES = 45L

// 'in 40 min', 'Happening now', '15 min late', the countdown line on the Next up card
fun countdownLabel(item: HomeAgendaItem, now: LocalDateTime): String {
    val start = item.start ?: return "Anytime today"
    val end = item.end

    if (!start.isAfter(now) && end != null && end.isAfter(now)) {
        val left = Duration.between(now, end).toMinutes()
        return if (left < 1) "Wrapping up" else "${humanDuration(left)} left"
    }
    if (start.isAfter(now)) {
        val until = Duration.between(now, start).toMinutes()
        return if (until < 1) "Starting now" else "in ${humanDuration(until)}"
    }
    val late = Duration.between(end ?: start, now).toMinutes()
    return if (late < 1) "Now" else "${humanDuration(late)} late"
}

// '14:00-14:30', or just the start for something with no end of its own
fun timeRangeLabel(item: HomeAgendaItem): String {
    val start = item.start ?: return "Anytime"
    val startLabel = start.toLocalTime().format(ClockFormat)
    val end = item.end?.takeIf { it.isAfter(start) } ?: return startLabel
    return "$startLabel–${end.toLocalTime().format(ClockFormat)}"
}

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// minutes as something readable at a glance: '45 min', '2 h', '1 h 20'
fun humanDuration(totalMinutes: Long): String {
    if (totalMinutes < 60) return "$totalMinutes min"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0L) "$hours h" else "$hours h $minutes"
}

// how many rows the ribbon splits into where things collide. two, and not negotiable by the
// data: the track is 12dp, so three lanes would be 3dp bars and stop reading as objects. a
// third concurrent item shares the lower lane rather than making everything thinner, since
// the ribbon's job is 'roughly how full is this stretch' and it still answers that
const val RibbonMaxLanes = 2

// an item with no end of its own still has to occupy something on the bar
private const val MinBlockMinutes = 30L

// one drawn bar on the ribbon. lanes is how many rows this block's collision cluster needs,
// not the ribbon's: a double-booked lunch splits only lunch, and a lone afternoon meeting
// keeps the full height
data class RibbonBlock(
    val id: String,
    val from: Float,
    val to: Float,
    val colorKey: String,
    val isTask: Boolean,
    val completed: Boolean,
    val lane: Int,
    val lanes: Int,
)

// lays today's items out on the bar, splitting into lanes only across the stretches where
// they genuinely overlap in time. overlap is measured on the real times rather than on the
// drawn rectangles: blocks get a minimum drawn width so a 15-minute event stays visible, and
// letting that padding create fake collisions would report double-bookings that aren't there
fun ribbonBlocks(agenda: List<HomeAgendaItem>, today: LocalDate): List<RibbonBlock> {
    val timed = agenda.mapNotNull { item ->
        val start = item.start?.takeIf { it.toLocalDate() == today } ?: return@mapNotNull null
        val end = item.end?.takeIf { it.isAfter(start) } ?: start.plusMinutes(MinBlockMinutes)
        Triple(item, start, end)
    }.sortedWith(compareBy({ it.second }, { it.third }))

    val blocks = mutableListOf<RibbonBlock>()
    val laneEnds = mutableListOf<LocalDateTime>()
    var clusterStart = 0
    var clusterEnd: LocalDateTime? = null

    // a cluster is closed once nothing can reach back into it, and only then is its lane count
    // known, so the whole cluster is rewritten with it
    fun closeCluster() {
        val lanes = laneEnds.size.coerceAtLeast(1)
        for (index in clusterStart until blocks.size) {
            blocks[index] = blocks[index].copy(lanes = lanes)
        }
        laneEnds.clear()
        clusterStart = blocks.size
        clusterEnd = null
    }

    timed.forEach { (item, start, end) ->
        if (clusterEnd?.isAfter(start) == false) closeCluster()

        val free = laneEnds.indexOfFirst { !it.isAfter(start) }
        val lane = when {
            free >= 0 -> free
            laneEnds.size < RibbonMaxLanes -> laneEnds.also { it.add(end) }.lastIndex
            // out of lanes: share the lower one rather than shrink the whole cluster
            else -> RibbonMaxLanes - 1
        }
        laneEnds[lane] = maxOf(laneEnds[lane], end)
        clusterEnd = maxOf(clusterEnd ?: end, end)

        blocks += RibbonBlock(
            id = item.id,
            from = ribbonFraction(start.toLocalTime()),
            to = ribbonFraction(end.toLocalTime()),
            colorKey = item.colorKey,
            isTask = item.kind == AgendaKind.Task,
            completed = item.completed,
            lane = lane,
            lanes = 1,
        )
    }
    closeCluster()
    return blocks
}

// what a tap at `fraction` along the bar selects. horizontal only. a lane is about 5dp tall
// where things overlap, which is not a target anyone can aim at, so a tap in a collided
// stretch takes the earliest item, tapping again takes the next, and the tap after the last
// clears. tolerance lets a near-miss still land, because a 15-minute event is a few dp wide
fun pickRibbonBlock(
    blocks: List<RibbonBlock>,
    fraction: Float,
    tolerance: Float,
    current: String?,
): RibbonBlock? {
    val hits = blocks.filter { fraction >= it.from - tolerance && fraction <= it.to + tolerance }
    if (hits.isEmpty()) return null
    val index = hits.indexOfFirst { it.id == current }
    if (index >= 0 && index == hits.lastIndex) return null
    return hits[index + 1]
}

// where a moment sits along the ribbon, 0f at RibbonStart and 1f at RibbonEnd. times outside
// the window clamp to the ends rather than falling off the card
fun ribbonFraction(time: LocalTime): Float {
    val span = (RibbonEnd.toSecondOfDay() - RibbonStart.toSecondOfDay()).toFloat()
    val offset = (time.toSecondOfDay() - RibbonStart.toSecondOfDay()).toFloat()
    return (offset / span).coerceIn(0f, 1f)
}
