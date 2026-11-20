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
 * use): low, soft tones made here, in the style of chat apps' sounds. Or the person's own sound pack ([SoundPack]),
 * when chosen. Played the way voices are (call audio or media), at their own volume (Settings > Playback).
 */
object UiSounds {
    /**
     * The events. Some are finer than the tones tell apart (kicked from the server, banned; someone connecting to your
     * channel or joining it from another): a sound pack can have a sound for each, the tones play the broader one's.
     * Some have a sound only in a sound pack ([PACK_ONLY]), as TeamSpeak's packs have sounds for them.
     */
    enum class Cue {
        CONNECTED, DISCONNECTED, CONNECTION_LOST, KICKED, KICKED_SERVER, BANNED,
        CHANNEL_SWITCHED, MOVED, SOMEONE_JOINED, SOMEONE_CONNECTED, SOMEONE_LEFT, SOMEONE_DISCONNECTED, SOMEONE_LOST_CONNECTION,
        SOMEONE_KICKED_CHANNEL, SOMEONE_KICKED_SERVER, SOMEONE_BANNED,
        MESSAGE_IN, MESSAGE_OUT, POKE, WHISPER_RECEIVED,
        MIC_OFF, MIC_ON, SOUND_OFF, SOUND_ON, AWAY_ON, AWAY_OFF,
        STREAM_STARTED, STREAM_STOPPED,
        TALK_POWER_GRANTED, TALK_POWER_REVOKED, SERVER_GROUP_ADDED, SERVER_GROUP_REMOVED, CHANNEL_GROUP_CHANGED,
        PERMISSION_REFUSED, CHANNEL_CREATED, CHANNEL_DELETED, CHANNEL_EDITED, YOUR_CHANNEL_EDITED, CHANNEL_MOVED,
    }

    /** Events the tones stay quiet for (as before there were sound packs); a pack may have a sound for them. */
    val PACK_ONLY = setOf(
        Cue.WHISPER_RECEIVED, Cue.AWAY_ON, Cue.AWAY_OFF, Cue.TALK_POWER_GRANTED, Cue.TALK_POWER_REVOKED,
        Cue.SERVER_GROUP_ADDED, Cue.SERVER_GROUP_REMOVED, Cue.CHANNEL_GROUP_CHANGED, Cue.PERMISSION_REFUSED,
        Cue.CHANNEL_CREATED, Cue.CHANNEL_DELETED, Cue.CHANNEL_EDITED, Cue.YOUR_CHANNEL_EDITED, Cue.CHANNEL_MOVED,
    )

    private const val RATE = 48000
    private val player = Executors.newSingleThreadExecutor { r -> Thread(r, "ui-sounds").apply { isDaemon = true } }
    private val made = HashMap<Cue, ShortArray>()

    /**
     * Plays [cue] if sounds are on and the event isn't switched off on its own (Settings › Playback); [soundOff] (the
     * app's sound button) keeps everything but its own feedback quiet.
     */
    fun play(cue: Cue, v: VoiceSettings, soundOff: Boolean = false) {
        if (!v.uiSounds || cue.name in v.mutedSounds) return
        if (soundOff && cue != Cue.SOUND_OFF) return
        sound(cue, v)
    }

    /** Settings' Listen: the event's sound as it plays with [v], also while the event is switched off. */
    fun listen(cue: Cue, v: VoiceSettings) = sound(cue, v)

    /** Whether [cue] has a sound with [v]: the tones have none for [PACK_ONLY], a pack only for the events it has a file for. */
    fun hasSound(cue: Cue, v: VoiceSettings, packCues: Set<Cue>?): Boolean =
        if (v.soundPack && packCues != null) cue in packCues else cue !in PACK_ONLY

    /** Whether [play] would make [cue] heard with [v]: on, not switched off, and with a sound (the pack's, or a tone). */
    fun audible(cue: Cue, v: VoiceSettings): Boolean {
        if (!v.uiSounds || cue.name in v.mutedSounds || v.uiSoundVolume <= 0f) return false
        val pack = SoundPack.pack
        return if (v.soundPack && pack != null) cue in pack.files else cue !in PACK_ONLY
    }

    private fun sound(cue: Cue, v: VoiceSettings) {
        if (v.uiSoundVolume <= 0f) return
        player.execute {
            try {
                if (v.soundPack && SoundPack.pack != null) {
                    // An event the pack has no sound for is quiet, as in TeamSpeak
                    SoundPack.pcm(cue)?.let { out(it.samples, v, it.rate, it.channels) }
                } else if (cue !in PACK_ONLY) {
                    val tone = TONE_OF[cue] ?: cue
                    val pcm = synchronized(made) { made.getOrPut(tone) { make(tone) } }
                    out(pcm, v)
                }
            } catch (e: Exception) {
                AppLog.log("Sound $cue not played: $e")
            }
        }
    }

