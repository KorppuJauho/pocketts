package io.github.korppujauho.pocketts

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * For the log, while the microphone runs: the phone's audio mode when it starts, and when Android silences the
 * recording (another app took the microphone: Android gives this one zeros) and lets it go again, with how many other
 * recordings there are. Android doesn't say which apps those are.
 */
class MicWatch(context: Context, private val ourSession: () -> Int) {
    private val am = context.getSystemService(AudioManager::class.java)
    private var silenced = false

    private val callback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) = check(configs)
    }

    init {
        val mode = when (am.mode) {
            AudioManager.MODE_IN_COMMUNICATION -> "call"
            AudioManager.MODE_IN_CALL -> "phone call"
            AudioManager.MODE_RINGTONE -> "ringing"
            else -> "normal"
        }
        // Android tells where call audio goes only from Android 12 on; asking earlier crashes the app
        val device = if (Build.VERSION.SDK_INT >= 31) am.communicationDevice?.let { ", call audio to type ${it.type}" }.orEmpty() else ""
        AppLog.log("Audio mode: $mode$device")
        am.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun check(configs: List<AudioRecordingConfiguration>) {
        val id = ourSession()
        if (id == 0) return
        val ours = configs.firstOrNull { it.clientAudioSessionId == id } ?: return
        if (ours.isClientSilenced == silenced) return
        silenced = ours.isClientSilenced
        val others = configs.size - 1
        AppLog.log(
            if (silenced) "Microphone silenced by Android: another app is using it ($others other recordings)"
            else "Microphone no longer silenced ($others other recordings)",
        )
    }

    fun stop() = am.unregisterAudioRecordingCallback(callback)
}
