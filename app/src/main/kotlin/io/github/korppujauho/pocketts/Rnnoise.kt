package io.github.korppujauho.pocketts

/**
 * RNNoise, Xiph's AI noise removal (built from its release with the app, see src/main/cpp): cleans the microphone's
 * sound, 20 ms at a time. One per microphone; [close] it when done.
 */
class Rnnoise private constructor(private var state: Long) : AutoCloseable {

    /** Cleans [pcm] (48 kHz mono, 960 samples) in place; gives the chance it's speech, 0 - 1. */
    fun process(pcm: ShortArray): Float = if (state != 0L) process(state, pcm) else 0f

    override fun close() {
        if (state != 0L) destroy(state)
        state = 0L
    }

    companion object {
        /** Whether the library loaded (it always should; if not, the microphone goes on without it). */
        private val available: Boolean by lazy {
            try {
                System.loadLibrary("pocketts_rnnoise")
                true
            } catch (e: Throwable) {
                AppLog.log("AI noise removal isn't available: $e")
                false
            }
        }

        /** A new one, or null when the library can't be used. */
        fun open(): Rnnoise? = if (available) create().takeIf { it != 0L }?.let { Rnnoise(it) } else null

        @JvmStatic private external fun create(): Long
        @JvmStatic private external fun destroy(state: Long)
        @JvmStatic private external fun process(state: Long, pcm: ShortArray): Float
    }
}
