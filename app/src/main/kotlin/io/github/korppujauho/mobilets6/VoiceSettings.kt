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
    val turnUrl: String = "", // a relay (TURN) server for streams, e.g. turn:relay.example.com:3478
    val turnUser: String = "",
    val turnPassword: String = "",
    val relayOnly: Boolean = false, // streams only through the relay
    val cameraRotateLock: Boolean = false, // the camera stream's turn is locked (else it follows how the phone is held)
    val overlayPtt: Boolean = false, // the push-to-talk button stays on screen over other apps
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
                turnUrl = p.getString("turnUrl", d.turnUrl).orEmpty(),
                turnUser = p.getString("turnUser", d.turnUser).orEmpty(),
                turnPassword = p.getString("turnPassword", d.turnPassword).orEmpty(),
                relayOnly = p.getBoolean("relayOnly", d.relayOnly),
                cameraRotateLock = p.getBoolean("cameraRotateLock", d.cameraRotateLock),
                overlayPtt = p.getBoolean("overlayPtt", d.overlayPtt),
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
            .putString("turnUrl", turnUrl.trim())
            .putString("turnUser", turnUser.trim())
            .putString("turnPassword", turnPassword)
            .putBoolean("relayOnly", relayOnly)
            .putBoolean("cameraRotateLock", cameraRotateLock)
            .putBoolean("overlayPtt", overlayPtt)
            .apply()
    }
}
