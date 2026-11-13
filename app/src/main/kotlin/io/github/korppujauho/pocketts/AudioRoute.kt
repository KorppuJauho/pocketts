package io.github.korppujauho.pocketts

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Where voice goes as the phone's call audio (for echo cancellation, and always with the earpiece): through a
 * headset when one is connected and [useHeadset] is on, else the loudspeaker, or the earpiece when [earpiece] is chosen.
 * As media (the loudspeaker without echo cancellation), Android sends the voice itself and none of this is used.
 */
class AudioRoute(
    context: Context,
    /** The loudspeaker was asked for and Android plays from the earpiece all the same (on some phones it does). */
    private val onSpeakerRefused: () -> Unit = {},
) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var on = false
    private var earpiece = false
    private var useHeadset = true
    // Times Android moved the voice off the device asked for and it was asked for again, since the last choice: some
    // phones don't let go, and asking forever would have the two take turns
    private var reasserted = 0

    // At the ear, the screen turns off as in a phone call, so a cheek presses nothing
    private val atEar: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java)
        .takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
        ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "pocketts:earpiece")

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { if (on) choose() }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { if (on) choose() }
    }

    // Android's word on where call audio goes now (Android 12 and later)
    private val changed: Any? = if (Build.VERSION.SDK_INT >= 31) {
        AudioManager.OnCommunicationDeviceChangedListener { device ->
            val wanted = wanted()
            if (on && wanted != null && device?.id != wanted.id && reasserted < 3) {
                reasserted++
                pick()
            }
            screenAtEar()
        }
    } else null

    fun start(earpiece: Boolean, useHeadset: Boolean) {
        if (on) return
        on = true
        this.earpiece = earpiece
        this.useHeadset = useHeadset
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.registerAudioDeviceCallback(devices, main)
        if (Build.VERSION.SDK_INT >= 31) {
            am.addOnCommunicationDeviceChangedListener({ main.post(it) }, changed as AudioManager.OnCommunicationDeviceChangedListener)
        }
        choose()
    }

    /**
     * Playback or the microphone just started: asked for again, now and once they've had time to really run. Some
     * phones (a Galaxy A54) drop what's asked before the app has call audio running, and play from the earpiece.
     */
    fun audioStarted() {
        if (!on) return
        choose()
        main.removeCallbacks(again)
        main.postDelayed(again, 1000)
        main.removeCallbacks(verdict)
        main.postDelayed(verdict, 3000)
    }

    private val again = Runnable { if (on) choose() }

    // Asked, asked again, and given time: where does the voice go when it should be the loudspeaker?
    private val verdict = Runnable {
        if (!on || earpiece) return@Runnable
        val refused = if (Build.VERSION.SDK_INT >= 31) {
            wanted()?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && am.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        } else {
            @Suppress("DEPRECATION")
            oldSpeaker && !am.isSpeakerphoneOn
        }
        if (refused) {
            AppLog.log("The loudspeaker was asked for, but this phone plays call audio from its earpiece")
            onSpeakerRefused()
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        if (!on) return
        on = false
        main.removeCallbacks(again)
        main.removeCallbacks(verdict)
        am.unregisterAudioDeviceCallback(devices)
        if (Build.VERSION.SDK_INT >= 31) {
            am.removeOnCommunicationDeviceChangedListener(changed as AudioManager.OnCommunicationDeviceChangedListener)
            am.clearCommunicationDevice()
        } else am.isSpeakerphoneOn = false
        am.mode = AudioManager.MODE_NORMAL
        if (atEar?.isHeld == true) atEar.release()
    }

    private val headsets = listOf(
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    )

    /** The device to play from, best first: a headset (when used), then the phone's own as chosen. */
    private fun wanted(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < 31) return null
        val own = if (earpiece) AudioDeviceInfo.TYPE_BUILTIN_EARPIECE else AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        // A phone without an earpiece (a tablet) plays from its loudspeaker
        val preference = (if (useHeadset) headsets else emptyList()) + own + AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val available = am.availableCommunicationDevices
        return preference.firstNotNullOfOrNull { type -> available.firstOrNull { it.type == type } }
    }

    /** A new choice, or new devices: asked for, and Android gets its tries again. */
    private fun choose() {
        reasserted = 0
        pick()
    }

    private fun pick() {
        if (Build.VERSION.SDK_INT >= 31) {
            val best = wanted() ?: return
            val was = am.communicationDevice?.id
            if (!am.setCommunicationDevice(best)) AppLog.log("Voice couldn't be sent to a device of type ${best.type}: Android refused")
            else if (was != best.id) {
                AppLog.log("Voice goes to ${io.github.korppujauho.pocketts.proto.LogPrivacy.person(best.productName)} (type ${best.type})")
            }
        } else {
            // Before Android 12 only the loudspeaker can be asked for: off, Android plays from a headset or the earpiece
            val headset = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type in listOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET,
                )
            }
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = !earpiece && !(headset && useHeadset)
            oldSpeaker = !earpiece && !(headset && useHeadset)
            oldEarpiece = earpiece && !headset
        }
        screenAtEar()
    }

    private var oldEarpiece = false // before Android 12: the earpiece plays, as far as can be told
    private var oldSpeaker = false // before Android 12: the loudspeaker was asked for

    /** The screen goes off at the ear only while the earpiece plays. */
    private fun screenAtEar() {
        val lock = atEar ?: return
        val ear = on && if (Build.VERSION.SDK_INT >= 31) am.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE else oldEarpiece
        if (ear && !lock.isHeld) lock.acquire() else if (!ear && lock.isHeld) lock.release()
    }
}
