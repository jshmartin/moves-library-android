package com.example.myapplication.ui.practice

import com.example.myapplication.data.model.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundTimerTest {
    private var now = 1_000L
    private val settings = TimerSettings(rounds = 2, activeSec = 60, restSec = 30)

    private fun timer(settings: TimerSettings = this.settings, getReadySec: Int = 10) =
        RoundTimer(settings, getReadySec, clock = { now })

    /** Moves the clock in small steps, as the screen does, and returns every cue that fired. */
    private fun RoundTimer.runFor(ms: Long): List<TimerCue> {
        val cues = mutableListOf<TimerCue>()
        var left = ms
        while (left > 0) {
            val step = minOf(left, 100L)
            now += step
            left -= step
            tick().cue?.let(cues::add)
        }
        return cues
    }

    @Test
    fun timelineRunsGetReadyThenActiveAndRestWithNoRestAfterTheLastRound() {
        val timeline = TimerTimeline(settings, getReadySec = 10)

        assertEquals(TimerPosition(TimerPhase.GET_READY, 1, 10_000, 10_000), timeline.positionAt(0))
        assertEquals(TimerPosition(TimerPhase.ACTIVE, 1, 60_000, 60_000), timeline.positionAt(10_000))
        assertEquals(TimerPosition(TimerPhase.REST, 1, 30_000, 30_000), timeline.positionAt(70_000))
        assertEquals(TimerPosition(TimerPhase.ACTIVE, 2, 60_000, 60_000), timeline.positionAt(100_000))
        assertEquals(TimerPhase.DONE, timeline.positionAt(160_000).phase)
        assertEquals(160_000, timeline.totalMs)
    }

    @Test
    fun zeroRestGoesStraightToTheNextRoundAndRingsIt() {
        val timer = timer(TimerSettings(rounds = 2, activeSec = 20, restSec = 0), getReadySec = 0)
        timer.start()

        val cues = timer.runFor(20_100)

        assertEquals(listOf(TimerCue.ROUND_START), cues)
        assertEquals(TimerPhase.ACTIVE, timer.tick().position.phase)
    }

    @Test
    fun aFullRunRingsEveryBoxingCueInOrder() {
        val timer = timer()
        timer.start()

        val cues = timer.runFor(160_000)

        assertEquals(
            listOf(
                TimerCue.COUNTDOWN, TimerCue.COUNTDOWN, TimerCue.COUNTDOWN, // get ready
                TimerCue.ROUND_START,
                TimerCue.WARNING,
                TimerCue.ROUND_END,
                TimerCue.COUNTDOWN, TimerCue.COUNTDOWN, TimerCue.COUNTDOWN, // rest
                TimerCue.ROUND_START,
                TimerCue.WARNING,
                TimerCue.ROUND_END,
            ),
            cues,
        )
        assertFalse(timer.isRunning)
    }

    @Test
    fun shortRoundsGetNoTenSecondWarning() {
        val timer = timer(TimerSettings(rounds = 1, activeSec = 20, restSec = 0), getReadySec = 0)
        timer.start()

        assertFalse(TimerCue.WARNING in timer.runFor(15_000))
    }

    @Test
    fun pausingFreezesTheClockAndResumingContinues() {
        val timer = timer(getReadySec = 0)
        timer.start()
        timer.runFor(5_000)
        timer.pause()

        now += 60_000
        assertEquals(55_000, timer.tick().position.remainingMs)
        assertTrue(timer.hasStarted)

        timer.start()
        timer.runFor(1_000)
        assertEquals(54_000, timer.tick().position.remainingMs)
    }

    @Test
    fun aLongFreezeLandsOnTheRightPhaseAndCountsTheRoundsItCrossed() {
        val timer = timer(TimerSettings(rounds = 3, activeSec = 30, restSec = 30), getReadySec = 0)
        timer.start()

        now += 95_000
        val tick = timer.tick()

        assertEquals(TimerPhase.REST, tick.position.phase)
        assertEquals(2, tick.position.round)
        assertEquals(2, tick.roundsFinished)
        assertEquals(TimerCue.ROUND_END, tick.cue)
    }

    @Test
    fun finishingARoundIsCountedOnce() {
        val timer = timer(getReadySec = 0)
        timer.start()
        now += 60_000

        assertEquals(1, timer.tick().roundsFinished)
        assertEquals(0, timer.tick().roundsFinished)
    }

    @Test
    fun skippingARoundMovesOnAndRingsButDoesNotCountIt() {
        val timer = timer(getReadySec = 0)
        timer.start()
        timer.runFor(5_000)

        timer.skip()
        val tick = timer.tick()

        assertEquals(TimerPhase.REST, tick.position.phase)
        assertEquals(TimerCue.ROUND_END, tick.cue)
        assertEquals(0, tick.roundsFinished)
    }

    @Test
    fun skippingTheLastPhaseFinishes() {
        val timer = timer(TimerSettings(rounds = 1, activeSec = 30, restSec = 0), getReadySec = 0)
        timer.start()

        timer.skip()

        assertEquals(TimerPhase.DONE, timer.tick().position.phase)
        assertFalse(timer.isRunning)
    }

    @Test
    fun resetGoesBackToTheStartSilently() {
        val timer = timer()
        timer.start()
        timer.runFor(30_000)

        timer.reset()
        val tick = timer.tick()

        assertFalse(timer.hasStarted)
        assertEquals(TimerPhase.GET_READY, tick.position.phase)
        assertEquals(10_000, tick.position.remainingMs)
        assertNull(tick.cue)
    }

    @Test
    fun startingAfterDoneRunsAgainFromTheTop() {
        val timer = timer(TimerSettings(rounds = 1, activeSec = 30, restSec = 0), getReadySec = 0)
        timer.start()
        timer.runFor(30_000)
        assertFalse(timer.isRunning)

        timer.start()

        assertTrue(timer.isRunning)
        assertEquals(30_000, timer.tick().position.remainingMs)
    }

    @Test
    fun newSettingsStartTheTimerOver() {
        val timer = timer(getReadySec = 0)
        timer.start()
        timer.runFor(5_000)

        timer.applySettings(TimerSettings(rounds = 1, activeSec = 45, restSec = 0))

        assertFalse(timer.isRunning)
        assertEquals(45_000, timer.tick().position.remainingMs)
    }
}
