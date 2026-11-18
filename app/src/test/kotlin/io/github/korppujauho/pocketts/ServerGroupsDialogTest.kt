package io.github.korppujauho.pocketts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.korppujauho.pocketts.proto.ServerModel
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.ServerGroupsDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A person's server groups: theirs ticked; a tick asks the server to add, taking it off to remove; greyed ones do nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class ServerGroupsDialogTest {
    @get:Rule val rule = createComposeRule()
    private val asked = mutableListOf<Pair<Int, Boolean>>()
    private val channelGroup = mutableListOf<Int>()

    private fun dialog(powers: ServerModel.GroupPowers = ServerModel.GroupPowers(75, 75), error: String? = null) = rule.setContent {
        PocketTsTheme {
            ServerGroupsDialog(
                ServerModel.Client(3, 1, "Lee", databaseId = 234, serverGroups = listOf(7), channelGroup = 8), Example.groupChoices,
                onChange = { g, member -> asked += g.id to member }, onDismiss = {},
                channelGroups = Example.channelGroups.copy(powers = powers), onChannelGroup = { channelGroup += it.id },
                error = error,
            )
        }
    }

    @Test
    fun ticksAddAndRemove() {
        dialog()
        rule.onNodeWithText("Member").assertIsOn()
        rule.onNodeWithText("Moderator").assertIsOff().performClick()
        rule.onNodeWithText("Member").performClick()
        assertEquals(listOf(9 to true, 7 to false), asked)
    }

    @Test
    fun aChannelGroupIsChosenInTheirChannel() {
        dialog()
        rule.onNodeWithText("Channel group in Default Channel").assertExists()
        rule.onNodeWithText("Operator").performClick()
        assertEquals(listOf(6), channelGroup)
    }

    @Test
    fun aChannelGroupBeyondOurPowerIsGreyedOut() {
        dialog(ServerModel.GroupPowers(30, 30))
        rule.onNodeWithText("Channel Admin").assertIsNotEnabled().performClick()
        assertEquals(emptyList<Int>(), channelGroup)
    }

    @Test
    fun theDefaultGroupIsGreyedOut() {
        dialog()
        rule.onAllNodesWithText("Guest")[0].assertIsNotEnabled().performClick()
        assertEquals(emptyList<Pair<Int, Boolean>>(), asked)
    }

    /** The server's refusal shows in the window, where the tick was. */
    @Test
    fun aRefusalShowsInTheWindow() {
        dialog(error = "You can't change the server groups of Lee")
        rule.onNodeWithText("You can't change the server groups of Lee").assertIsDisplayed()
    }

    @Test
    fun yourOwnGroupsFromYourOwnMenu() {
        var opened = false
        rule.setContent {
            PocketTsTheme {
                io.github.korppujauho.pocketts.ui.PersonSheet(
                    ServerModel.Client(1, 1, "Jamie", databaseId = 12), "", isSelf = true, volume = 1f, onVolume = {}, onPoke = {}, onChat = {},
                    onDismiss = {}, admin = true, onGroups = { opened = true },
                )
            }
        }
        rule.onNodeWithText("Groups").performClick()
        assertEquals(true, opened)
    }
}
