package com.muradgalayev.brainbuddy.domain.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

// 'Tomorrow' is a word about today, not about whatever day the dialog happens to be open on.
// conflating the two produced a label that was defensible and still wrong to read: editing the
// 25th, offered the 26th, told 'Tomorrow', on a day when tomorrow meant the 25th
class TimeSuggestionDayLabelTest {

    private val today = LocalDate.of(2026, 8, 24) // a Monday

    private fun suggestionOn(date: LocalDate) = TimeSuggestion(
        date = date,
        startMinutes = 9 * 60,
        endMinutes = 10 * 60,
        kind = ActivityKind.Meeting,
        reason = SuggestionReason.TypicalForActivity,
        reasonText = "test",
        score = 1.0,
    )

    @Test
    fun `no label when the suggestion is for the day already open`() {
        val open = LocalDate.of(2026, 8, 25)
        assertNull(suggestionOn(open).dayLabel(relativeTo = open, today = today))
    }

    @Test
    fun `tomorrow is used when it really is tomorrow`() {
        // dialog open on today, suggestion for today + 1
        assertEquals(
            "Tomorrow",
            suggestionOn(today.plusDays(1)).dayLabel(relativeTo = today, today = today),
        )
    }

    @Test
    fun `the reported bug - editing the 25th and offered the 26th`() {
        val open = LocalDate.of(2026, 8, 25)
        val offered = LocalDate.of(2026, 8, 26)

        val label = suggestionOn(offered).dayLabel(relativeTo = open, today = today)

        // the old code said Tomorrow here, which on the 24th means the 25th
        assertEquals("Wed 26", label)
    }

    @Test
    fun `today keeps its word even when the dialog is on another day`() {
        val open = LocalDate.of(2026, 8, 27)
        assertEquals(
            "Today",
            suggestionOn(today).dayLabel(relativeTo = open, today = today),
        )
    }

    @Test
    fun `within the week the weekday always carries its date`() {
        val label = suggestionOn(today.plusDays(3)).dayLabel(relativeTo = today, today = today)
        // never a bare 'Thu', that's the same ambiguity one week out
        assertEquals("Thu 27", label)
    }

    @Test
    fun `past a week it falls back to a plain date`() {
        val far = today.plusDays(20) // 13 September
        assertEquals(
            "13 Sep",
            suggestionOn(far).dayLabel(relativeTo = today, today = today),
        )
    }
}
