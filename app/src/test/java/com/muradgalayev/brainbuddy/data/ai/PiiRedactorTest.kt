package com.muradgalayev.brainbuddy.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

// guards the two ways this class can fail badly: under-redacting, which leaks a direct
// identifier to a third party, and over-redacting, which silently corrupts the dates and phone
// numbers the calendar and contact tools depend on.
// exercises the generic patterns directly, since the known-identifier pass needs a live
// AuthRepository and is exact-match, so it has no interesting edge cases
class PiiRedactorTest {

    private val phone = PiiRedactor.PHONE_PATTERN
    private val email = PiiRedactor.EMAIL_PATTERN

    @Test
    fun `redacts phone numbers users actually type`() {
        val cases = listOf(
            "call me on +994 55 123 45 67",
            "my number is 00994551234567",
            "reach me at 055 123 4567",
            "it's 0551234567",
        )
        cases.forEach { input ->
            assertEquals(
                "should have redacted a phone in: $input",
                true,
                phone.containsMatchIn(input),
            )
        }
    }

    @Test
    fun `leaves calendar dates and times alone`() {
        // these are load-bearing: create_calendar_event takes yyyy-MM-dd and HH:mm
        val cases = listOf(
            "book it for 2026-08-07 at 14:30",
            "meeting on 2026-12-01",
            "from 09:00 to 10:30",
            "split it into 3 blocks of 25 minutes",
            "move it to 15/03/2026",
        )
        cases.forEach { input ->
            assertEquals(
                "must not treat this as a phone number: $input",
                false,
                phone.containsMatchIn(input),
            )
        }
    }

    @Test
    fun `redacts email addresses`() {
        assertEquals(
            "mail me at [email withheld] please",
            email.replace("mail me at ada@example.com please", "[email withheld]"),
        )
    }

    @Test
    fun `leaves ordinary words containing an at-sign alone`() {
        assertEquals(false, email.containsMatchIn("meet me @ the cafe"))
    }
}
