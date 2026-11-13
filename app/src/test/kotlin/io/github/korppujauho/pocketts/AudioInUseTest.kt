package io.github.korppujauho.pocketts

import android.media.AudioDeviceInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.korppujauho.pocketts.AudioInUse.Device
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.SettingsActions
import io.github.korppujauho.pocketts.ui.SettingsContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Playback's Audio in use: the mode, what plays and what records, in words, with how to change the mode.
 * It only shows; the settings change it once saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class AudioInUseTest {
    @get:Rule val rule = createComposeRule()

    private fun actions(now: AudioInUse?) = object : SettingsActions {
        override fun save(voice: VoiceSettings, nickname: String) {}
        override fun micLevel() = 0f to false
        override fun micTest(on: Boolean) {}
        override fun playTestSound() {}
        override fun audioInUse() = now
    }

    private fun show(now: AudioInUse?, voice: VoiceSettings = VoiceSettings()) = rule.setContent {
        PocketTsTheme { SettingsContent(voice, "Jamie", actions(now), {}, startTab = 1) }
    }

    private fun seen(text: String) = rule.onNodeWithText(text).performScrollTo().assertIsDisplayed()

    @Test
    fun aPhoneCallThroughTheLoudspeaker() {
        show(AudioInUse(call = true, Device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel"), Device(AudioDeviceInfo.TYPE_BUILTIN_MIC, "Pixel")))
        seen("Phone call")
        // The setting's own button, and what plays
        rule.onAllNodesWithText("Loudspeaker").assertCountEquals(2)
        seen("Phone's microphone")
        seen("Media instead: louder, but others may hear their own voice back.")
    }

    @Test
    fun bluetoothHeadphonesInACallAreSaidToBeCallQuality() {
        show(
            AudioInUse(
                call = true, Device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Buds"), Device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Buds"),
                bluetooth = true,
            )
        )
        seen("Buds (Bluetooth, call quality)")
        seen("Buds (Bluetooth)")
        seen("Bluetooth headphones send their microphone only in a phone call, which also lowers their sound quality.")
    }

    @Test
    fun mediaBetweenTalksAndTheWayToChangeIt() {
        show(AudioInUse(call = false, playback = null, microphone = Device(AudioDeviceInfo.TYPE_BUILTIN_MIC, "")), VoiceSettings(echoCancellation = false))
        seen("Media")
        seen("Shows when someone talks")
        seen("Phone call instead: your echo is removed, but voices are quieter.")
        rule.onNodeWithText("Change in Capture ›").performScrollTo().performClick()
        seen("Echo cancellation (phone call mode)")
    }

    @Test
    fun theEarpieceLeavesNoChoice() {
        show(AudioInUse(call = true, Device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, ""), null), VoiceSettings(earpiece = true))
        seen("The earpiece always plays as a phone call.")
        seen("Not recording")
    }

    @Test
    fun notConnected() {
        show(null)
        seen("Connect to a server to see what plays and records.")
    }

    @Test
    fun anUnsavedChangeIsSaidToWait() {
        show(AudioInUse(call = true, Device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, ""), null))
        rule.onNodeWithText("Earpiece").performClick()
        seen("Changes show here after Save & Apply.")
    }
}
