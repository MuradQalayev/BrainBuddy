package com.muradgalayev.brainbuddy.ui.home

import com.muradgalayev.brainbuddy.ui.utils.resolve
import com.muradgalayev.brainbuddy.testing.TestStrings
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class HomeAgendaTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 12)
    private val now: LocalDateTime = today.atTime(14, 0)

    private fun item(
        id: String,
        start: LocalTime?,
        end: LocalTime? = null,
        kind: AgendaKind = AgendaKind.Event,
        completed: Boolean = false,
    ) = HomeAgendaItem(
        id = id,
        title = id,
        kind = kind,
        start = start?.let { today.atTime(it) },
        end = end?.let { today.atTime(it) },
        completed = completed,
        colorKey = "sky",
    )

    @Test
    fun `something running beats something starting later`() {
        val items = listOf(
            item("running", LocalTime.of(13, 30), LocalTime.of(15, 0)),
            item("later", LocalTime.of(16, 0)),
        )
        assertEquals("running", selectNextUp(items, now)?.id)
    }

    @Test
    fun `otherwise the earliest upcoming item wins`() {
        val items = listOf(
            item("evening", LocalTime.of(19, 0)),
            item("soon", LocalTime.of(14, 30)),
        )
        assertEquals("soon", selectNextUp(items, now)?.id)
    }

    @Test
    fun `completed items are never suggested`() {
        val items = listOf(
            item("done", LocalTime.of(14, 30), completed = true),
            item("open", LocalTime.of(18, 0)),
        )
        assertEquals("open", selectNextUp(items, now)?.id)
    }

    @Test
    fun `a recently missed item still surfaces`() {
        val items = listOf(item("missed", LocalTime.of(13, 40)))
        assertEquals("missed", selectNextUp(items, now)?.id)
    }

    @Test
    fun `something missed hours ago drops out`() {
        val items = listOf(item("this morning", LocalTime.of(9, 0)))
        assertNull(selectNextUp(items, now))
    }

    @Test
    fun `an untimed task fills the slot when nothing is scheduled`() {
        val items = listOf(item("someday", null, kind = AgendaKind.Task))
        assertEquals("someday", selectNextUp(items, now)?.id)
    }

    @Test
    fun `an untimed task never outranks a timed one`() {
        val items = listOf(
            item("untimed", null, kind = AgendaKind.Task),
            item("timed", LocalTime.of(17, 0)),
        )
        assertEquals("timed", selectNextUp(items, now)?.id)
    }

    @Test
    fun `countdown reads ahead, during and after`() {
        assertEquals("in 45 min", countdownLabel(item("a", LocalTime.of(14, 45)), now).resolve(TestStrings.en))
        assertEquals("in 3 h 30", countdownLabel(item("b", LocalTime.of(17, 30)), now).resolve(TestStrings.en))
        assertEquals("30 min left", countdownLabel(item("c", LocalTime.of(13, 30), LocalTime.of(14, 30)), now).resolve(TestStrings.en))
        assertEquals("20 min late", countdownLabel(item("d", LocalTime.of(13, 40)), now).resolve(TestStrings.en))
        assertEquals("Anytime today", countdownLabel(item("e", null, kind = AgendaKind.Task), now).resolve(TestStrings.en))
    }

    @Test
    fun `durations read as hours once they pass sixty minutes`() {
        assertEquals("59 min", humanDuration(59).resolve(TestStrings.en))
        assertEquals("1 h", humanDuration(60).resolve(TestStrings.en))
        assertEquals("1 h 5", humanDuration(65).resolve(TestStrings.en))
        assertEquals("2 h", humanDuration(120).resolve(TestStrings.en))
    }

    @Test
    fun `the ribbon spans six in the morning to midnight`() {
        assertEquals(0f, ribbonFraction(LocalTime.of(6, 0)), 0.001f)
        assertEquals(0.5f, ribbonFraction(LocalTime.of(15, 0)), 0.01f)
        assertEquals(1f, ribbonFraction(LocalTime.of(23, 59)), 0.001f)
    }

    @Test
    fun `the small hours clamp to the left edge, not the right`() {
        // 00:30 is before the window opens, so it belongs at the start of the bar: the marker parking
        // on the far right would read as 'the day is over'
        assertEquals(0f, ribbonFraction(LocalTime.of(3, 0)), 0.001f)
        assertEquals(0f, ribbonFraction(LocalTime.MIDNIGHT), 0.001f)
        assertEquals(0f, ribbonFraction(LocalTime.of(0, 30)), 0.001f)
    }

    @Test
    fun `a task without a time becomes an untimed agenda item`() {
        val todo = TodoItem(
            id = "t1", title = "Post letter", description = "", isCompleted = false,
            date = today.toString(), startTime = "", endTime = "",
            priority = "MEDIUM", attendees = 0, color = "blue", category = "personal",
        )
        val agenda = todo.toAgendaItem(today)
        assertNull(agenda.start)
        assertEquals(AgendaKind.Task, agenda.kind)
    }

    @Test
    fun `a task with a time lands on today at that time`() {
        val todo = TodoItem(
            id = "t2", title = "Call", description = "", isCompleted = false,
            date = today.toString(), startTime = "16:30", endTime = "",
            priority = "MEDIUM", attendees = 0, color = "blue", category = "personal",
        )
        assertEquals(today.atTime(16, 30), todo.toAgendaItem(today).start)
    }
}