    /** The finer events play their broader one's tone. */
    private val TONE_OF = mapOf(
        Cue.KICKED_SERVER to Cue.KICKED, Cue.BANNED to Cue.KICKED,
        Cue.SOMEONE_CONNECTED to Cue.SOMEONE_JOINED,
        Cue.SOMEONE_DISCONNECTED to Cue.SOMEONE_LEFT, Cue.SOMEONE_LOST_CONNECTION to Cue.SOMEONE_LEFT,
        Cue.SOMEONE_KICKED_CHANNEL to Cue.SOMEONE_LEFT, Cue.SOMEONE_KICKED_SERVER to Cue.SOMEONE_LEFT, Cue.SOMEONE_BANNED to Cue.SOMEONE_LEFT,
    )

    private fun out(pcm: ShortArray, v: VoiceSettings, rate: Int = RATE, channels: Int = 1) {
        val gain = v.uiSoundVolume
        val scaled = ShortArray(pcm.size) { (pcm[it] * gain).toInt().toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (v.callAudio) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(rate)
                    .setChannelMask(if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(scaled.size * 2)
            .build()
        try {
            track.write(scaled, 0, scaled.size)
            track.play()
            Thread.sleep(scaled.size * 1000L / (rate * channels) + 60)
        } finally {
            track.release()
        }
    }

    // ====== Making the sounds ======
    // Low and round, in the style of chat apps' sounds: soft sine "bloops" between A3 and E5 (220 - 660 Hz), a gentle
    // start, a long fade and a short echo, instead of bright beeps.

    /** One note: [from] Hz gliding to [to], [ms] long, starting at [at] ms; [decay]: how fast it fades. */
    private data class Note(val at: Int, val from: Double, val to: Double = from, val ms: Int, val level: Double = 0.5, val decay: Double = 4.0)

    // Notes (Hz)
    private const val F3 = 174.6
    private const val G3 = 196.0
    private const val A3 = 220.0
    private const val B3 = 246.9
    private const val C4 = 261.6
    private const val D4 = 293.7
    private const val E4 = 329.6
    private const val G4 = 392.0
    private const val A4 = 440.0
    private const val C5 = 523.3
    private const val E5 = 659.3

    private fun make(cue: Cue): ShortArray = render(
        when (cue) {
            Cue.KICKED_SERVER, Cue.BANNED, Cue.SOMEONE_CONNECTED, Cue.SOMEONE_DISCONNECTED, Cue.SOMEONE_LOST_CONNECTION,
            Cue.SOMEONE_KICKED_CHANNEL, Cue.SOMEONE_KICKED_SERVER, Cue.SOMEONE_BANNED ->
                error("played with its broader event's tone")
            Cue.WHISPER_RECEIVED, Cue.AWAY_ON, Cue.AWAY_OFF, Cue.TALK_POWER_GRANTED, Cue.TALK_POWER_REVOKED,
            Cue.SERVER_GROUP_ADDED, Cue.SERVER_GROUP_REMOVED, Cue.CHANNEL_GROUP_CHANGED, Cue.PERMISSION_REFUSED,
            Cue.CHANNEL_CREATED, Cue.CHANNEL_DELETED, Cue.CHANNEL_EDITED, Cue.YOUR_CHANNEL_EDITED, Cue.CHANNEL_MOVED -> error("no tone")
            // A soft chord rising: you're in
            Cue.CONNECTED -> listOf(Note(0, C4, ms = 380), Note(110, E4, ms = 380), Note(220, G4, ms = 520))
            // The same falling: you left
            Cue.DISCONNECTED -> listOf(Note(0, G4, ms = 380), Note(110, E4, ms = 380), Note(220, C4, ms = 520))
            // Slow and sinking
            Cue.CONNECTION_LOST -> listOf(Note(0, E4, ms = 320), Note(200, C4, ms = 320), Note(400, A3, A3 * 0.97, ms = 560))
            // Two low notes
            Cue.KICKED -> listOf(Note(0, A3, ms = 360, level = 0.6, decay = 3.0), Note(240, F3, ms = 560, level = 0.6, decay = 3.0))
            // Two soft notes up: you're in your new channel
            Cue.CHANNEL_SWITCHED -> listOf(Note(0, C4, ms = 180, level = 0.42, decay = 6.0), Note(110, G4, ms = 300, level = 0.36, decay = 5.0))
            // Up, down and up again: someone moved you
            Cue.MOVED -> listOf(
                Note(0, G4, ms = 150, level = 0.4, decay = 6.0), Note(110, C4, ms = 150, level = 0.4, decay = 6.0),
                Note(220, G4, ms = 260, level = 0.34, decay = 5.0),
            )
            // A soft step up / down: someone came into / left your channel (rounder than the buttons' thocks)
            Cue.SOMEONE_JOINED -> listOf(Note(0, A3, ms = 140, level = 0.38, decay = 7.0), Note(100, E4, ms = 240, level = 0.36, decay = 6.0))
            Cue.SOMEONE_LEFT -> listOf(Note(0, E4, ms = 140, level = 0.38, decay = 7.0), Note(100, A3, ms = 260, level = 0.36, decay = 6.0))
            // A soft two-note pluck for a message in, a quiet low tap for one sent
            Cue.MESSAGE_IN -> listOf(Note(0, E4, ms = 150, level = 0.36, decay = 8.0), Note(80, A4, ms = 240, level = 0.32, decay = 7.0))
            Cue.MESSAGE_OUT -> listOf(Note(0, D4, ms = 110, level = 0.24, decay = 10.0))
            // Two quick pairs: someone poked you
            Cue.POKE -> listOf(
                Note(0, C5, ms = 150, decay = 6.0), Note(110, E5, ms = 200, decay = 6.0),
                Note(330, C5, ms = 150, decay = 6.0), Note(440, E5, ms = 280, decay = 6.0),
            )
            // Low "thocks": down for off, up for on (the sound button lower than the microphone)
            Cue.MIC_OFF -> listOf(Note(0, G4, ms = 90, level = 0.45, decay = 9.0), Note(80, D4, ms = 140, level = 0.45, decay = 9.0))
            Cue.MIC_ON -> listOf(Note(0, D4, ms = 90, level = 0.45, decay = 9.0), Note(80, G4, ms = 140, level = 0.45, decay = 9.0))
            Cue.SOUND_OFF -> listOf(Note(0, E4, ms = 90, level = 0.45, decay = 9.0), Note(80, B3, ms = 150, level = 0.45, decay = 9.0))
            Cue.SOUND_ON -> listOf(Note(0, B3, ms = 90, level = 0.45, decay = 9.0), Note(80, E4, ms = 150, level = 0.45, decay = 9.0))
            // Three notes rising when a stream starts, falling when it stops
            Cue.STREAM_STARTED -> listOf(
                Note(0, G3, ms = 170, level = 0.36, decay = 6.0), Note(120, D4, ms = 170, level = 0.34, decay = 6.0),
                Note(240, G4, ms = 320, level = 0.3, decay = 4.0),
            )
            Cue.STREAM_STOPPED -> listOf(
                Note(0, G4, ms = 170, level = 0.34, decay = 6.0), Note(120, D4, ms = 170, level = 0.34, decay = 6.0),
                Note(240, G3, ms = 340, level = 0.32, decay = 4.0),
            )
        }
    )

    private fun render(notes: List<Note>): ShortArray {
        val echoAt = RATE * 90 / 1000
        val total = notes.maxOf { it.at + it.ms } * RATE / 1000 + echoAt + RATE / 20
        val dry = DoubleArray(total)
        for (n in notes) {
            val start = n.at * RATE / 1000
            val len = n.ms * RATE / 1000
            var phase = 0.0
            for (i in 0 until len) {
                if (start + i >= total) break
                val t = i.toDouble() / len
                // An eased glide sounds softer than a straight one
                val glide = 1 - (1 - t) * (1 - t)
                val freq = n.from + (n.to - n.from) * glide
                phase += 2 * PI * freq / RATE
                val attack = minOf(1.0, i / (RATE * 0.012)) // 12 ms: no click, no snap
                val tail = minOf(1.0, (len - i) / (RATE * 0.02))
                val env = attack * tail * exp(-n.decay * t)
                // Mostly the pure note, a touch of its octave and of the octave below: warm, not bright
                dry[start + i] += n.level * env * (sin(phase) + 0.10 * sin(2 * phase) + 0.12 * sin(phase / 2))
            }
        }
        // One short echo, quieter: rounds the sound off like a small room
        val mix = DoubleArray(total)
        for (i in 0 until total) mix[i] = dry[i] + if (i >= echoAt) 0.22 * dry[i - echoAt] else 0.0
        val peak = maxOf(1.0, mix.maxOf { kotlin.math.abs(it) })
        return ShortArray(total) { (mix[it] / peak * 0.7 * Short.MAX_VALUE).toInt().toShort() }
    }
}
