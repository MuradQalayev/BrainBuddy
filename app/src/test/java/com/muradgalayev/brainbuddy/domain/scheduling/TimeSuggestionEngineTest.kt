package com.muradgalayev.brainbuddy.domain.scheduling

import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TimeSuggestionEngineTest {

    // a Wednesday, and a now early enough that today's whole day is still open
    private val date: LocalDate = LocalDate.of(2026, 8, 12)
    private val now: LocalDateTime = date.atTime(7, 0)
    private val nowMillis = 1_770_000_000_000L

    private val emptyLookup = HabitStatLookup { _, _ -> null }

    private fun context(
        busy: List<BusyInterval> = emptyList(),
        sleep: SleepWindow = SleepWindow(bedMinutes = 23 * 60, wakeMinutes = 7 * 60),
        productiveTime: ProductiveTime? = null,
        energy: EnergySignals? = null,
        at: LocalDateTime = now,
        on: LocalDate = date,
        workingHours: WorkingHours? = null,
    ) = DayContext(
        date = on,
        now = at,
        busy = busy,
        sleep = sleep,
        productiveTime = productiveTime,
        energy = energy,
        workingHours = workingHours,
    )

    private fun suggest(
        title: String,
        context: DayContext = context(),
        lookup: HabitStatLookup = emptyLookup,
    ) = TimeSuggestionEngine.suggest(title, "", context, lookup)

    // builds a lookup where one habit has been observed `times` times at `minutes`
    private fun lookupWith(
        habitKey: String,
        minutes: Int,
        times: Int,
        durationMinutes: Int = 60,
    ) = HabitStatLookup { key, _ ->
        if (key != habitKey) null
        else (1..times).fold(HabitTimeStat()) { acc, _ ->
            acc.observe(minutes, durationMinutes, nowMillis)
        }
    }

    // shape of the output

    @Test
    fun `an open day with no history still produces suggestions`() {
        val result = suggest("Dinner")
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= 3)
    }

    @Test
    fun `suggestions are never closer together than an hour`() {
        val result = suggest("Study")
        result.zipWithNext { a, b ->
            assertTrue(
                "${a.startLabel} and ${b.startLabel} are too close to be distinct options",
                circularDistance(a.startMinutes, b.startMinutes) >= 60,
            )
        }
    }

    @Test
    fun `a nearly full day yields a single suggestion`() {
        // 08:00-20:00 solid, leaving one usable evening pocket
        val busy = (8..19).map {
            BusyInterval(it * 60, it * 60 + 55, "Block $it")
        }
        val result = suggest("Dinner", context(busy = busy))
        assertEquals(1, result.size)
    }

    @Test
    fun `an impossible day yields nothing rather than a bad slot`() {
        val busy = listOf(BusyInterval(0, MINUTES_PER_DAY - 1, "All day offsite"))
        assertTrue(suggest("Dinner", context(busy = busy)).isEmpty())
    }

    // hard constraints

    @Test
    fun `nothing is suggested while the user is asleep`() {
        val sleep = SleepWindow(bedMinutes = 22 * 60, wakeMinutes = 8 * 60)
        // deliberately an activity whose window opens before this user wakes up
        val result = suggest("Breakfast", context(sleep = sleep))
        assertTrue(result.isNotEmpty())
        result.forEach {
            assertTrue(
                "${it.startLabel} starts before this user is awake",
                it.startMinutes >= 8 * 60,
            )
            assertTrue(
                "${it.endLabel} runs past bedtime",
                it.endMinutes <= 22 * 60 + 30,
            )
        }
    }

    @Test
    fun `suggestions never overlap something already on the day`() {
        val busy = listOf(
            BusyInterval(18 * 60, 19 * 60, "Standup"),
            BusyInterval(20 * 60, 21 * 60, "Call"),
        )
        suggest("Dinner", context(busy = busy)).forEach { s ->
            busy.forEach { b ->
                assertTrue(
                    "${s.startLabel}–${s.endLabel} collides with ${b.title}",
                    s.endMinutes <= b.startMinutes || s.startMinutes >= b.endMinutes,
                )
            }
        }
    }

    // a timed to-do is a commitment even though it lives in a different list. the user has one
    // day, however many tables the app keeps it in
    @Test
    fun `suggestions avoid timed to-dos as well as events`() {
        val commitments = listOf(
            BusyInterval(18 * 60, 19 * 60, "Standup"),          // calendar event
            BusyInterval(20 * 60, 20 * 60 + 30, "Call pharmacy"), // to-do
        )
        val result = suggest("Dinner", context(busy = commitments))
        assertTrue(result.isNotEmpty())
        result.forEach { s ->
            commitments.forEach { b ->
                assertTrue(
                    "${s.startLabel}–${s.endLabel} collides with ${b.title}",
                    s.endMinutes <= b.startMinutes || s.startMinutes >= b.endMinutes,
                )
            }
        }
    }

    @Test
    fun `a to-do can be the thing a suggestion is chained to`() {
        val busy = listOf(BusyInterval(17 * 60, 17 * 60 + 30, "Call pharmacy"))
        val result = suggest("Errand", context(busy = busy, at = date.atTime(16, 0)))
        assertTrue(result.any { it.reasonText == "Right after Call pharmacy" })
    }

    @Test
    fun `nothing is suggested in the past on today's date`() {
        val afternoon = date.atTime(15, 30)
        suggest("Study", context(at = afternoon)).forEach {
            assertTrue(
                "${it.startLabel} is in the past",
                it.startMinutes >= 15 * 60 + 30,
            )
        }
    }

    @Test
    fun `mode working hours bound the complete suggested event`() {
        val result = suggest(
            "Study",
            context(workingHours = WorkingHours(startMinute = 10 * 60, endMinute = 12 * 60)),
        )

        assertTrue(result.isNotEmpty())
        result.forEach { suggestion ->
            assertTrue("${suggestion.startLabel} starts before mode hours", suggestion.startMinutes >= 10 * 60)
            assertTrue("${suggestion.endLabel} ends after mode hours", suggestion.endMinutes <= 12 * 60)
        }
    }

    @Test
    fun `fallback search cannot escape mode working hours`() {
        // Dinner's normal window starts at 17:00, so this exercises the engine's
        // whole-day fallback while a mode limits usable time to the afternoon.
        val result = suggest(
            "Dinner",
            context(workingHours = WorkingHours(startMinute = 14 * 60, endMinute = 16 * 60)),
        )

        assertTrue(result.isNotEmpty())
        result.forEach { suggestion ->
            assertTrue(suggestion.startMinutes >= 14 * 60)
            assertTrue(suggestion.endMinutes <= 16 * 60)
        }
    }

    @Test
    fun `invalid synced working hours are ignored safely`() {
        assertEquals(null, WorkingHours.fromNullable(startMinute = 17 * 60, endMinute = 9 * 60))
        assertEquals(null, WorkingHours.fromNullable(startMinute = 9 * 60, endMinute = null))
        assertEquals(
            WorkingHours(startMinute = 23 * 60 + 45, endMinute = 24 * 60),
            WorkingHours.fromNullable(startMinute = 23 * 60 + 45, endMinute = 24 * 60),
        )
    }

    @Test
    fun `a future date is offered from the start of the day`() {
        val tomorrow = date.plusDays(1)
        val result = suggest("Breakfast", context(on = tomorrow, at = date.atTime(22, 0)))
        assertTrue(result.isNotEmpty())
        assertTrue(result.first().startMinutes < 12 * 60)
    }

    // ── Personal history ─────────────────────────────────────────────────────

    @Test
    fun `a repeated late dinner moves the suggestion late`() {
        val naive = suggest("Dinner").first().startMinutes
        val learned = suggest(
            "Dinner",
            lookup = lookupWith("title:dinner", 21 * 60, times = 10),
        ).first().startMinutes

        assertTrue(
            "history should pull the suggestion later: ${formatHhMm(naive)} -> ${formatHhMm(learned)}",
            learned > naive,
        )
        assertTrue(
            "expected close to 21:00, got ${formatHhMm(learned)}",
            circularDistance(learned, 21 * 60) <= 45,
        )
    }

    @Test
    fun `learned duration carries over to the proposed end time`() {
        val result = suggest(
            "Dinner",
            lookup = lookupWith("title:dinner", 20 * 60, times = 12, durationMinutes = 30),
        )
        assertTrue(
            "expected a short dinner, got ${result.first().durationMinutes} min",
            result.first().durationMinutes <= 45,
        )
    }

    @Test
    fun `history for the activity family transfers to a new wording`() {
        // Never typed "supper" before, but "dinner" has been eaten at 21:00 for weeks.
        val result = suggest(
            "Supper",
            lookup = lookupWith("kind:dinner", 21 * 60, times = 10),
        )
        assertTrue(
            "expected the family habit to apply, got ${result.first().startLabel}",
            circularDistance(result.first().startMinutes, 21 * 60) <= 60,
        )
    }

    @Test
    fun `a learned habit is explained as such`() {
        val result = suggest("Dinner", lookup = lookupWith("title:dinner", 21 * 60, times = 10))
        assertEquals(SuggestionReason.UsualTime, result.first().reason)
    }

    // ── Energy ───────────────────────────────────────────────────────────────

    @Test
    fun `a heavy day shortens a demanding session`() {
        val rested = suggest("Gym").first().durationMinutes
        val spent = suggest(
            "Gym",
            context(
                energy = EnergySignals(
                    stepsToday = 22_000,
                    stepsDailyAverage = 6_000,
                    lastSleepHours = 4.5,
                    sleepBaselineHours = 8.0,
                ),
                at = date.atTime(16, 0),
            ),
        ).first().durationMinutes

        assertTrue("expected a shorter session when depleted", spent < rested)
    }

    @Test
    fun `tiredness does not shorten a meal`() {
        val rested = suggest("Dinner").first().durationMinutes
        val spent = suggest(
            "Dinner",
            context(
                energy = EnergySignals(
                    stepsToday = 22_000,
                    stepsDailyAverage = 6_000,
                    lastSleepHours = 4.0,
                    sleepBaselineHours = 8.0,
                ),
            ),
        ).first().durationMinutes
        assertEquals(rested, spent)
    }

    @Test
    fun `missing health data is treated as unknown, not as well rested`() {
        assertEquals(
            suggest("Gym").first().startMinutes,
            suggest("Gym", context(energy = EnergySignals())).first().startMinutes,
        )
    }

    @Test
    fun `demanding work is kept out of the wind-down hour`() {
        val sleep = SleepWindow(bedMinutes = 23 * 60, wakeMinutes = 7 * 60)
        suggest("Deep work", context(sleep = sleep)).forEach {
            assertTrue(
                "${it.endLabel} eats into the wind-down before a 23:00 bedtime",
                it.endMinutes <= 22 * 60,
            )
        }
    }

    // ── Preferences ──────────────────────────────────────────────────────────

    @Test
    fun `the productive window breaks ties for demanding work`() {
        val morning = suggest("Deep work", context(productiveTime = ProductiveTime.Morning))
            .first().startMinutes
        val evening = suggest("Deep work", context(productiveTime = ProductiveTime.Evening))
            .first().startMinutes
        assertTrue(
            "morning=${formatHhMm(morning)} evening=${formatHhMm(evening)}",
            evening > morning,
        )
    }

    /** The survey answer informs the guess; it must not overrule observed behaviour. */
    @Test
    fun `observed history outranks the productive-window answer`() {
        val result = suggest(
            "Deep work",
            context(productiveTime = ProductiveTime.Evening),
            lookup = lookupWith("title:deep work", 9 * 60, times = 15),
        )
        assertTrue(
            "expected the observed 09:00 habit to win, got ${result.first().startLabel}",
            circularDistance(result.first().startMinutes, 9 * 60) <= 60,
        )
    }

    /** A night owl doesn't eat dinner later — the chronotype only moves flexible work. */
    @Test
    fun `the productive window does not move anchored activities`() {
        assertEquals(
            suggest("Dinner").first().startMinutes,
            suggest("Dinner", context(productiveTime = ProductiveTime.Night)).first().startMinutes,
        )
    }

    @Test
    fun `a slot butting onto an existing event says so`() {
        val busy = listOf(BusyInterval(17 * 60, 18 * 60, "Standup"))
        val result = suggest("Errand", context(busy = busy, at = date.atTime(16, 0)))
        assertTrue(result.any { it.reason == SuggestionReason.AfterEvent })
    }

    @Test
    fun `every suggestion carries a reason the user can read`() {
        suggest("Gym").forEach {
            assertTrue(it.reasonText.isNotBlank())
        }
    }

    @Test
    fun `round times are preferred over quarter-past ones`() {
        // with nothing else to separate them, the top pick should land on the hour
        val result = suggest("Dinner").first()
        assertEquals(0, result.startMinutes % 30)
    }

    // the long tail: titles no lexicon will ever contain

    // an early-rising user whose whole calendar sits in the morning shouldn't be offered
    // mid-afternoon for a title we can't identify
    @Test
    fun `an unknown title borrows the user's own scheduling hours`() {
        val earlyRiser = HabitStatLookup { key, _ ->
            if (key != HabitKey.ALL_EVENTS) null
            else (1..40).fold(HabitTimeStat()) { acc, i ->
                // A spread of real morning events, 07:00–11:00.
                acc.observe(7 * 60 + (i * 6) % 240, 45, nowMillis)
            }
        }
        val generic = suggest("Recital").first().startMinutes
        val personal = suggest("Recital", lookup = earlyRiser).first().startMinutes

        assertTrue(
            "baseline should pull an unknown title into this user's morning, " +
                "got ${formatHhMm(personal)} (generic was ${formatHhMm(generic)})",
            personal < generic,
        )
        assertTrue("got ${formatHhMm(personal)}", personal < 13 * 60)
    }

    // the baseline is a region of the day, so it must not pose as a habit
    @Test
    fun `the pooled baseline never claims to be a usual time`() {
        val pooled = HabitStatLookup { key, _ ->
            if (key != HabitKey.ALL_EVENTS) null
            else (1..60).fold(HabitTimeStat()) { acc, _ ->
                acc.observe(10 * 60, 45, nowMillis)
            }
        }
        suggest("Kids recital", lookup = pooled).forEach {
            assertTrue(
                "reason was ${it.reason}",
                it.reason != SuggestionReason.UsualTime,
            )
        }
    }

    // a recognised activity has a real prior, the scheduling average is worse
    @Test
    fun `a recognised activity ignores the pooled baseline`() {
        val nightOwlBaseline = HabitStatLookup { key, _ ->
            if (key != HabitKey.ALL_EVENTS) null
            else (1..60).fold(HabitTimeStat()) { acc, _ ->
                acc.observe(21 * 60, 45, nowMillis)
            }
        }
        assertEquals(
            suggest("Breakfast").first().startMinutes,
            suggest("Breakfast", lookup = nightOwlBaseline).first().startMinutes,
        )
    }

    // one or two events are not a scheduling pattern
    @Test
    fun `a thin baseline does not move anything`() {
        val thin = HabitStatLookup { key, _ ->
            if (key != HabitKey.ALL_EVENTS) null
            else HabitTimeStat().observe(7 * 60, 45, nowMillis)
        }
        assertEquals(
            suggest("Recital").first().startMinutes,
            suggest("Recital", lookup = thin).first().startMinutes,
        )
    }

    // the specific habit always outranks the pooled average
    @Test
    fun `a learned title beats the pooled baseline`() {
        val both = HabitStatLookup { key, _ ->
            when (key) {
                "title:recital" ->
                    (1..8).fold(HabitTimeStat()) { acc, _ -> acc.observe(16 * 60, 45, nowMillis) }
                HabitKey.ALL_EVENTS ->
                    (1..60).fold(HabitTimeStat()) { acc, _ -> acc.observe(9 * 60, 45, nowMillis) }
                else -> null
            }
        }
        val result = suggest("Recital", lookup = both).first()
        assertTrue(
            "expected the learned 16:00, got ${result.startLabel}",
            circularDistance(result.startMinutes, 16 * 60) <= 45,
        )
        assertEquals(SuggestionReason.UsualTime, result.reason)
    }

    // across days

    // future days carry no energy signals, tomorrow's step count doesn't exist
    private fun laterDays(
        from: LocalDate = date,
        count: Int = 2,
        busy: Map<LocalDate, List<BusyInterval>> = emptyMap(),
    ) = (1..count).map { offset ->
        val day = from.plusDays(offset.toLong())
        context(busy = busy[day].orEmpty(), on = day, at = now)
    }

    private fun suggestAcross(
        title: String,
        today: DayContext = context(),
        later: List<DayContext> = laterDays(),
        lookup: HabitStatLookup = emptyLookup,
    ) = TimeSuggestionEngine.suggestAcrossDays(title, "", today, later, lookup)

    @Test
    fun `an ordinary day is not second-guessed`() {
        val result = suggestAcross("Gym")
        assertTrue(
            "nothing should be pushed to another day on a normal, open day",
            result.all { it.date == date },
        )
    }

    @Test
    fun `a depleted day offers tomorrow as well`() {
        val spent = context(
            energy = EnergySignals(
                stepsToday = 24_000,
                stepsDailyAverage = 6_000,
                lastSleepHours = 4.0,
                sleepBaselineHours = 8.0,
            ),
            at = date.atTime(16, 0),
        )
        val result = suggestAcross("Gym", today = spent)

        val moved = result.filter { it.date != date }
        assertEquals("expected exactly one cross-day option", 1, moved.size)
        assertEquals(SuggestionReason.BetterAnotherDay, moved.single().reason)
        assertEquals("You'll have more in the tank", moved.single().reasonText)
        // the day being edited still leads, moving is offered and never imposed
        assertEquals(date, result.first().date)
    }

    @Test
    fun `a full day hands the whole suggestion to tomorrow`() {
        val packed = context(busy = listOf(BusyInterval(0, MINUTES_PER_DAY - 1, "Offsite")))
        val result = suggestAcross("Gym", today = packed)

        assertEquals(1, result.size)
        assertEquals(date.plusDays(1), result.single().date)
        assertEquals(SuggestionReason.BetterAnotherDay, result.single().reason)
    }

    @Test
    fun `tomorrow is checked against tomorrow's real commitments`() {
        val packed = context(busy = listOf(BusyInterval(0, MINUTES_PER_DAY - 1, "Offsite")))
        val tomorrow = date.plusDays(1)
        val result = suggestAcross(
            "Gym",
            today = packed,
            later = laterDays(
                busy = mapOf(tomorrow to listOf(BusyInterval(0, MINUTES_PER_DAY - 1, "Also busy"))),
            ),
        )
        // Tomorrow is full too, so it must fall through to the day after rather
        // than proposing a slot that collides with an all-day commitment.
        assertEquals(date.plusDays(2), result.single().date)
    }

    @Test
    fun `nothing is offered when no day in the window has room`() {
        val allDay = listOf(BusyInterval(0, MINUTES_PER_DAY - 1, "Booked"))
        val result = suggestAcross(
            "Gym",
            today = context(busy = allDay),
            later = laterDays(
                busy = (1..2).associate { date.plusDays(it.toLong()) to allDay },
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `the strip never grows past three chips`() {
        val spent = context(
            energy = EnergySignals(
                stepsToday = 24_000,
                stepsDailyAverage = 6_000,
                lastSleepHours = 4.0,
                sleepBaselineHours = 8.0,
            ),
        )
        assertTrue(suggestAcross("Study", today = spent).size <= 3)
    }

    @Test
    fun `a cross-day suggestion labels the day it moves to`() {
        val packed = context(busy = listOf(BusyInterval(0, MINUTES_PER_DAY - 1, "Offsite")))
        val moved = suggestAcross("Gym", today = packed).single()
        // `date` is both the day on screen and the engine's today here, which is
        // the one case where Tomorrow is the correct word
        assertEquals("Tomorrow", moved.dayLabel(date, today = date))
        assertTrue(moved.movesDay(date))
    }

    @Test
    fun `a same-day suggestion carries no day label`() {
        val s = suggestAcross("Dinner").first()
        assertEquals(null, s.dayLabel(date, today = date))
        assertEquals(false, s.movesDay(date))
    }

    // a connection's shared availability. opaque blocks: hours we know, contents we don't and
    // mustn't imply

    @Test
    fun `an opaque block is avoided like any other`() {
        val theirs = BusyInterval(12 * 60, 13 * 60, BusyInterval.SHARED_LABEL, opaque = true)
        val result = suggest("Lunch", context(busy = listOf(theirs)))
        assertTrue(result.isNotEmpty())
        assertTrue(
            "12:00-13:00 is taken on their side and must not be proposed",
            result.none { it.startMinutes < 13 * 60 && 12 * 60 < it.endMinutes },
        )
    }

    @Test
    fun `an opaque block is never named in a reason`() {
        // the same arrangement as the chaining test above, where the engine demonstrably wants to say
        // the block's name, except this block is not ours to talk about
        val theirs = BusyInterval(17 * 60, 17 * 60 + 30, "Therapy", opaque = true)
        val result = suggest("Errand", context(busy = listOf(theirs), at = date.atTime(16, 0)))
        assertTrue(result.isNotEmpty())
        assertTrue(
            "A borrowed block's title must never reach a chip",
            result.none { it.reasonText.contains("Therapy") },
        )
        assertTrue(result.none { it.reason == SuggestionReason.AfterEvent })
    }

    @Test
    fun `a block of my own is still named`() {
        val mine = BusyInterval(17 * 60, 17 * 60 + 30, "Standup")
        val result = suggest("Errand", context(busy = listOf(mine), at = date.atTime(16, 0)))
        assertTrue(result.any { it.reasonText == "Right after Standup" })
    }

    @Test
    fun `a slot clear on both sides says so`() {
        val theirs = BusyInterval(9 * 60, 11 * 60, BusyInterval.SHARED_LABEL, opaque = true)
        val result = suggest("Nail appointment", context(busy = listOf(theirs)))
        assertTrue(result.any { it.reason == SuggestionReason.FreeForBoth })
    }

    @Test
    fun `nothing claims to be free for both when only my day is known`() {
        val mine = BusyInterval(9 * 60, 11 * 60, "Standup")
        val result = suggest("Nail appointment", context(busy = listOf(mine)))
        assertTrue(result.none { it.reason == SuggestionReason.FreeForBoth })
    }
}
