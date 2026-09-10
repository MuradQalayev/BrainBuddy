package com.muradgalayev.brainbuddy.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HomeRibbonTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 12)

    private fun item(
        id: String,
        start: LocalTime,
        end: LocalTime? = null,
        kind: AgendaKind = AgendaKind.Event,
    ) = HomeAgendaItem(
        id = id,
        title = id,
        kind = kind,
        start = today.atTime(start),
        end = end?.let { today.atTime(it) },
        completed = false,
        colorKey = "sky",
    )

    private fun blocks(vararg items: HomeAgendaItem) = ribbonBlocks(items.toList(), today)

    @Test
    fun `a day without collisions stays one full-height lane`() {
        val result = blocks(
            item("morning", LocalTime.of(9, 0), LocalTime.of(10, 0)),
            item("afternoon", LocalTime.of(14, 0), LocalTime.of(15, 0)),
        )
        assertEquals(listOf(1, 1), result.map { it.lanes })
        assertEquals(listOf(0, 0), result.map { it.lane })
    }

    @Test
    fun `two overlapping items split into two lanes`() {
        val result = blocks(
            item("standup", LocalTime.of(14, 0), LocalTime.of(14, 30)),
            item("review", LocalTime.of(14, 15), LocalTime.of(15, 0)),
        )
        assertEquals(listOf(0, 1), result.map { it.lane })
        assertEquals(listOf(2, 2), result.map { it.lanes })
    }

    @Test
    fun `only the colliding cluster splits, the rest of the day keeps full height`() {
        val result = blocks(
            item("standup", LocalTime.of(14, 0), LocalTime.of(14, 30)),
            item("review", LocalTime.of(14, 15), LocalTime.of(15, 0)),
            item("evening", LocalTime.of(19, 0), LocalTime.of(20, 0)),
        ).associateBy { it.id }
        assertEquals(2, result.getValue("standup").lanes)
        assertEquals(2, result.getValue("review").lanes)
        assertEquals(1, result.getValue("evening").lanes)
    }

    @Test
    fun `back-to-back items do not count as overlapping`() {
        val result = blocks(
            item("first", LocalTime.of(9, 0), LocalTime.of(10, 0)),
            item("second", LocalTime.of(10, 0), LocalTime.of(11, 0)),
        )
        assertEquals(listOf(1, 1), result.map { it.lanes })
    }

    @Test
    fun `a third concurrent item shares the lower lane rather than adding a third`() {
        val result = blocks(
            item("a", LocalTime.of(14, 0), LocalTime.of(15, 0)),
            item("b", LocalTime.of(14, 10), LocalTime.of(15, 0)),
            item("c", LocalTime.of(14, 20), LocalTime.of(15, 0)),
        )
        assertEquals(listOf(0, 1, 1), result.map { it.lane })
        assertEquals(listOf(2, 2, 2), result.map { it.lanes })
    }

    @Test
    fun `an item with no end of its own still occupies the bar`() {
        val result = blocks(item("task", LocalTime.of(9, 0), kind = AgendaKind.Task))
        assertEquals(1, result.size)
        assertEquals(ribbonFraction(LocalTime.of(9, 30)), result.single().to)
    }

    @Test
    fun `items from another day are left off the bar`() {
        val yesterday = HomeAgendaItem(
            id = "old",
            title = "old",
            kind = AgendaKind.Event,
            start = today.minusDays(1).atTime(14, 0),
            end = today.minusDays(1).atTime(15, 0),
            completed = false,
            colorKey = "sky",
        )
        assertEquals(emptyList<RibbonBlock>(), ribbonBlocks(listOf(yesterday), today))
    }

    // tapping

    private val standup = item("standup", LocalTime.of(14, 0), LocalTime.of(14, 30))
    private val review = item("review", LocalTime.of(14, 15), LocalTime.of(15, 0))

    @Test
    fun `a tap on empty track selects nothing`() {
        val result = pickRibbonBlock(blocks(standup), ribbonFraction(LocalTime.of(9, 0)), 0f, null)
        assertNull(result)
    }

    @Test
    fun `a tap within the slack still lands on a short block`() {
        val bars = blocks(standup)
        val justPast = bars.single().to + .01f
        assertEquals("standup", pickRibbonBlock(bars, justPast, .02f, null)?.id)
        assertNull(pickRibbonBlock(bars, justPast, 0f, null))
    }

    @Test
    fun `tapping a collided stretch cycles through it and then clears`() {
        val bars = blocks(standup, review)
        val overlap = ribbonFraction(LocalTime.of(14, 20))

        val first = pickRibbonBlock(bars, overlap, 0f, null)
        assertEquals("standup", first?.id)

        val second = pickRibbonBlock(bars, overlap, 0f, first?.id)
        assertEquals("review", second?.id)

        assertNull(pickRibbonBlock(bars, overlap, 0f, second?.id))
    }

    @Test
    fun `tapping the same lone block twice clears it`() {
        val bars = blocks(standup)
        val middle = ribbonFraction(LocalTime.of(14, 15))
        assertEquals("standup", pickRibbonBlock(bars, middle, 0f, null)?.id)
        assertNull(pickRibbonBlock(bars, middle, 0f, "standup"))
    }
}
