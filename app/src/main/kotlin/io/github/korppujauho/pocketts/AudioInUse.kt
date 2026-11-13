package io.github.korppujauho.pocketts

import android.media.AudioDeviceInfo

/**
 * What the voices play through and what records right now, as Android reports it (Settings › Playback shows it). It
 * shows what really happens, not only what was asked for: some phones play call audio from the earpiece though the
 * loudspeaker was asked for.
 */
data class AudioInUse(
    /** Played as a phone call (echo cancellation on, or the earpiece), else as media. */
    val call: Boolean,
    /** Where the voices go; null while Android hasn't said (it does once someone talks). */
    val playback: Device?,
    /** What records; null when the microphone isn't recording. */
    val microphone: Device?,
    /** PocketTS may not use the microphone. */
    val noMicPermission: Boolean = false,
) {
    /** An Android audio device: its kind ([AudioDeviceInfo]'s TYPE_…) and its name, such as "Galaxy Buds". */
    data class Device(val type: Int, val name: String)

    companion object {
        fun device(d: AudioDeviceInfo?): Device? = d?.let { Device(it.type, it.productName?.toString().orEmpty()) }
    }
}
