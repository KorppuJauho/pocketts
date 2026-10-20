package io.github.korppujauho.mobilets6

import android.content.Context

/** How talking works, as in WebTS's Settings: activation, levels and the microphone's processing. */
data class VoiceSettings(
    val activation: Activation = Activation.VAD,
    val vadThreshold: Float = 0.01f, // voice activation level: the microphone's RMS, 0 - 0.1
    val vadTailMs: Int = 600, // delay before deactivation
    val voiceVolumeDb: Int = 0, // everyone's voice, -20 to +12 dB
    val micVolume: Float = 1f, // the microphone, 0 - 3
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val autoGainControl: Boolean = false,
    val floatingPtt: Boolean = true, // the round push-to-talk button
    val volumeKeysPtt: Boolean = false, // volume buttons talk while held (app on screen)
) {
    enum class Activation { VAD, PTT, CONTINUOUS }

    val voiceGain: Float get() = Math.pow(10.0, voiceVolumeDb / 20.0).toFloat()

    companion object {
        fun load(context: Context): VoiceSettings {
            val p = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
            val d = VoiceSettings()
            return VoiceSettings(
                activation = p.getString("activation", null)?.let { runCatching { Activation.valueOf(it) }.getOrNull() } ?: d.activation,
                vadThreshold = p.getFloat("vadThreshold", d.vadThreshold),
                vadTailMs = p.getInt("vadTailMs", d.vadTailMs),
                voiceVolumeDb = p.getInt("voiceVolumeDb", d.voiceVolumeDb),
                micVolume = p.getFloat("micVolume", d.micVolume),
                noiseSuppression = p.getBoolean("noiseSuppression", d.noiseSuppression),
                echoCancellation = p.getBoolean("echoCancellation", d.echoCancellation),
                autoGainControl = p.getBoolean("autoGainControl", d.autoGainControl),
                floatingPtt = p.getBoolean("floatingPtt", d.floatingPtt),
                volumeKeysPtt = p.getBoolean("volumeKeysPtt", d.volumeKeysPtt),
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("voice", Context.MODE_PRIVATE).edit()
            .putString("activation", activation.name)
            .putFloat("vadThreshold", vadThreshold)
            .putInt("vadTailMs", vadTailMs)
            .putInt("voiceVolumeDb", voiceVolumeDb)
            .putFloat("micVolume", micVolume)
            .putBoolean("noiseSuppression", noiseSuppression)
            .putBoolean("echoCancellation", echoCancellation)
            .putBoolean("autoGainControl", autoGainControl)
            .putBoolean("floatingPtt", floatingPtt)
            .putBoolean("volumeKeysPtt", volumeKeysPtt)
            .apply()
    }
}
