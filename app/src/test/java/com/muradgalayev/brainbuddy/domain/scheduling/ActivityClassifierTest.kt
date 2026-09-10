package com.muradgalayev.brainbuddy.domain.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityClassifierTest {

    private fun kindOf(title: String) = ActivityClassifier.classify(title).kind

    @Test
    fun `recognises the everyday vocabulary of a calendar`() {
        assertEquals(ActivityKind.Dinner, kindOf("Dinner"))
        assertEquals(ActivityKind.Dinner, kindOf("dinner with Leyla"))
        assertEquals(ActivityKind.Lunch, kindOf("Lunch break"))
        assertEquals(ActivityKind.Workout, kindOf("Gym"))
        assertEquals(ActivityKind.Workout, kindOf("leg day at the gym"))
        assertEquals(ActivityKind.Meeting, kindOf("Team standup"))
        assertEquals(ActivityKind.Appointment, kindOf("Dentist appointment"))
        assertEquals(ActivityKind.Medication, kindOf("Take meds"))
    }

    @Test
    fun `punctuation and case do not matter`() {
        assertEquals(ActivityKind.Dinner, kindOf("DINNER!!!"))
        assertEquals(ActivityKind.Workout, kindOf("gym?"))
        assertEquals(ActivityKind.Meeting, kindOf("1:1 with Sam"))
    }

    // longest match wins, so a phrase beats the single word buried inside it
    @Test
    fun `multi-word phrases beat the words inside them`() {
        assertEquals(ActivityKind.Social, kindOf("catch up with mum"))
        assertEquals(ActivityKind.Errand, kindOf("grocery shopping"))
        assertEquals(ActivityKind.DeepWork, kindOf("deep work on the report"))
    }

    @Test
    fun `unknown titles fall back to General with zero confidence`() {
        val result = ActivityClassifier.classify("Zorblax")
        assertEquals(ActivityKind.General, result.kind)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun `description only classifies at reduced confidence`() {
        val result = ActivityClassifier.classify("Thing", "quick gym session")
        assertEquals(ActivityKind.Workout, result.kind)
        assertTrue(result.confidence > 0f && result.confidence < 0.75f)
    }

    @Test
    fun `habit keys collapse phrasings of the same routine`() {
        assertEquals("title:dinner", HabitKey.forTitle("Dinner"))
        assertEquals("title:dinner", HabitKey.forTitle("dinner with Leyla"))
        assertEquals("title:dinner", HabitKey.forTitle("  Grab DINNER!  "))
        assertEquals("title:gym", HabitKey.forTitle("gym session"))
    }

    @Test
    fun `habit keys keep genuinely different routines apart`() {
        assertTrue(HabitKey.forTitle("morning run") != HabitKey.forTitle("errand run"))
    }

    @Test
    fun `a title with nothing in it produces no key`() {
        assertEquals(null, HabitKey.forTitle("..."))
        assertEquals(null, HabitKey.forTitle("the a my"))
    }
}
