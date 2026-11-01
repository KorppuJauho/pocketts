package io.github.korppujauho.pocketts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import io.github.korppujauho.pocketts.ui.ControlsBar
import io.github.korppujauho.pocketts.ui.PttButton
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a screen reader (TalkBack) gets from the controls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun holdToTalkTapsOnAndOffForAScreenReader() {
        val calls = mutableListOf<Boolean>()
        compose.setContent {
            PocketTsTheme {
                var held by remember { mutableStateOf(false) }
                PttButton(held) { calls += it; held = it }
            }
        }
        val button = compose.onNodeWithContentDescription("Hold to talk")
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
        button.assert(clickLabel("start talking"))
        button.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) // what TalkBack does on a double tap
        button.assert(clickLabel("stop talking"))
        button.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(listOf(true, false), calls)
    }

    @Test
    fun microphoneAndSoundSayOnOrOff() {
        compose.setContent {
            PocketTsTheme { ControlsBar(micOff = true, soundOff = false, onMic = {}, onSound = {}) }
        }
        compose.onNodeWithContentDescription("Microphone").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
        compose.onNodeWithContentDescription("Sound").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
        it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == label
    }
}
