package com.muradgalayev.brainbuddy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AppModeScheduleTest {

    private fun schedule(
        days: Set<Int>,
        start: Int,
        end: Int,
        enabled: Boolean = true,
    ) = ModeSchedule(days = days, startMinute = start, endMinute = end, enabled = enabled)

    // windows within one day

    @Test
    fun `a weekday window covers its own hours only`() {
        val work = schedule(ModeSchedule.WEEKDAYS, 9 * 60, 17 * 60)

        assertTrue(work.covers(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0)))
        assertTrue(work.covers(DayOfWeek.WEDNESDAY, LocalTime.of(16, 59)))
        assertFalse(work.covers(DayOfWeek.WEDNESDAY, LocalTime.of(8, 59)))
        // the end is exclusive: 17:00 is the first minute of not-work
        assertFalse(work.covers(DayOfWeek.WEDNESDAY, LocalTime.of(17, 0)))
        assertFalse(work.covers(DayOfWeek.SATURDAY, LocalTime.of(12, 0)))
    }

    @Test
    fun `a disabled schedule covers nothing`() {
        val off = schedule(ModeSchedule.WEEKDAYS, 9 * 60, 17 * 60, enabled = false)
        assertFalse(off.covers(DayOfWeek.WEDNESDAY, LocalTime.of(12, 0)))
    }

    @Test
    fun `a schedule with no days covers nothing`() {
        assertFalse(schedule(emptySet(), 9 * 60, 17 * 60).covers(DayOfWeek.MONDAY, LocalTime.NOON))
    }

    @Test
    fun `an all-day schedule includes the final minute`() {
        val allDay = schedule(setOf(DayOfWeek.SATURDAY.value), 0, 24 * 60)

        assertTrue(allDay.covers(DayOfWeek.SATURDAY, LocalTime.of(23, 59)))
        assertFalse(allDay.covers(DayOfWeek.SUNDAY, LocalTime.MIDNIGHT))
    }

    // windows that cross midnight

    @Test
    fun `an overnight window runs into the following morning`() {
        // Mon-Fri 22:00 to 06:00, the tail belongs to the day the window started on
        val rest = schedule(ModeSchedule.WEEKDAYS, 22 * 60, 6 * 60)

        assertTrue(rest.crossesMidnight)
        assertTrue("22:30 Monday is inside", rest.covers(DayOfWeek.MONDAY, LocalTime.of(22, 30)))
        assertTrue("02:00 Tuesday belongs to Monday's window", rest.covers(DayOfWeek.TUESDAY, LocalTime.of(2, 0)))
        assertFalse("07:00 Tuesday is past the end", rest.covers(DayOfWeek.TUESDAY, LocalTime.of(7, 0)))
        assertFalse("21:00 Monday is before the start", rest.covers(DayOfWeek.MONDAY, LocalTime.of(21, 0)))
    }

    @Test
    fun `an overnight window starting Friday still covers Saturday morning`() {
        val rest = schedule(ModeSchedule.WEEKDAYS, 22 * 60, 6 * 60)
        // Saturday is not a scheduled day, but Friday night runs into it
        assertTrue(rest.covers(DayOfWeek.SATURDAY, LocalTime.of(3, 0)))
        assertFalse(rest.covers(DayOfWeek.SATURDAY, LocalTime.of(23, 0)))
    }

    // which mode wins

    @Test
    fun `a manual pick beats any schedule`() {
        val active = resolveActiveMode(
            modes = BuiltInModes.all,
            selection = ModeSelection.Manual(AppMode.ID_WEEKEND),
            day = DayOfWeek.WEDNESDAY,
            time = LocalTime.of(10, 0),
        )
        // Wednesday 10:00 is inside Work's schedule, but the user chose Weekend
        assertEquals(AppMode.ID_WEEKEND, active?.id)
    }

    @Test
    fun `a manual id that no longer exists falls back to the schedule`() {
        // the mode was deleted on another device, and the stale pick must not blank everything
        val active = resolveActiveMode(
            modes = BuiltInModes.all,
            selection = ModeSelection.Manual("deleted-mode"),
            day = DayOfWeek.WEDNESDAY,
            time = LocalTime.of(10, 0),
        )
        assertEquals(AppMode.ID_WORK, active?.id)
    }

    @Test
    fun `overlapping schedules resolve by sort order, not by list order`() {
        val late = BuiltInModes.work.copy(id = "late", sortIndex = 5)
        val early = BuiltInModes.work.copy(id = "early", sortIndex = 1)
        val active = resolveActiveMode(
            modes = listOf(late, early),
            selection = ModeSelection.Automatic,
            day = DayOfWeek.WEDNESDAY,
            time = LocalTime.of(10, 0),
        )
        assertEquals("early", active?.id)
    }

    @Test
    fun `nothing scheduled and nothing picked means no mode`() {
        assertNull(
            resolveActiveMode(
                modes = BuiltInModes.all,
                selection = ModeSelection.Automatic,
                day = DayOfWeek.WEDNESDAY,
                time = LocalTime.of(22, 0),
            )
        )
    }

    @Test
    fun `explicit no mode suppresses a matching schedule`() {
        assertNull(
            resolveActiveMode(
                modes = BuiltInModes.all,
                selection = ModeSelection.NoMode,
                day = DayOfWeek.WEDNESDAY,
                time = LocalTime.of(10, 0),
            )
        )
    }

    @Test
    fun `stored selection keeps legacy absence automatic and round trips no mode`() {
        assertEquals(ModeSelection.Automatic, ModeSelection.fromStoredValue(null))
        assertEquals(ModeSelection.Manual("work"), ModeSelection.fromStoredValue("work"))
        assertEquals(
            ModeSelection.NoMode,
            ModeSelection.fromStoredValue(ModeSelection.NoMode.toStoredValue()),
        )
        assertNull(ModeSelection.Automatic.toStoredValue())
    }

    @Test
    fun `deleted manual selection becomes automatic but explicit no mode does not`() {
        assertEquals(
            ModeSelection.Automatic,
            ModeSelection.Manual("deleted").withAvailableModes(BuiltInModes.all),
        )
        assertEquals(
            ModeSelection.NoMode,
            ModeSelection.NoMode.withAvailableModes(BuiltInModes.all),
        )
    }

    // timer boundaries

    @Test
    fun `next boundary is the next same-day start`() {
        val boundary = nextModeScheduleBoundary(
            modes = listOf(BuiltInModes.work),
            after = LocalDateTime.of(2026, 8, 12, 8, 30), // Wednesday
        )

        assertEquals(LocalDateTime.of(2026, 8, 12, 9, 0), boundary)
    }

    @Test
    fun `next boundary finds the end of yesterday's overnight schedule`() {
        val overnight = AppMode(
            id = "sleep",
            name = "Sleep",
            schedule = schedule(setOf(DayOfWeek.MONDAY.value), 22 * 60, 6 * 60),
        )
        val boundary = nextModeScheduleBoundary(
            modes = listOf(overnight),
            after = LocalDateTime.of(2026, 8, 11, 2, 0), // Tuesday
        )

        assertEquals(LocalDateTime.of(2026, 8, 11, 6, 0), boundary)
    }

    @Test
    fun `next boundary wraps to next week without polling`() {
        val monday = AppMode(
            id = "monday",
            name = "Monday",
            schedule = schedule(setOf(DayOfWeek.MONDAY.value), 9 * 60, 10 * 60),
        )
        val boundary = nextModeScheduleBoundary(
            modes = listOf(monday),
            after = LocalDateTime.of(2026, 8, 10, 12, 0), // Monday, after both edges
        )

        assertEquals(LocalDateTime.of(2026, 8, 17, 9, 0), boundary)
    }

    @Test
    fun `disabled schedules have no boundary`() {
        val disabled = BuiltInModes.work.copy(
            schedule = BuiltInModes.work.schedule?.copy(enabled = false),
        )

        assertNull(
            nextModeScheduleBoundary(
                modes = listOf(disabled),
                after = LocalDateTime.of(2026, 8, 12, 8, 30),
            )
        )
    }

    @Test
    fun `Rome spring-forward sleep uses elapsed instants rather than local hours`() {
        val overnight = AppMode(
            id = "dst-night",
            name = "DST night",
            schedule = schedule(setOf(DayOfWeek.SATURDAY.value), 22 * 60, 6 * 60),
        )
        val rome = ZoneId.of("Europe/Rome")
        val after = ZonedDateTime.of(
            LocalDateTime.of(2026, 3, 29, 1, 30),
            rome,
        )

        // The scheduler deliberately rechecks at the 02:00 -> 03:00 clock jump, then sleeps
        // to the real 06:00 end. Across both sleeps only 3h30 elapse, not the 4h30 suggested
        // by subtracting the two local clock readings.
        val clockJump = requireNotNull(nextModeScheduleBoundary(listOf(overnight), after))
        val end = requireNotNull(nextModeScheduleBoundary(listOf(overnight), clockJump))

        assertEquals(LocalTime.of(3, 0), clockJump.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), clockJump.offset)
        assertEquals(LocalTime.of(6, 0), end.toLocalTime())
        assertEquals(Duration.ofHours(3).plusMinutes(30), Duration.between(after.toInstant(), end.toInstant()))
    }

    @Test
    fun `Rome fall-back sleep accounts for the repeated hour`() {
        val overnight = AppMode(
            id = "dst-night",
            name = "DST night",
            schedule = schedule(setOf(DayOfWeek.SATURDAY.value), 22 * 60, 6 * 60),
        )
        val rome = ZoneId.of("Europe/Rome")
        val after = ZonedDateTime.ofLocal(
            LocalDateTime.of(2026, 10, 25, 1, 30),
            rome,
            ZoneOffset.ofHours(2),
        )

        val clockJump = requireNotNull(nextModeScheduleBoundary(listOf(overnight), after))
        val end = requireNotNull(nextModeScheduleBoundary(listOf(overnight), clockJump))

        assertEquals(LocalTime.of(2, 0), clockJump.toLocalTime())
        assertEquals(ZoneOffset.ofHours(1), clockJump.offset)
        assertEquals(LocalTime.of(6, 0), end.toLocalTime())
        // The repeated hour makes this one real hour longer than local-time subtraction.
        assertEquals(Duration.ofHours(5).plusMinutes(30), Duration.between(after.toInstant(), end.toInstant()))
    }

    @Test
    fun `Rome repeated schedule boundary is visited at both offsets`() {
        val overlap = AppMode(
            id = "overlap",
            name = "Overlap",
            schedule = schedule(setOf(DayOfWeek.SUNDAY.value), 2 * 60 + 30, 4 * 60),
        )
        val rome = ZoneId.of("Europe/Rome")
        val afterFirstCrossing = ZonedDateTime.ofLocal(
            LocalDateTime.of(2026, 10, 25, 2, 45),
            rome,
            ZoneOffset.ofHours(2),
        )

        val rollback = requireNotNull(nextModeScheduleBoundary(listOf(overlap), afterFirstCrossing))
        val repeatedStart = requireNotNull(nextModeScheduleBoundary(listOf(overlap), rollback))

        assertEquals(LocalTime.of(2, 0), rollback.toLocalTime())
        assertEquals(ZoneOffset.ofHours(1), rollback.offset)
        assertEquals(LocalTime.of(2, 30), repeatedStart.toLocalTime())
        assertEquals(ZoneOffset.ofHours(1), repeatedStart.offset)
        assertEquals(Duration.ofMinutes(30), Duration.between(rollback.toInstant(), repeatedStart.toInstant()))
    }

    // ── The presets ──

    @Test
    fun `built-in modes never silence medication reminders`() {
        // A mode that suppresses a dose reminder causes harm rather than quiet.
        BuiltInModes.all.forEach { mode ->
            val allowed = mode.overrides.allowedNotifications
            if (allowed != null) {
                assertTrue(
                    "${mode.name} must let medication through",
                    ModeNotificationKind.MEDICATION_REMINDERS in allowed,
                )
            }
        }
    }

    @Test
    fun `built-in modes override something`() {
        BuiltInModes.all.forEach { mode ->
            assertFalse("${mode.name} would change nothing", mode.overrides.isEmpty)
        }
    }
}
