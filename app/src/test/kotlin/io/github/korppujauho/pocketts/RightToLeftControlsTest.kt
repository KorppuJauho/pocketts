package io.github.korppujauho.pocketts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.LayoutDirection
import io.github.korppujauho.pocketts.ui.ControlsBar
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.PttButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The buttons keep their order in a right-to-left language: push-to-talk at the right end, as in the others. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RightToLeftControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pushToTalkStaysOnTheRight() {
        compose.setContent {
            PocketTsTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ControlsBar(micOff = false, soundOff = false, onMic = {}, onSound = {}, ptt = { PttButton(false) {} })
                }
            }
        }
        val mic = compose.onNodeWithContentDescription("Microphone").getBoundsInRoot()
        val talk = compose.onNodeWithContentDescription("Hold to talk").getBoundsInRoot()
        assertTrue("push-to-talk ${talk.left} right of the microphone ${mic.left}", talk.left > mic.left)
    }
}
