package com.example.myapplication.ui.practice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The timer's sounds, synthesized so the app ships no audio files. Cues play on the media stream
 * without taking audio focus, so they sound over whatever music is already playing.
 */
class TimerSounds {
    private val lazyTracks = lazy { TimerCue.entries.associateWith { buildTrack(samplesFor(it)) } }

    /** Builds the sounds ahead of time so the first bell isn't late. Safe off the main thread. */
    fun prepare() {
        lazyTracks.value
    }

    fun play(cue: TimerCue) {
        val track = lazyTracks.value.getValue(cue)
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() {
        if (lazyTracks.isInitialized()) lazyTracks.value.values.forEach(AudioTrack::release)
    }

    private fun buildTrack(samples: ShortArray): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
        .build()
        .apply { write(samples, 0, samples.size) }

    private fun samplesFor(cue: TimerCue): ShortArray = when (cue) {
        TimerCue.ROUND_START -> render(BELL, strikes = 1, gapSec = 0.0, ringSec = 1.3)
        TimerCue.ROUND_END -> render(BELL, strikes = 3, gapSec = 0.28, ringSec = 1.3)
        TimerCue.WARNING -> render(CLACK, strikes = 2, gapSec = 0.16, ringSec = 0.1)
        TimerCue.COUNTDOWN -> render(TICK, strikes = 1, gapSec = 0.0, ringSec = 0.12)
    }

    private class Partial(val hz: Double, val level: Double, val decaySec: Double)

    private fun render(partials: List<Partial>, strikes: Int, gapSec: Double, ringSec: Double): ShortArray {
        val length = (((strikes - 1) * gapSec + ringSec) * SAMPLE_RATE).toInt()
        val mix = DoubleArray(length)
        repeat(strikes) { strike ->
            val start = (strike * gapSec * SAMPLE_RATE).toInt()
            for (i in start until length) {
                val t = (i - start).toDouble() / SAMPLE_RATE
                mix[i] += partials.sumOf { it.level * exp(-t / it.decaySec) * sin(2 * PI * it.hz * t) }
            }
        }
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        return ShortArray(length) { (mix[it] / peak * 0.9 * Short.MAX_VALUE).toInt().toShort() }
    }

    private companion object {
        const val SAMPLE_RATE = 22_050

        // Inharmonic overtones are what make it read as struck metal rather than a beep.
        val BELL = listOf(
            Partial(1_250.0, 1.0, 0.45),
            Partial(2_510.0, 0.55, 0.28),
            Partial(3_450.0, 0.4, 0.18),
            Partial(4_910.0, 0.2, 0.09),
        )
        val CLACK = listOf(Partial(1_900.0, 1.0, 0.018), Partial(3_300.0, 0.7, 0.012))
        val TICK = listOf(Partial(1_000.0, 1.0, 0.04))
    }
}
