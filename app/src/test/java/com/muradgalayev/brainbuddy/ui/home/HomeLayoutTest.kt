package com.muradgalayev.brainbuddy.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    private fun layout(
        order: List<String>? = null,
        hidden: Set<String> = emptySet(),
        spans: Map<String, String> = emptyMap(),
    ) = homeLayout(storedOrder = order, hidden = hidden, storedSpans = spans)

    @Test
    fun `an untouched layout is the declared order at the declared widths`() {
        val rows = layout(hidden = HomeWidget.defaultHidden).rows(includeHidden = false)
        assertEquals(
            listOf(
                listOf(HomeWidget.Today),
                listOf(HomeWidget.Mode),
                listOf(HomeWidget.NextUp),
                listOf(HomeWidget.QuickAdd, HomeWidget.Wellness),
                listOf(HomeWidget.Tree),
                listOf(HomeWidget.AiPrompt),
            ),
            rows.map { it.widgets },
        )
    }

    @Test
    fun `a stored order is honoured`() {
        val result = layout(order = listOf("ai_prompt", "today")).order.take(2)
        assertEquals(listOf(HomeWidget.AiPrompt, HomeWidget.Today), result)
    }

    @Test
    fun `a widget missing from a stored order keeps its declared position`() {
        // what someone with an older layout sees after a release adds a tile: it turns up where it was
        // designed to go, not dumped at the bottom
        val stored = HomeWidget.entries.filterNot { it == HomeWidget.NextUp }.map { it.id }
        val order = layout(order = stored).order
        assertEquals(HomeWidget.entries.size, order.size)
        assertEquals(HomeWidget.entries.indexOf(HomeWidget.NextUp), order.indexOf(HomeWidget.NextUp))
    }

    @Test
    fun `an unknown stored id is dropped`() {
        val order = layout(order = listOf("today", "a_tile_that_no_longer_exists")).order
        assertEquals(HomeWidget.entries.size, order.size)
        assertEquals(HomeWidget.Today, order.first())
    }

    @Test
    fun `resizing a full tile to half lets it pair with the next one`() {
        val result = layout(
            order = listOf("today", "quick_add"),
            hidden = HomeWidget.entries.map { it.id }.toSet() - "today" - "quick_add",
            spans = mapOf("today" to "Half"),
        ).rows(includeHidden = false)

        assertEquals(1, result.size)
        assertTrue(result.single().isPair)
        assertEquals(listOf(HomeWidget.Today, HomeWidget.QuickAdd), result.single().widgets)
    }

    @Test
    fun `resizing a half tile to full breaks the pair it was in`() {
        val result = layout(
            order = listOf("quick_add", "wellness"),
            hidden = HomeWidget.entries.map { it.id }.toSet() - "quick_add" - "wellness",
            spans = mapOf("quick_add" to "Full"),
        ).rows(includeHidden = false)

        assertEquals(listOf(listOf(HomeWidget.QuickAdd), listOf(HomeWidget.Wellness)), result.map { it.widgets })
        assertTrue(result.none { it.isPair })
    }

    @Test
    fun `an unreadable stored span falls back to the declared one`() {
        val result = layout(spans = mapOf("today" to "Enormous"))
        assertEquals(WidgetSpan.Full, result.spanOf(HomeWidget.Today))
    }

    @Test
    fun `hidden tiles stay in place as ghosts while editing`() {
        val result = layout(hidden = setOf("mode"))
        val editing = result.rows(includeHidden = true).flatMap { it.tiles }
        val live = result.rows(includeHidden = false).flatMap { it.tiles }

        assertEquals(HomeWidget.entries.size, editing.size)
        assertEquals(HomeWidget.entries.size - 1, live.size)
        // same slot it occupied before it was switched off
        assertEquals(HomeWidget.Mode, editing[1].widget)
        assertTrue(editing[1].hidden)
        assertFalse(editing[0].hidden)
    }

    @Test
    fun `dragging a tile down puts it at the index it was dropped on`() {
        val start = layout()
        val moved = start.reordered(from = 0, to = 2)
        assertEquals(start.order[1], moved[0])
        assertEquals(start.order[2], moved[1])
        assertEquals(start.order[0], moved[2])
        assertEquals(start.order.size, moved.size)
    }

    @Test
    fun `dragging a tile up puts it at the index it was dropped on`() {
        val start = layout()
        val moved = start.reordered(from = 3, to = 0)
        assertEquals(start.order[3], moved[0])
        assertEquals(start.order[0], moved[1])
    }

    @Test
    fun `a drag that goes nowhere or off the ends changes nothing`() {
        val start = layout()
        assertEquals(start.order, start.reordered(from = 2, to = 2))
        assertEquals(start.order, start.reordered(from = 0, to = -1))
        assertEquals(start.order, start.reordered(from = 0, to = start.order.size))
    }
}
