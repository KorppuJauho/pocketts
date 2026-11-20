package io.github.korppujauho.pocketts

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * What the microphone heard, for the log every [frames] frames (30 s): the loudest moment, how much was exact digital
 * silence (all zeros, which is what Android gives an app it has silenced, unlike a quiet room) and how long voice went
 * out. Only numbers: never the sound.
 */
class MicSummary(private val frames: Int = 1500, private val frameMs: Int = 20) {
    private var count = 0
    private var peak = 0
    private var zero = 0
    private var sent = 0

    /** One frame as recorded, and whether voice went out with it; the summary when a period is full, else null. */
    fun add(pcm: ShortArray, sending: Boolean): String? {
        var max = 0
        for (s in pcm) {
            val a = abs(s.toInt())
            if (a > max) max = a
        }
        if (max > peak) peak = max
        if (max == 0) zero++
        if (sending) sent++
        if (++count < frames) return null
        val loudest = if (peak == 0) "silent" else "%d dBFS".format((20 * log10(peak / 32768.0)).roundToInt())
        val line = "Microphone in %d s: loudest %s, digital silence %d %%, voice sent %d s".format(
            frames * frameMs / 1000, loudest, zero * 100 / count, sent * frameMs / 1000,
        )
        count = 0; peak = 0; zero = 0; sent = 0
        return line
    }
}
