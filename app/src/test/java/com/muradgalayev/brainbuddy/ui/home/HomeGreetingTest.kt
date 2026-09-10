package com.muradgalayev.brainbuddy.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

// the greeting is the one line on home written at the user, so the failures that matter are the
// embarrassing ones: 'good evening' at 2am, a dangling comma when we don't know the name, or a
// leftover {name} shipped to a real screen
class HomeGreetingTest {

    @Test
    fun `bands cover the whole day with no gaps or overlaps`() {
        val expected = listOf(
            0 to DayBand.LateNight, 4 to DayBand.LateNight,
            5 to DayBand.EarlyMorning, 7 to DayBand.EarlyMorning,
            8 to DayBand.Morning, 11 to DayBand.Morning,
            12 to DayBand.Afternoon, 16 to DayBand.Afternoon,
            17 to DayBand.Evening, 20 to DayBand.Evening,
            21 to DayBand.Night, 23 to DayBand.Night,
        )
        expected.forEach { (hour, band) ->
            assertEquals("hour $hour", band, dayBandFor(LocalTime.of(hour, 0)))
        }
    }

    @Test
    fun `a 2am check-in gets the night-owl band, not the evening one`() {
        assertEquals(DayBand.LateNight, dayBandFor(LocalTime.of(2, 0)))
        val lines = (0..4).map { homeGreeting(LocalTime.of(2, 0), "Murad", it) }
        assertTrue("expected a night-owl line among $lines", lines.any { it.contains("Night owl") })
        assertFalse(lines.any { it.contains("evening", ignoreCase = true) })
    }

    @Test
    fun `the name is substituted when known`() {
        assertEquals("Good morning, Murad", homeGreeting(LocalTime.of(9, 0), "Murad", 0))
    }

    @Test
    fun `no name leaves a clean line rather than a dangling comma`() {
        // every phrasing in every band, since each one strips its slot differently
        val everyBand = listOf(2, 6, 9, 14, 18, 22).map { LocalTime.of(it, 0) }
        everyBand.forEach { time ->
            (0..4).forEach { seed ->
                val line = homeGreeting(time, null, seed)
                assertFalse("unfilled slot in '$line'", line.contains("{name}"))
                assertFalse("dangling comma in '$line'", line.contains(", ?") || line.endsWith(","))
                assertFalse("double space in '$line'", line.contains("  "))
                assertEquals("untrimmed '$line'", line.trim(), line)
            }
        }
    }

    @Test
    fun `the seed folds into range so any Int is safe`() {
        val inRange = homeGreeting(LocalTime.of(9, 0), "Murad", 0)
        assertEquals(inRange, homeGreeting(LocalTime.of(9, 0), "Murad", 5))
        assertEquals(inRange, homeGreeting(LocalTime.of(9, 0), "Murad", -5))
        // would overflow a naive abs(seed) % size
        homeGreeting(LocalTime.of(9, 0), "Murad", Int.MIN_VALUE)
    }

    @Test
    fun `first name falls back through username then email`() {
        assertEquals("Murad", firstNameFrom("Murad Galayev", null, null))
        assertEquals("Murad", firstNameFrom(null, "murad_g", null))
        assertEquals("Murad", firstNameFrom(null, null, "murad@example.com"))
        assertEquals("Murad", firstNameFrom("  ", "murad", null))
        assertNull(firstNameFrom(null, null, null))
    }
}
