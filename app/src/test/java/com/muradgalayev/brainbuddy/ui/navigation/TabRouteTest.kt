package com.muradgalayev.brainbuddy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// which tab the bottom bar should highlight for a given route. the navbar matches routes
// exactly and falls back to the first tab for anything unrecognised, so every detail screen
// used to light up Home regardless of where you'd opened it from. this mapping is easy to
// forget when a screen is added, which is what these tests are for
class TabRouteTest {

    @Test
    fun `a tab route resolves to itself`() {
        assertEquals(Screen.Home.route, tabRouteFor(Screen.Home.route))
        assertEquals(Screen.Activity.route, tabRouteFor(Screen.Activity.route))
        assertEquals(Screen.Calendar.route, tabRouteFor(Screen.Calendar.route))
        assertEquals(Screen.Settings.route, tabRouteFor(Screen.Settings.route))
    }

    @Test
    fun `workspace detail screens keep the Workspace tab lit`() {
        val fromWorkspace = listOf(
            Screen.Todo.route,
            Screen.Pomodoro.route,
            Screen.WellnessSummary.route,
            Screen.WellnessEdit.route,
            Screen.ActivityGoals.route,
            Screen.Medications.route,
            Screen.CareNearby.route,
            Screen.Reservation.route,
            Screen.ReservationHistory.route,
        )
        for (route in fromWorkspace) {
            assertEquals(route, Screen.Activity.route, tabRouteFor(route))
        }
    }

    @Test
    fun `every Myndora Together screen keeps the Workspace tab lit`() {
        // Together's home is the Workspace card, not Settings
        assertEquals(Screen.Activity.route, tabRouteFor(Screen.Together.route))
        assertEquals(Screen.Activity.route, tabRouteFor(Screen.AddConnection.route))
        assertEquals(Screen.Activity.route, tabRouteFor(Screen.ConnectionProfile.route))
    }

    @Test
    fun `calendar detail screens keep the Calendar tab lit`() {
        assertEquals(Screen.Calendar.route, tabRouteFor(Screen.TaskBreakdown.route))
        assertEquals(Screen.Calendar.route, tabRouteFor(Screen.TaskDetail.route))
    }

    @Test
    fun `settings sub-pages keep the Settings tab lit`() {
        val fromSettings = listOf(
            Screen.EditProfile.route,
            Screen.Customization.route,
            Screen.NotificationSettings.route,
            Screen.AiSettings.route,
            Screen.LinkedAccounts.route,
            Screen.LinkedDevices.route,
        )
        for (route in fromSettings) {
            assertEquals(route, Screen.Settings.route, tabRouteFor(route))
        }
        assertEquals(Screen.Settings.route, tabRouteFor(Screen.Modes.route))
        assertEquals(
            Screen.Settings.route,
            tabRouteFor("${Screen.ModeEdit.route}?modeId=work"),
        )
    }

    @Test
    fun `parameterised routes are matched on their base`() {
        // routes are registered as patterns, so an exact-string lookup never matches
        assertEquals(
            Screen.Activity.route,
            tabRouteFor("${Screen.ConnectionProfile.route}?userId={userId}"),
        )
        assertEquals(
            Screen.Calendar.route,
            tabRouteFor("${Screen.TaskBreakdown.route}?eventId={eventId}"),
        )
        assertEquals(
            Screen.Settings.route,
            tabRouteFor("${Screen.AiSettings.route}?focusWellness=true"),
        )
    }

    @Test
    fun `a real argument value resolves the same as the pattern`() {
        assertEquals(
            Screen.Activity.route,
            tabRouteFor("${Screen.ConnectionProfile.route}?userId=abc-123"),
        )
    }

    @Test
    fun `an unmapped route is returned unchanged rather than defaulting to a tab`() {
        // no highlight beats the wrong highlight when a new screen is added
        assertEquals("brand_new_screen", tabRouteFor("brand_new_screen"))
    }

    @Test
    fun `null route stays null`() {
        assertNull(tabRouteFor(null))
    }

    @Test
    fun `no detail screen resolves to Home`() {
        // Home has no detail screens beneath it, so anything landing there is the fallback bug returning
        val details = listOf(
            Screen.Todo.route,
            Screen.Pomodoro.route,
            Screen.WellnessSummary.route,
            Screen.Medications.route,
            Screen.CareNearby.route,
            Screen.Together.route,
            Screen.AddConnection.route,
            Screen.ConnectionProfile.route,
            Screen.TaskBreakdown.route,
            Screen.EditProfile.route,
            Screen.LinkedDevices.route,
        )
        for (route in details) {
            val resolved = tabRouteFor(route)
            assert(resolved != Screen.Home.route) { "$route wrongly highlights Home" }
        }
    }
}
