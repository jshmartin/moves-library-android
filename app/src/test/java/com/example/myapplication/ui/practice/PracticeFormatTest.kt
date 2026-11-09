package com.example.myapplication.ui.practice

import com.example.myapplication.data.dao.SessionSummary
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeFormatTest {
    private val day = 24 * 60 * 60 * 1000L

    private fun session(startedAt: Long, minutes: Long, rounds: Int = 0, sets: Int = 0, reps: Int = 0) = SessionSummary(
        session = PracticeSession(
            name = "s",
            startedAt = startedAt,
            endedAt = startedAt + minutes * 60_000,
            roundsCompleted = rounds,
        ),
        setCount = sets,
        repCount = reps,
    )

    @Test
    fun targetsReadAsSetsAndReps() {
        assertEquals("3 sets × 10 reps", targetText(3, 10))
        assertEquals("1 set × 1 rep", targetText(1, 1))
        assertEquals("2 sets", targetText(2, null))
    }

    @Test
    fun timerSummaryNamesRoundsAndBothPhases() {
        assertEquals("5 rounds · 1:00 active · 0:30 rest", timerText(TimerSettings(5, 60, 30)))
    }

    @Test
    fun durationStepsAreFineUnderAMinuteAndCoarseAboveIt() {
        assertEquals(35, stepDuration(30, up = true))
        assertEquals(75, stepDuration(60, up = true))
        assertEquals(55, stepDuration(60, up = false))
        assertEquals(60, stepDuration(75, up = false))
    }

    @Test
    fun logTotalsSeparateTheLastWeekFromAllTime() {
        val now = 100 * day
        val totals = logTotals(
            listOf(session(now - day, minutes = 30), session(now - 30 * day, minutes = 90)),
            now,
        )

        assertEquals(LogTotals(1, 30 * 60_000L, 2, 120 * 60_000L), totals)
    }

    @Test
    fun sessionStatsLeaveOutWhatWasNotCounted() {
        assertEquals("2 rounds · 1 set · 10 reps", session(0, 1, rounds = 2, sets = 1, reps = 10).statsText())
        assertEquals("3 rounds", session(0, 1, rounds = 3).statsText())
        assertEquals("Nothing counted", session(0, 1).statsText())
    }

    @Test
    fun longTotalsReadAsHoursAndMinutes() {
        assertEquals("45m", hoursAndMinutes(45 * 60_000L))
        assertEquals("2h 5m", hoursAndMinutes(125 * 60_000L))
    }
}
