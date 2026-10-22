package io.github.korppujauho.pocketts

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Process
import java.nio.ByteBuffer

/**
 * What the phone plays (a video, a game) as a screen share's sound. While the screen is shared, Android lets an app
 * record what other apps play; an app can refuse it (it's then silent for viewers). Our own sound, the channel's
 * voices and the streams being watched, is left out, so viewers don't hear the channel twice.
 *
 * WebRTC's microphone is off meanwhile: its recording loop still asks for a piece of sound every 10 ms, and [fill]
 * gives it the phone's. Reading waits for the next piece, so the phone's sound sets the pace.
 */
class ScreenSound(private val projection: MediaProjection) {
    @Volatile private var record: AudioRecord? = null
    private var shape = Triple(0, 0, 0) // the encoding, channels and sample rate it was made for
    private var failed = false
    @Volatile private var stopped = false

    /** Fills [buffer] with the next [bytes] of the phone's sound, in the shape WebRTC asks for (silence when there's none). */
    @Synchronized
    fun fill(buffer: ByteBuffer, encoding: Int, channels: Int, sampleRate: Int, bytes: Int) {
        val r = if (stopped) null else recordFor(encoding, channels, sampleRate, bytes)
        buffer.clear()
        val got = r?.read(buffer, bytes, AudioRecord.READ_BLOCKING) ?: 0
        if (got < bytes) {
            for (i in maxOf(got, 0) until bytes) buffer.put(i, 0)
            // Nothing to wait for: the loop is paced here instead
            if (got <= 0) try { Thread.sleep(10) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    // Made on the first piece asked for, in its shape (made again if that changes)
    @SuppressLint("MissingPermission") // only used with the microphone allowed (Android's rule for recording other apps)
    private fun recordFor(encoding: Int, channels: Int, sampleRate: Int, bytes: Int): AudioRecord? {
        val wanted = Triple(encoding, channels, sampleRate)
        if (shape == wanted) return record
        record?.release()
        record = null
        shape = wanted
        if (failed) return null
        return try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .excludeUid(Process.myUid())
                .build()
            val mask = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            val format = AudioFormat.Builder().setEncoding(encoding).setSampleRate(sampleRate).setChannelMask(mask).build()
            val min = AudioRecord.getMinBufferSize(sampleRate, mask, encoding)
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(min, bytes * 10)) // 100 ms
                .setAudioPlaybackCaptureConfig(config)
                .build()
                .also {
                    it.startRecording()
                    record = it
                    AppLog.log("[Streams] Phone sound: ${sampleRate / 1000} kHz, $channels channel(s)")
                }
        } catch (e: Exception) {
            failed = true
            AppLog.log("[Streams] The phone's sound can't be recorded: $e")
            null
        }
    }

    /** Stops recording: a [fill] waiting for sound returns at once, and the next ones give silence. */
    fun stop() {
        stopped = true
        try { record?.stop() } catch (_: Exception) {}
    }

    /** Lets go of the recording, after [stop] (waits for a [fill] still running). */
    @Synchronized
    fun release() {
        record?.release()
        record = null
    }
}
