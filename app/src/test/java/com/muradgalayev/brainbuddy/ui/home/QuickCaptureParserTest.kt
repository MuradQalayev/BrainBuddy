package com.muradgalayev.brainbuddy.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickCaptureParserTest {

    // Wednesday, 10:15
    private val now = LocalDateTime.of(2026, 8, 12, 10, 15)
    private val today = now.toLocalDate()

    @Test
    fun `plain text keeps its title and says nothing about when`() {
        val draft = parseQuickCapture("Buy milk", now)
        assertEquals("Buy milk", draft.title)
        assertNull(draft.date)
        assertNull(draft.time)
    }

    @Test
    fun `tomorrow with a meridiem time`() {
        val draft = parseQuickCapture("Call pharmacy tomorrow at 3pm", now)
        assertEquals("Call pharmacy", draft.title)
        assertEquals(today.plusDays(1), draft.date)
        assertEquals(LocalTime.of(15, 0), draft.time)
    }

    @Test
    fun `24h clock is taken as written`() {
        val draft = parseQuickCapture("Standup 09:30", now)
        assertEquals("Standup", draft.title)
        assertEquals(LocalTime.of(9, 30), draft.time)
    }

    @Test
    fun `relative offsets resolve against now`() {
        val draft = parseQuickCapture("Take meds in 20 min", now)
        assertEquals("Take meds", draft.title)
        assertEquals(today, draft.date)
        assertEquals(LocalTime.of(10, 35), draft.time)
    }

    @Test
    fun `hour offsets roll the clock forward`() {
        val draft = parseQuickCapture("Leave in 2h", now)
        assertEquals("Leave", draft.title)
        assertEquals(LocalTime.of(12, 15), draft.time)
    }

    @Test
    fun `a bare hour after 'at' resolves to the next one still ahead`() {
        val draft = parseQuickCapture("Ring dad at 8", now)
        assertEquals("Ring dad", draft.title)
        assertEquals(LocalTime.of(20, 0), draft.time)
    }

    @Test
    fun `a bare hour still ahead today stays in the morning`() {
        val draft = parseQuickCapture("Ring dad at 11", now)
        assertEquals(LocalTime.of(11, 0), draft.time)
    }

    @Test
    fun `weekday names jump to the next matching day`() {
        val draft = parseQuickCapture("Gym on friday", now)
        assertEquals("Gym", draft.title)
        assertEquals(LocalDate.of(2026, 8, 14), draft.date)
    }

    @Test
    fun `a weekday that is today means today`() {
        val draft = parseQuickCapture("Laundry wednesday", now)
        assertEquals("Laundry", draft.title)
        assertEquals(today, draft.date)
    }

    @Test
    fun `part of day only counts next to a day word`() {
        val draft = parseQuickCapture("Dentist tomorrow morning", now)
        assertEquals("Dentist", draft.title)
        assertEquals(today.plusDays(1), draft.date)
        assertEquals(LocalTime.of(9, 0), draft.time)
    }

    @Test
    fun `a standalone part of day stays part of the title`() {
        val draft = parseQuickCapture("Morning routine", now)
        assertEquals("Morning routine", draft.title)
        assertNull(draft.time)
    }

    @Test
    fun `tonight implies the evening`() {
        val draft = parseQuickCapture("Pack bag tonight", now)
        assertEquals("Pack bag", draft.title)
        assertEquals(today, draft.date)
        assertEquals(LocalTime.of(20, 0), draft.time)
    }

    @Test
    fun `an explicit time beats the part of day default`() {
        val draft = parseQuickCapture("Dentist tomorrow morning at 11:45", now)
        assertEquals("Dentist", draft.title)
        assertEquals(LocalTime.of(11, 45), draft.time)
    }

    @Test
    fun `numbers that are part of the task are left alone`() {
        val draft = parseQuickCapture("Buy 3 eggs", now)
        assertEquals("Buy 3 eggs", draft.title)
        assertNull(draft.time)
    }

    @Test
    fun `weekday abbreviations are not eaten out of ordinary words`() {
        val draft = parseQuickCapture("Buy sun cream", now)
        assertEquals("Buy sun cream", draft.title)
        assertNull(draft.date)
    }

    @Test
    fun `next week shifts by seven days`() {
        val draft = parseQuickCapture("Renew pass next week", now)
        assertEquals("Renew pass", draft.title)
        assertEquals(today.plusWeeks(1), draft.date)
    }

    @Test
    fun `a line that is nothing but a time keeps the raw text as its title`() {
        val draft = parseQuickCapture("tomorrow", now)
        assertEquals("tomorrow", draft.title)
        assertEquals(today.plusDays(1), draft.date)
    }

    @Test
    fun `midday and midnight cross over correctly`() {
        assertEquals(LocalTime.of(0, 30), parseQuickCapture("Sleep 12:30am", now).time)
        assertEquals(LocalTime.of(12, 30), parseQuickCapture("Lunch 12:30pm", now).time)
    }

    @Test
    fun `Italian day and time`() {
        val draft = parseQuickCapture("Chiamare la farmacia domani alle 15", now)
        assertEquals("Chiamare la farmacia", draft.title)
        assertEquals(today.plusDays(1), draft.date)
        assertEquals(LocalTime.of(15, 0), draft.time)
    }

    @Test
    fun `Italian relative offset`() {
        val draft = parseQuickCapture("Riunione tra 30 min", now)
        assertEquals("Riunione", draft.title)
        assertEquals(LocalTime.of(10, 45), draft.time)
    }

    @Test
    fun `Italian weekday with an accent, and without one`() {
        // now is a Wednesday, so the coming Friday is two days out
        assertEquals(today.plusDays(2), parseQuickCapture("Dentista venerdì", now).date)
        assertEquals(today.plusDays(2), parseQuickCapture("Dentista venerdi", now).date)
        assertEquals("Dentista", parseQuickCapture("Dentista venerdì", now).title)
    }

    @Test
    fun `Italian part of day and dotted time`() {
        val evening = parseQuickCapture("Cena domani sera", now)
        assertEquals(today.plusDays(1), evening.date)
        assertEquals(LocalTime.of(19, 0), evening.time)
        assertEquals(LocalTime.of(15, 30), parseQuickCapture("Palestra alle 15.30", now).time)
    }

    @Test
    fun `a dotted number without alle stays part of the title`() {
        val draft = parseQuickCapture("Pagare 2.50 al bar", now)
        assertEquals("Pagare 2.50 al bar", draft.title)
        assertNull(draft.time)
    }

}
