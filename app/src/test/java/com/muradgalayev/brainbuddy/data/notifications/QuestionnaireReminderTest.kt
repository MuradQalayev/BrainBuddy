package com.muradgalayev.brainbuddy.data.notifications

import com.muradgalayev.brainbuddy.testing.TestStrings
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireReminderTest {
    private val zone = ZoneId.of("Europe/Rome")

    @Test
    fun `wake time chooses a gentle daytime reminder hour`() {
        assertEquals(10, preferredReminderHour("07:00"))
        assertEquals(14, preferredReminderHour("11:00"))
        assertEquals(19, preferredReminderHour("18:30"))
        assertEquals(18, preferredReminderHour(""))
    }

    @Test
    fun `next reminder uses the requested future day and preferred hour`() {
        val now = ZonedDateTime.of(2026, 9, 9, 21, 45, 0, 0, zone)
        val trigger = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(
                nextQuestionnaireReminderMillis(
                    nowMillis = now.toInstant().toEpochMilli(),
                    delayDays = 3,
                    preferredHour = 11,
                    zoneId = zone,
                )
            ),
            zone,
        )

        assertEquals(2026, trigger.year)
        assertEquals(9, trigger.monthValue)
        assertEquals(12, trigger.dayOfMonth)
        assertEquals(11, trigger.hour)
        assertEquals(0, trigger.minute)
    }

    @Test
    fun `notification copy shares progress but no questionnaire answers`() {
        val (title, body) = questionnaireReminderCopy(answered = 5, total = 25, lookup = TestStrings.en)

        assertTrue(title.contains("min"))
        assertTrue(body.contains("5 of 25"))
        assertTrue(body.contains("safely saved"))
    }

    @Test
    fun `fully answered draft is reminded to finish submission`() {
        val (title, body) = questionnaireReminderCopy(answered = 25, total = 25, lookup = TestStrings.en)

        assertEquals("Your profile is ready to finish", title)
        assertTrue(body.contains("finish your profile"))
    }
}
