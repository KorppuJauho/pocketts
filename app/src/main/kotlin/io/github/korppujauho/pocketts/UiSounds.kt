package io.github.korppujauho.pocketts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The app's own sounds for events, as WebTS plays them for the same events (TeamSpeak's own sound files aren't ours to
 * use): short plops, ticks and chimes made here from plain tones. Played the way voices are (call audio or media),
 * at their own volume (Settings > Playback).
 */
object UiSounds {
    enum class Cue {
        CONNECTED, DISCONNECTED, CONNECTION_LOST, KICKED,
        CHANNEL_SWITCHED, MOVED, SOMEONE_JOINED, SOMEONE_LEFT,
        MESSAGE_IN, MESSAGE_OUT, POKE,
        MIC_OFF, MIC_ON, SOUND_OFF, SOUND_ON,
        STREAM_STARTED, STREAM_STOPPED,
    }

    private const val RATE = 48000
    private val player = Executors.newSingleThreadExecutor { r -> Thread(r, "ui-sounds").apply { isDaemon = true } }
    private val made = HashMap<Cue, ShortArray>()

    /** Plays [cue] if sounds are on; [soundOff] (the app's sound button) keeps everything but its own feedback quiet. */
    fun play(cue: Cue, v: VoiceSettings, soundOff: Boolean = false) {
        if (!v.uiSounds || v.uiSoundVolume <= 0f) return
        if (soundOff && cue != Cue.SOUND_OFF) return
        player.execute {
            try {
                val pcm = synchronized(made) { made.getOrPut(cue) { make(cue) } }
                out(pcm, v)
            } catch (e: Exception) {
                AppLog.log("Sound $cue not played: $e")
            }
        }
    }

    private fun out(pcm: ShortArray, v: VoiceSettings) {
        val gain = v.uiSoundVolume
        val scaled = ShortArray(pcm.size) { (pcm[it] * gain).toInt().toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (v.echoCancellation) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(scaled.size * 2)
            .build()
        try {
            track.write(scaled, 0, scaled.size)
            track.play()
            Thread.sleep(scaled.size * 1000L / RATE + 60)
        } finally {
            track.release()
        }
    }

    // ====== Making the sounds ======

    /** One note: [from] Hz gliding to [to], [ms] long, starting at [at] ms; a soft overtone, a quick start, a fading tail. */
    private data class Note(val at: Int, val from: Double, val to: Double = from, val ms: Int, val level: Double = 0.5, val decay: Double = 6.0)

    private fun make(cue: Cue): ShortArray = render(
        when (cue) {
            // Three notes up: you're in
            Cue.CONNECTED -> listOf(Note(0, 523.0, ms = 140), Note(90, 659.0, ms = 140), Note(180, 784.0, ms = 260))
            // The same down: you left
            Cue.DISCONNECTED -> listOf(Note(0, 784.0, ms = 140), Note(90, 659.0, ms = 140), Note(180, 523.0, ms = 260))
            // Two low notes, the second sagging
            Cue.CONNECTION_LOST -> listOf(Note(0, 440.0, ms = 180), Note(170, 392.0, 349.0, ms = 320))
            // Two low, firm notes
            Cue.KICKED -> listOf(Note(0, 330.0, ms = 160, level = 0.55, decay = 3.0), Note(150, 247.0, ms = 320, level = 0.55, decay = 3.0))
            // A plop up and a small chime: your new channel
            Cue.CHANNEL_SWITCHED -> listOf(Note(0, 480.0, 820.0, ms = 110), Note(90, 988.0, ms = 180, level = 0.3))
            // Two notes up: someone moved you
            Cue.MOVED -> listOf(Note(0, 659.0, ms = 120), Note(110, 880.0, ms = 200))
            // A soft plop up / down: someone came into / left your channel
            Cue.SOMEONE_JOINED -> listOf(Note(0, 560.0, 900.0, ms = 120, level = 0.45))
            Cue.SOMEONE_LEFT -> listOf(Note(0, 900.0, 520.0, ms = 140, level = 0.4))
            // A bubble pop for a message in, a light tick for one sent
            Cue.MESSAGE_IN -> listOf(Note(0, 1150.0, 720.0, ms = 70, level = 0.45, decay = 9.0), Note(55, 1400.0, ms = 90, level = 0.25, decay = 9.0))
            Cue.MESSAGE_OUT -> listOf(Note(0, 1500.0, 1200.0, ms = 45, level = 0.25, decay = 12.0))
            // Two bright double taps: someone poked you
            Cue.POKE -> listOf(
                Note(0, 1319.0, ms = 70, decay = 8.0), Note(90, 1319.0, ms = 70, decay = 8.0),
                Note(260, 1568.0, ms = 70, decay = 8.0), Note(350, 1568.0, ms = 120, decay = 8.0),
            )
            // Clicks: down for off, up for on (the sound button a little lower than the microphone)
            Cue.MIC_OFF -> listOf(Note(0, 900.0, ms = 45, level = 0.4, decay = 10.0), Note(60, 620.0, ms = 60, level = 0.4, decay = 10.0))
            Cue.MIC_ON -> listOf(Note(0, 620.0, ms = 45, level = 0.4, decay = 10.0), Note(60, 900.0, ms = 60, level = 0.4, decay = 10.0))
            Cue.SOUND_OFF -> listOf(Note(0, 700.0, ms = 50, level = 0.4, decay = 9.0), Note(65, 460.0, ms = 70, level = 0.4, decay = 9.0))
            Cue.SOUND_ON -> listOf(Note(0, 460.0, ms = 50, level = 0.4, decay = 9.0), Note(65, 700.0, ms = 70, level = 0.4, decay = 9.0))
            // A rising sweep with a chime when a stream starts, falling when it stops
            Cue.STREAM_STARTED -> listOf(Note(0, 420.0, 980.0, ms = 180, level = 0.35, decay = 2.5), Note(160, 1175.0, ms = 220, level = 0.3))
            Cue.STREAM_STOPPED -> listOf(Note(0, 980.0, 420.0, ms = 220, level = 0.35, decay = 2.5))
        }
    )

    private fun render(notes: List<Note>): ShortArray {
        val total = notes.maxOf { it.at + it.ms } * RATE / 1000 + RATE / 50
        val mix = DoubleArray(total)
        for (n in notes) {
            val start = n.at * RATE / 1000
            val len = n.ms * RATE / 1000
            var phase = 0.0
            for (i in 0 until len) {
                if (start + i >= total) break
                val t = i.toDouble() / len
                val freq = n.from + (n.to - n.from) * t
                phase += 2 * PI * freq / RATE
                val attack = minOf(1.0, i / (RATE * 0.004)) // 4 ms: no click
                val tail = minOf(1.0, (len - i) / (RATE * 0.006))
                val env = attack * tail * exp(-n.decay * t)
                // The note and a quieter octave: rounder than a bare tone
                mix[start + i] += n.level * env * (sin(phase) + 0.25 * sin(2 * phase))
            }
        }
        val peak = maxOf(1.0, mix.maxOf { kotlin.math.abs(it) })
        return ShortArray(total) { (mix[it] / peak * 0.8 * Short.MAX_VALUE).toInt().toShort() }
    }
}
