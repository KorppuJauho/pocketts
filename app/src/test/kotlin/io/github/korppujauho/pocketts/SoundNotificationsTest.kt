package io.github.korppujauho.pocketts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.korppujauho.pocketts.UiSounds.Cue
import io.github.korppujauho.pocketts.ui.EventSounds
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.SOUND_CATEGORIES
import io.github.korppujauho.pocketts.ui.SoundPackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Settings › Playback › Sound notifications: each event on or off on its own, kept with the other settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundNotificationsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyEventIsInOneCategory() {
        val shown = SOUND_CATEGORIES.flatMap { it.second }
        assertEquals(Cue.entries.toSet(), shown.toSet())
        assertEquals(shown.size, shown.toSet().size)
    }

    @Test
    fun mutedEventsAreKept() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(emptySet<String>(), VoiceSettings.load(context).mutedSounds) // all on at first
        VoiceSettings(mutedSounds = setOf("SOMEONE_LEFT", "MESSAGE_OUT")).save(context)
        assertEquals(setOf("SOMEONE_LEFT", "MESSAGE_OUT"), VoiceSettings.load(context).mutedSounds)
    }

    @Test
    fun whichEventsHaveASound() {
        val tones = VoiceSettings()
        assertTrue(UiSounds.hasSound(Cue.CONNECTED, tones, null))
        assertFalse(UiSounds.hasSound(Cue.WHISPER_RECEIVED, tones, null))
        val pack = VoiceSettings(soundPack = true)
        assertTrue(UiSounds.hasSound(Cue.WHISPER_RECEIVED, pack, setOf(Cue.WHISPER_RECEIVED)))
        assertFalse(UiSounds.hasSound(Cue.CONNECTED, pack, setOf(Cue.WHISPER_RECEIVED)))
        // A pack imported but not chosen: the tones
        assertTrue(UiSounds.hasSound(Cue.CONNECTED, tones, setOf(Cue.WHISPER_RECEIVED)))
    }

    @Test
    fun eventsSwitchOffOneByOneOrByCategory() {
        var saved = VoiceSettings()
        compose.setContent {
            PocketTsTheme {
                var v by remember { mutableStateOf(VoiceSettings()) }
                EventSounds(v, null, { v = it; saved = it })
            }
        }
        compose.onNodeWithText("8 of 8 on").assertIsDisplayed()
        // Folded until opened
        compose.onNodeWithText("People in your channel").performClick()
        compose.onNodeWithText("Someone left your channel").performClick()
        assertEquals(setOf("SOMEONE_LEFT"), saved.mutedSounds)
        compose.onNodeWithText("7 of 8 on").assertIsDisplayed()
        // The category's box: all of Connection off, and on again
        compose.onNodeWithContentDescription("Connection").performClick()
        assertEquals(
            setOf("SOMEONE_LEFT", "CONNECTED", "DISCONNECTED", "CONNECTION_LOST", "KICKED", "KICKED_SERVER", "BANNED"),
            saved.mutedSounds,
        )
        compose.onNodeWithText("0 of 6 on").assertIsDisplayed()
        compose.onNodeWithContentDescription("Connection").performClick()
        assertEquals(setOf("SOMEONE_LEFT"), saved.mutedSounds)
    }

    @Test
    fun anEventWithoutASoundSaysWhy() {
        compose.setContent {
            PocketTsTheme {
                EventSounds(VoiceSettings(soundPack = true), SoundPackInfo("Radio Pack", 1, 39, setOf(Cue.POKE)), {}, open = setOf(2))
            }
        }
        compose.onNodeWithContentDescription("Listen: You were poked").assertIsDisplayed()
        // A message arrived, You sent a message, Someone whispers to you
        assertEquals(3, compose.onAllNodesWithText("Not in this pack", substring = true).fetchSemanticsNodes().size)
    }
}
