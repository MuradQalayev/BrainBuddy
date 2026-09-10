package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.remote.dto.FreeBusySlotDto
import com.muradgalayev.brainbuddy.domain.scheduling.BusyInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// the wire format of a connection's day, and what it's allowed to become. these assertions are
// the client half of the privacy boundary: the server sends three numbers, and nothing
// downstream may end up holding more than that
class FreeBusyMapperTest {

    private val monday = LocalDate.of(2026, 8, 10)

    private fun slot(day: String, start: Int, end: Int) = FreeBusySlotDto(day, start, end)

    @Test
    fun `blocks are grouped by their date`() {
        val result = listOf(
            slot("2026-08-10", 540, 600),
            slot("2026-08-10", 720, 780),
            slot("2026-08-11", 540, 600),
        ).toBusyIntervalsByDate()

        assertEquals(2, result.size)
        assertEquals(2, result.getValue(monday).size)
        assertEquals(1, result.getValue(monday.plusDays(1)).size)
    }

    @Test
    fun `every block is opaque and carries no borrowed title`() {
        val result = listOf(slot("2026-08-10", 540, 600)).toBusyIntervalsByDate()
        val block = result.getValue(monday).single()

        assertTrue(block.opaque)
        assertEquals(BusyInterval.SHARED_LABEL, block.title)
    }

    @Test
    fun `a timestamped day still resolves to its date`() {
        // Postgres may render a date column with a time attached depending on the client, and the
        // leading ten characters are the part that means anything
        val result = listOf(slot("2026-08-10T00:00:00", 540, 600)).toBusyIntervalsByDate()
        assertEquals(setOf(monday), result.keys)
    }

    @Test
    fun `unusable rows are dropped rather than guessed at`() {
        val result = listOf(
            slot("not a date", 540, 600),
            slot("2026-08-10", 600, 600), // zero length
            slot("2026-08-10", 700, 600), // inverted
            slot("2026-08-10", 540, 600), // the only good one
        ).toBusyIntervalsByDate()

        assertEquals(1, result.getValue(monday).size)
        assertEquals(540, result.getValue(monday).single().startMinutes)
    }

    @Test
    fun `minutes are held inside the day`() {
        val result = listOf(slot("2026-08-10", -30, 5000)).toBusyIntervalsByDate()
        val block = result.getValue(monday).single()

        assertEquals(0, block.startMinutes)
        assertEquals(24 * 60, block.endMinutes)
    }
}
