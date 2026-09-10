package com.muradgalayev.brainbuddy.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetsTest {

    @Test
    fun `only the opt-in tiles start hidden`() {
        assertEquals(setOf("focus", "wins"), HomeWidget.defaultHidden)
    }

    @Test
    fun `the default home is today, mode, next up, the capture pair, tree and ai`() {
        val rows = homeRows(visibleHomeWidgets(HomeWidget.defaultHidden))
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
    fun `a half tile left alone takes the whole row`() {
        val rows = homeRows(visibleHomeWidgets(HomeWidget.defaultHidden + "wellness"))
        val captureRow = rows.first { HomeWidget.QuickAdd in it.widgets }
        assertEquals(listOf(HomeWidget.QuickAdd), captureRow.widgets)
        assertFalse(captureRow.isPair)
    }

    @Test
    fun `the opt-in tiles pair up with each other once added`() {
        val rows = homeRows(visibleHomeWidgets(emptySet()))
        assertEquals(
            listOf(
                listOf(HomeWidget.Today),
                listOf(HomeWidget.Mode),
                listOf(HomeWidget.NextUp),
                listOf(HomeWidget.QuickAdd, HomeWidget.Wellness),
                listOf(HomeWidget.Focus, HomeWidget.Wins),
                listOf(HomeWidget.Tree),
                listOf(HomeWidget.AiPrompt),
            ),
            rows.map { it.widgets },
        )
    }

    @Test
    fun `a full-width tile between two halves breaks the pair`() {
        val rows = homeRows(listOf(HomeWidget.QuickAdd, HomeWidget.NextUp, HomeWidget.Wellness))
        assertEquals(
            listOf(
                listOf(HomeWidget.QuickAdd),
                listOf(HomeWidget.NextUp),
                listOf(HomeWidget.Wellness),
            ),
            rows.map { it.widgets },
        )
    }

    @Test
    fun `hiding everything leaves nothing to lay out`() {
        val allHidden = HomeWidget.entries.map { it.id }.toSet()
        assertTrue(visibleHomeWidgets(allHidden).isEmpty())
        assertTrue(homeRows(visibleHomeWidgets(allHidden)).isEmpty())
    }

    @Test
    fun `an unknown stored id is ignored rather than crashing the screen`() {
        // a tile removed in a later release can still be sitting in someone's DataStore
        val rows = homeRows(visibleHomeWidgets(setOf("a_tile_that_no_longer_exists")))
        assertEquals(HomeWidget.entries.size, rows.sumOf { it.widgets.size })
        assertEquals(null, HomeWidget.fromId("a_tile_that_no_longer_exists"))
    }

    @Test
    fun `capture gets the wider half of its pair`() {
        assertEquals(1.25f, widgetWeight(HomeWidget.QuickAdd))
        assertEquals(1f, widgetWeight(HomeWidget.Wellness))
    }

    @Test
    fun `every widget id is unique and stable`() {
        val ids = HomeWidget.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.isNotBlank() && it == it.lowercase() })
    }
}
