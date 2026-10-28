package io.github.korppujauho.pocketts

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlin.math.sqrt

/**
 * The microphone: 20 ms at a time, its level, whether it's "on" by the activation (voice activation, push-to-talk,
 * continuous), and the Opus frames sent while it is. Holding whisper sends them as a whisper instead, in any activation.
 */
class MicSender(
    private val settings: () -> VoiceSettings,
    /** Whether anything may go out now: connected, and can talk in this channel (or, [whisper], may whisper). */
    private val canSend: (whisper: Boolean) -> Boolean,
    private val send: (frame: ByteArray, whisper: Boolean) -> Unit,
    private val sendStop: (whisper: Boolean) -> Unit,
    private val onTalking: (talking: Boolean, whisper: Boolean) -> Unit,
) {
    private companion object {
        const val RATE = 48000
        const val FRAME = 960
    }

    @Volatile var pttHeld = false
    /** Whisper held: what goes out is whispered, whatever the activation. */
    @Volatile var whisperHeld = false
    /** Microphone off: nothing is recorded (the phone's microphone indicator goes away too). */
    @Volatile var muted = false
        set(value) { field = value; wake() }
    /** Settings open: the level is measured (even when muted) but nothing is sent. */
    @Volatile var testing = false
        set(value) { field = value; wake() }
    /** The microphone's level (RMS, 0 - 1), for the meter. */
    @Volatile var level = 0f
        private set
    /** Whether it's "on" right now (as the meter shows it). */
    @Volatile var active = false
        private set

    @Volatile private var running = true
    @Volatile private var restart = false
    private val lock = Object()
    private val thread = Thread({ loop() }, "mic").apply { start() }

    /** The source or processing changed: start the recorder again. */
    fun reconfigure() {
        restart = true
        wake()
    }

    fun release() {
        running = false
        wake()
        thread.join(1000)
    }

    private fun wake() = synchronized(lock) { lock.notifyAll() }

    private fun loop() {
        while (running) {
            if (muted && !testing) {
                level = 0f
                synchronized(lock) { while (running && muted && !testing) lock.wait(1000) }
                continue
            }
            try {
                record()
            } catch (e: Exception) {
                AppLog.log("Microphone stopped: $e")
                Thread.sleep(1000)
            }
        }
    }

    @SuppressLint("MissingPermission") // only started once the permission is there
    private fun record() {
        restart = false
        val s = settings()
        val source = if (s.echoCancellation) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 2 * 4))
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("the microphone couldn't be opened")
        }
        val effects = listOfNotNull(
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = s.echoCancellation } else null,
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = s.noiseSuppression } else null,
            if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled = s.autoGainControl } else null,
        )
        val encoder = OpusEncoder()
        val pcm = ShortArray(FRAME)
        var lastSpeech = 0L
        var sending = false
        var sentWhisper = false // what [sending] is: a whisper or talking
        AppLog.log("Microphone on (echo ${s.echoCancellation}, noise ${s.noiseSuppression}, gain ${s.autoGainControl})")
        try {
            rec.startRecording()
            while (running && !restart && !(muted && !testing)) {
                var got = 0
                while (got < FRAME) {
                    val n = rec.read(pcm, got, FRAME - got)
                    if (n <= 0) throw IllegalStateException("read failed ($n)")
                    got += n
                }
                val cfg = settings()
                // Microphone volume, then the level
                var sum = 0.0
                for (i in pcm.indices) {
                    val v = (pcm[i] * cfg.micVolume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    pcm[i] = v.toShort()
                    val f = v / 32768.0
                    sum += f * f
                }
                val rms = sqrt(sum / pcm.size).toFloat()
                level = rms
                val now = System.currentTimeMillis()
                val whisper = whisperHeld
                val on = whisper || when (cfg.activation) {
                    VoiceSettings.Activation.CONTINUOUS -> true
                    VoiceSettings.Activation.PTT -> pttHeld
                    VoiceSettings.Activation.VAD -> {
                        if (rms >= cfg.vadThreshold) lastSpeech = now
                        now - lastSpeech < cfg.vadTailMs
                    }
                }
                active = on
                val go = on && !muted && !testing && canSend(whisper)
                if (go && sending && sentWhisper != whisper) {
                    // From talking to whispering, or back: the one ends, the other starts
                    sendStop(sentWhisper)
                    onTalking(false, sentWhisper)
                    sending = false
                }
                if (go) {
                    if (!sending) { sending = true; sentWhisper = whisper; onTalking(true, whisper) }
                    encoder.encode(pcm) { send(it, whisper) }
                } else if (sending) {
                    sending = false
                    sendStop(sentWhisper)
                    onTalking(false, sentWhisper)
                }
            }
        } finally {
            if (sending) {
                sendStop(sentWhisper)
                onTalking(false, sentWhisper)
            }
            active = false
            level = 0f
            try { rec.stop() } catch (_: Exception) {}
            effects.forEach { it.release() }
            rec.release()
            encoder.close()
        }
    }
}
