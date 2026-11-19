package io.github.korppujauho.pocketts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import io.github.korppujauho.pocketts.proto.ChannelEdits
import io.github.korppujauho.pocketts.proto.ServerModel
import io.github.korppujauho.pocketts.ui.ChannelDialog
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A new channel from the channel menu: its place said, not chosen; permanent or not as one switch (temporary when off). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChannelBelowTest {
    @get:Rule
    val compose = createComposeRule()

    private var saved: ChannelEdits.Settings? = null

    private fun open(create: ServerModel.ChannelCreatePowers) = compose.setContent {
        PocketTsTheme {
            ChannelDialog(
                null, ChannelEdits.Settings("", kind = ChannelEdits.Kind.TEMPORARY, parent = 0, after = 2), Example.state().tree, "",
                onSave = { saved = it }, onDelete = null, onDismiss = {}, place = "Below Gaming", create = create,
            )
        }
    }

    @Test
    fun belowAndTemporaryUnlessSwitchedToPermanent() {
        open(ServerModel.ChannelCreatePowers(known = true, permanent = true, temporary = true, sortOrder = true))
        compose.onNodeWithText("Below Gaming").assertExists()
        compose.onNodeWithText("Inside").assertDoesNotExist()
        compose.onAllNodesWithText("Name")[0].performTextInput("Raid night")
        // Temporary until switched (the switch's box is its own node; the row takes the tap)
        compose.onNodeWithText("Permanent").performClick()
        compose.onNodeWithText("Create").performClick()
        assertEquals(ChannelEdits.Kind.PERMANENT, saved?.kind)
        assertEquals(2, saved?.after)
        assertEquals("Raid night", saved?.name)
    }

    @Test
    fun onlyTemporaryAllowedHasNothingToSwitch() {
        open(ServerModel.ChannelCreatePowers(known = true, temporary = true))
        compose.onNodeWithText("Permanent").assertDoesNotExist()
    }
}
