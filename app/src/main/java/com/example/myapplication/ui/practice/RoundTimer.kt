package com.example.myapplication.ui.practice

import com.example.myapplication.data.model.TimerSettings

enum class TimerPhase { GET_READY, ACTIVE, REST, DONE }

/** A moment in a timer run. During REST, [round] is the round that just ended. */
data class TimerPosition(
    val phase: TimerPhase,
    val round: Int,
    val remainingMs: Long,
    val phaseMs: Long,
)

enum class TimerCue { ROUND_START, ROUND_END, WARNING, COUNTDOWN }

/** What changed since the last tick. */
data class TimerTick(
    val position: TimerPosition,
    val cue: TimerCue?,
    /** Rounds whose active phase ran to its end since the last tick. */
    val roundsFinished: Int,
)

private data class Segment(val phase: TimerPhase, val round: Int, val ms: Long)

/** The fixed run of phases for [settings]. Everything else is derived from elapsed time. */
class TimerTimeline(val settings: TimerSettings, getReadySec: Int = GET_READY_SEC) {
    private val segments: List<Segment> = buildList {
        if (getReadySec > 0) add(Segment(TimerPhase.GET_READY, 1, getReadySec * 1000L))
        for (round in 1..settings.rounds) {
            add(Segment(TimerPhase.ACTIVE, round, settings.activeSec * 1000L))
            if (round < settings.rounds && settings.restSec > 0) {
                add(Segment(TimerPhase.REST, round, settings.restSec * 1000L))
            }
        }
    }

    val totalMs: Long = segments.sumOf { it.ms }

    fun positionAt(elapsedMs: Long): TimerPosition {
        var end = 0L
        for (segment in segments) {
            end += segment.ms
            if (elapsedMs < end) return TimerPosition(segment.phase, segment.round, end - elapsedMs, segment.ms)
        }
        return TimerPosition(TimerPhase.DONE, settings.rounds, 0, 0)
    }

    fun roundsFinishedAt(elapsedMs: Long): Int {
        var end = 0L
        return segments.count { segment ->
            end += segment.ms
            segment.phase == TimerPhase.ACTIVE && elapsedMs >= end
        }
    }

    /** When the phase running at [elapsedMs] ends. */
    fun phaseEndAfter(elapsedMs: Long): Long {
        var end = 0L
        for (segment in segments) {
            end += segment.ms
            if (elapsedMs < end) return end
        }
        return totalMs
    }

    companion object {
        const val GET_READY_SEC = 10
    }
}

/**
 * A boxing-style round timer. It only remembers how long it has run, so a late tick or a frozen
 * app can't make it drift: the next tick just lands at the right place. [clock] is monotonic.
 */
class RoundTimer(
    settings: TimerSettings,
    private val getReadySec: Int = TimerTimeline.GET_READY_SEC,
    private val clock: () -> Long,
) {
    private var timeline = TimerTimeline(settings, getReadySec)
    private var bankedMs = 0L
    private var resumedAt: Long? = null
    private var lastElapsedMs = 0L
    private var lastPosition = timeline.positionAt(0)

    val settings: TimerSettings get() = timeline.settings
    val isRunning: Boolean get() = resumedAt != null
    val hasStarted: Boolean get() = isRunning || bankedMs > 0

    private val elapsedMs: Long get() = bankedMs + (resumedAt?.let { clock() - it } ?: 0)

    /** Starts, resumes after a pause, or runs again once done. */
    fun start() {
        if (isRunning) return
        if (timeline.positionAt(elapsedMs).phase == TimerPhase.DONE) reset()
        resumedAt = clock()
    }

    fun pause() {
        bankedMs = elapsedMs
        resumedAt = null
    }

    fun reset() {
        bankedMs = 0
        resumedAt = null
        lastElapsedMs = 0
        lastPosition = timeline.positionAt(0)
    }

    /** Jumps to the next phase. The bell still rings, but a skipped round doesn't count as finished. */
    fun skip() {
        val target = timeline.phaseEndAfter(elapsedMs)
        bankedMs = target
        if (isRunning) resumedAt = clock()
        lastElapsedMs = target
    }

    fun applySettings(settings: TimerSettings) {
        timeline = TimerTimeline(settings, getReadySec)
        reset()
    }

    fun tick(): TimerTick {
        val elapsed = elapsedMs
        val position = timeline.positionAt(elapsed)
        val tick = TimerTick(
            position = position,
            cue = cueBetween(lastPosition, position),
            roundsFinished = timeline.roundsFinishedAt(elapsed) - timeline.roundsFinishedAt(lastElapsedMs),
        )
        lastElapsedMs = elapsed
        lastPosition = position
        if (position.phase == TimerPhase.DONE && isRunning) {
            bankedMs = timeline.totalMs
            resumedAt = null
        }
        return tick
    }

    private fun cueBetween(before: TimerPosition, after: TimerPosition): TimerCue? {
        fun crossed(mark: Long) = before.remainingMs > mark && after.remainingMs <= mark
        return when {
            before.phase != after.phase || before.round != after.round -> when (after.phase) {
                TimerPhase.ACTIVE -> TimerCue.ROUND_START
                TimerPhase.REST, TimerPhase.DONE -> TimerCue.ROUND_END
                TimerPhase.GET_READY -> null
            }
            after.phase == TimerPhase.ACTIVE ->
                TimerCue.WARNING.takeIf { after.phaseMs >= MIN_MS_FOR_WARNING && crossed(WARNING_MS) }
            after.phase != TimerPhase.DONE && COUNTDOWN_MARKS.any { crossed(it) } -> TimerCue.COUNTDOWN
            else -> null
        }
    }

    private companion object {
        const val WARNING_MS = 10_000L
        const val MIN_MS_FOR_WARNING = 30_000L
        val COUNTDOWN_MARKS = listOf(3_000L, 2_000L, 1_000L)
    }
}
