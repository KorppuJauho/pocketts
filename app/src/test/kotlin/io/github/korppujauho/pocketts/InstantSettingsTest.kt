package io.github.korppujauho.pocketts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
 * Settings keep a change as it's made, with no Save button. The exceptions: the language waits for Apply, turning the
 * debug log off asks first, and the relay's fields are kept when left or when Settings closes, not after every letter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class InstantSettingsTest {
    @get:Rule val rule = createComposeRule()
    private val saved = mutableListOf<VoiceSettings>()
    private val languages = mutableListOf<String>()

    private val actions = object : SettingsActions by Example.noSettings {
        override fun save(voice: VoiceSettings) { saved += voice }
        override fun setLanguage(tag: String) { languages += tag }
    }

    private var open by mutableStateOf(true)

    private fun show(tab: Int, voice: VoiceSettings = VoiceSettings()) = rule.setContent {
        PocketTsTheme { if (open) SettingsContent(voice, actions, { open = false }, startTab = tab) }
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
    }

    /** Waits until a change has been kept (it's kept a moment after it's made). */
    private fun kept() {
        repeat(20) { if (saved.isEmpty()) { rule.waitForIdle(); rule.mainClock.advanceTimeBy(500) } }
        rule.waitForIdle()
    }

    @Test
    fun aCheckIsKeptWithoutSaving() {
        show(0)
        rule.onNodeWithText("Keep the screen on").performScrollTo().performClick()
        kept()
        assertTrue(saved.last().keepScreenOn)
        rule.onAllNodesWithText("Save & Apply").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun theLanguageWaitsForApply() {
        show(3)
        rule.onNodeWithText("Apply").assertDoesNotExist()
        rule.onNodeWithText("Phone's language").performScrollTo().performClick()
        rule.onNodeWithText("Suomi").performClick()
        settle()
        assertEquals(emptyList<String>(), languages)
        rule.onNodeWithText("Apply").performScrollTo().performClick()
        assertEquals(listOf("fi"), languages)
    }

    @Test
    fun turningTheDebugLogOffAsksFirst() {
        show(4, VoiceSettings(debugLog = true))
        rule.onNodeWithText("Keep a debug log").performScrollTo().performClick()
        settle()
        assertTrue(saved.isEmpty())
        rule.onNodeWithText("Turn off the debug log?").assertExists()
        rule.onNodeWithText("Turn off").performClick()
        kept()
        assertFalse(saved.last().debugLog)
    }

    @Test
    fun theRelayIsKeptWhenSettingsClose() {
        show(4)
        rule.onAllNodesWithText("Relay (turn:host:port)")[0].performTextInput("turn:relay.example.com:3478")
        settle()
        // Still being typed: not kept yet
        assertTrue(saved.none { it.turnUrl.isNotEmpty() })
        rule.runOnIdle { open = false }
        rule.waitForIdle()
        assertEquals("turn:relay.example.com:3478", saved.last().turnUrl)
    }
}
