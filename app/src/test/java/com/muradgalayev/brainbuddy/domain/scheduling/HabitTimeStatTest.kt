package com.muradgalayev.brainbuddy.domain.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTimeStatTest {

    private val day = 24 * 60 * 60 * 1000L
    private val t0 = 1_770_000_000_000L

    private fun stat(vararg times: Int, at: Long = t0): HabitTimeStat =
        times.fold(HabitTimeStat()) { acc, m ->
            acc.observe(startMinutes = m, durationMinutes = 60, atMillis = at)
        }

    @Test
    fun `mean of a consistent habit is that time`() {
        val s = stat(19 * 60, 19 * 60, 19 * 60)
        assertEquals(19 * 60, s.meanMinutes)
    }

    @Test
    fun `mean averages within the day`() {
        val s = stat(18 * 60, 20 * 60)
        assertEquals(19 * 60, s.meanMinutes)
    }

    // the reason the sums are circular at all. arithmetic averaging of 23:40 and 00:20 gives noon,
    // the opposite side of the clock from the right answer
    @Test
    fun `mean wraps correctly across midnight`() {
        val s = stat(23 * 60 + 40, 20)
        assertEquals(0, s.meanMinutes)
    }

    @Test
    fun `consistency separates a routine from a scatter`() {
        val tight = stat(19 * 60, 19 * 60 + 10, 18 * 60 + 50)
        val loose = stat(8 * 60, 14 * 60, 21 * 60)
        assertTrue(tight.consistency > 0.95f)
        assertTrue(loose.consistency < 0.5f)
        assertTrue(tight.spreadMinutes < loose.spreadMinutes)
    }

    @Test
    fun `empty stat reports nothing rather than a default time`() {
        val empty = HabitTimeStat()
        assertNull(empty.meanMinutes)
        assertNull(empty.meanDurationMinutes)
        assertEquals(false, empty.hasData)
    }

    @Test
    fun `recency decay lets a changed routine take over`() {
        // six months of 18:00 dinners, then a fortnight of 21:00 ones
        var s = HabitTimeStat()
        repeat(20) { i ->
            s = s.observe(18 * 60, 60, atMillis = t0 - (200L - i * 7) * day)
        }
        repeat(6) { i ->
            s = s.observe(21 * 60, 60, atMillis = t0 - (14L - i * 2) * day)
        }
        val mean = s.decayedTo(t0).meanMinutes!!
        assertTrue("expected a shift off the old 18:00, got ${formatHhMm(mean)}", mean > 19 * 60)
        assertTrue(
            "expected the recent 21:00 pattern to dominate, got ${formatHhMm(mean)}",
            circularDistance(mean, 21 * 60) < circularDistance(mean, 18 * 60),
        )
    }

    @Test
    fun `decay never resurrects weight`() {
        val fresh = stat(12 * 60, 12 * 60)
        val aged = fresh.decayedTo(t0 + 400 * day)
        assertTrue(aged.weightSum < fresh.weightSum)
    }

    @Test
    fun `blend starts at the prior and ends at the user`() {
        val kind = ActivityKind.Dinner
        val none = blendWithPrior(null, kind)
        assertEquals(kind.priorPeak, none.peakMinutes)
        assertEquals(0f, none.personalWeight, 0.001f)

        val single = blendWithPrior(stat(21 * 60), kind)
        assertTrue(
            "one observation should nudge, not decide",
            single.peakMinutes in (kind.priorPeak + 1) until (21 * 60),
        )

        val many = blendWithPrior(stat(*IntArray(12) { 21 * 60 }), kind)
        assertTrue(
            "twelve observations should land near 21:00, got ${formatHhMm(many.peakMinutes)}",
            circularDistance(many.peakMinutes, 21 * 60) <= 30,
        )
        assertTrue(many.personalWeight > 0.75f)
    }

    // the same number of observations shouldn't carry the same authority when they disagree with
    // each other. eight identical dinners is a routine, eight scattered across six hours is a
    // person with no routine, and the built-in prior is the better guess for them
    @Test
    fun `scattered history is trusted less than consistent history`() {
        val consistent = stat(*IntArray(8) { 21 * 60 })
        val scattered = stat(17 * 60, 18 * 60, 19 * 60, 20 * 60, 21 * 60, 22 * 60, 19 * 60, 20 * 60)

        val tight = blendWithPrior(consistent, ActivityKind.Dinner)
        val loose = blendWithPrior(scattered, ActivityKind.Dinner)

        assertTrue(
            "consistent=${tight.personalWeight} scattered=${loose.personalWeight}",
            tight.personalWeight > loose.personalWeight + 0.2f,
        )
        assertTrue("a firm routine should be taken at its word", tight.personalWeight > 0.85f)
    }

    // eight identical observations should mean eight identical observations
    @Test
    fun `a firm routine is not dragged back toward the textbook`() {
        val prior = blendWithPrior(stat(*IntArray(8) { 16 * 60 }), ActivityKind.Appointment)
        assertTrue(
            "expected close to 16:00, got ${formatHhMm(prior.peakMinutes)}",
            circularDistance(prior.peakMinutes, 16 * 60) <= 30,
        )
    }

    // one data point has no measurable spread, so it must not look like certainty
    @Test
    fun `a single observation nudges rather than decides`() {
        val prior = blendWithPrior(stat(21 * 60), ActivityKind.Dinner)
        assertTrue("one observation shouldn't take over", prior.personalWeight < 0.5f)
        assertTrue(
            "but it should still move things, got ${formatHhMm(prior.peakMinutes)}",
            prior.peakMinutes > ActivityKind.Dinner.priorPeak,
        )
    }

    // a single mistyped time must not redefine what dinner is
    @Test
    fun `blended peak is clamped into the activity window`() {
        val absurd = blendWithPrior(stat(*IntArray(30) { 4 * 60 }), ActivityKind.Dinner)
        assertTrue(
            "got ${formatHhMm(absurd.peakMinutes)}",
            absurd.peakMinutes in ActivityKind.Dinner.windowStart..ActivityKind.Dinner.windowEnd,
        )
    }

    @Test
    fun `circular distance takes the short way round`() {
        assertEquals(20, circularDistance(23 * 60 + 50, 10))
        assertEquals(120, circularDistance(10 * 60, 12 * 60))
        assertEquals(0, circularDistance(60, 60))
    }
}
