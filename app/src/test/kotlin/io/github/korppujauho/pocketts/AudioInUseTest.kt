package io.github.korppujauho.pocketts

import android.media.AudioDeviceInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.korppujauho.pocketts.AudioInUse.Device
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.SettingsActions
import io.github.korppujauho.pocketts.ui.SettingsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Playback: voices play as Media, a Phone call or At your ear, with what each brings and costs, and the
 * Audio in use box that shows the mode, what plays and what records, in words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class AudioInUseTest {
    @get:Rule val rule = createComposeRule()
    private val saved = mutableListOf<VoiceSettings>()

    private fun actions(now: AudioInUse?) = object : SettingsActions {
        override fun save(voice: VoiceSettings, nickname: String) { saved += voice }
        override fun micLevel() = 0f to false
        override fun micTest(on: Boolean) {}
        override fun playTestSound() {}
        override fun audioInUse() = now
    }

    private fun show(now: AudioInUse?, voice: VoiceSettings = VoiceSettings(), tab: Int = 1) = rule.setContent {
        PocketTsTheme { SettingsContent(voice, "Jamie", actions(now), {}, startTab = tab) }
    }

    private fun seen(text: String) = rule.onNodeWithText(text).performScrollTo().assertIsDisplayed()

    /** A "Voices play as" choice (the box below may say the same word: the choice comes first). */
    private fun choice(text: String) = rule.onAllNodesWithText(text)[0]

    /** Taps a choice, then Save & Apply: what was saved. */
    private fun choose(text: String): VoiceSettings {
        choice(text).performClick()
        rule.onNodeWithText("Save & Apply").performClick()
        return saved.last()
    }

    private val speaker = Device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel")
    private val phoneMic = Device(AudioDeviceInfo.TYPE_BUILTIN_MIC, "Pixel")

    @Test
    fun aPhoneCallThroughTheLoudspeaker() {
        show(AudioInUse(call = true, speaker, phoneMic))
        choice("Phone call").assertIsSelected()
        rule.onAllNodesWithText("Phone call").assertCountEquals(2)
        rule.onNodeWithContentDescription("Plus: Echo removed, call volume").assertIsDisplayed()
        rule.onNodeWithContentDescription("Minus: Quieter; Bluetooth sound is call quality").assertIsDisplayed()
        seen("Loudspeaker")
        seen("Phone's microphone")
    }

    @Test
    fun bluetoothHeadphonesInACallAreSaidToBeCallQuality() {
        show(AudioInUse(call = true, Device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Buds"), Device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Buds")))
        seen("Buds (Bluetooth, call quality)")
        seen("Buds (Bluetooth)")
    }

    @Test
    fun mediaBetweenTalks() {
        show(AudioInUse(call = false, playback = null, microphone = phoneMic), VoiceSettings(echoCancellation = false))
        choice("Media").assertIsSelected()
        rule.onNodeWithContentDescription("Plus: Best sound quality, media volume").assertIsDisplayed()
        rule.onNodeWithContentDescription("Minus: Bluetooth headphones' microphone can't be used").assertIsDisplayed()
        seen("Shows when someone talks")
    }

    @Test
    fun eachChoiceSetsTheSoundUp() {
        show(AudioInUse(call = true, speaker, phoneMic))
        val media = choose("Media")
        assertFalse(media.callAudio)
        assertFalse(media.earpiece)
    }

    @Test
    fun atYourEarIsTheEarpiece() {
        show(AudioInUse(call = true, Device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, ""), null))
        val ear = choose("At your ear")
        assertTrue(ear.earpiece)
        assertTrue(ear.callAudio)
    }

    @Test
    fun aPhoneThatRefusedTheLoudspeakerPlaysAsMediaAndSaysWhy() {
        val refused = VoiceSettings(echoCancellation = true, speakerRefusedAt = BuildConfig.VERSION_CODE)
        show(AudioInUse(call = false, speaker, phoneMic), refused)
        choice("Media").assertIsSelected()
        seen("This phone can't play phone call sound through its loudspeaker, so voices play as media. Push-to-talk or headphones avoid echo.")
        // Phone call chosen again: the loudspeaker gets another try
        val again = choose("Phone call")
        assertEquals(0, again.speakerRefusedAt)
        assertTrue(again.callAudio)
    }

    @Test
    fun theEarpieceChoiceSaysWhatItBrings() {
        show(AudioInUse(call = true, Device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, ""), null), VoiceSettings(earpiece = true))
        choice("At your ear").assertIsSelected()
        rule.onNodeWithContentDescription("Plus: The screen turns off at your ear").assertIsDisplayed()
        seen("Not recording")
    }

    @Test
    fun notConnected() {
        show(null)
        seen("Connect to a server to see what plays and records.")
    }

    @Test
    fun anUnsavedChangeIsSaidToWait() {
        show(AudioInUse(call = true, speaker, null))
        choice("Media").performClick()
        seen("Changes show here after Save & Apply.")
    }

    @Test
    fun captureSaysWhereEchoCancellationIsSet() {
        show(null, tab = 2)
        seen("Echo cancellation is on while voices play as a phone call (Playback › Voices play as).")
    }
}
