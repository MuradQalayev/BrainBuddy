package com.muradgalayev.brainbuddy.data.local

import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PomodoroTimerStateTest {

    @Test
    fun `completed standalone timer is re-primed before a new launch`() {
        val completed = PomodoroTimerState(
            timerState = TimerState.COMPLETED,
            sessionType = PomodoroSessionType.FOCUS,
            totalDurationMs = 10 * 60 * 1000L,
            remainingMs = 0L,
            progress = 1f,
            resetCount = 2,
            extraTimeAddedMs = 60_000L,
            completedSessions = 3,
            focusModeActive = true,
            selectedAmbientSound = AmbientSound.RAIN,
        )

        val primed = requireNotNull(
            prepareStandaloneTimerState(
                current = completed,
                hasQueue = false,
                defaultDurationMs = PomodoroTimerManager.FOCUS_DURATION_MS,
            )
        )

        assertEquals(TimerState.IDLE, primed.timerState)
        assertEquals(PomodoroTimerManager.FOCUS_DURATION_MS, primed.totalDurationMs)
        assertEquals(PomodoroTimerManager.FOCUS_DURATION_MS, primed.remainingMs)
        assertEquals(0f, primed.progress)
        assertEquals(0, primed.resetCount)
        assertEquals(0L, primed.extraTimeAddedMs)
        assertEquals(3, primed.completedSessions)
        assertEquals(AmbientSound.RAIN, primed.selectedAmbientSound)
    }

    @Test
    fun `loaded calendar queue is never replaced by a standalone launch`() {
        assertNull(
            prepareStandaloneTimerState(
                current = PomodoroTimerState(timerState = TimerState.COMPLETED),
                hasQueue = true,
                defaultDurationMs = PomodoroTimerManager.FOCUS_DURATION_MS,
            )
        )
    }

    @Test
    fun `running and paused timers cannot be re-primed`() {
        listOf(TimerState.RUNNING, TimerState.PAUSED).forEach { timerState ->
            assertNull(
                prepareStandaloneTimerState(
                    current = PomodoroTimerState(timerState = timerState),
                    hasQueue = false,
                    defaultDurationMs = PomodoroTimerManager.FOCUS_DURATION_MS,
                )
            )
        }
    }
}
