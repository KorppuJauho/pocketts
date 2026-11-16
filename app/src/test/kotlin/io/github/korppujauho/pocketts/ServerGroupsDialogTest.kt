package io.github.korppujauho.pocketts

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun dialog() = rule.setContent {
        PocketTsTheme {
            ServerGroupsDialog(
                ServerModel.Client(3, 1, "Lee", databaseId = 234, serverGroups = listOf(7)), Example.groupChoices,
                onChange = { g, member -> asked += g.id to member }, onDismiss = {},
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
    fun theDefaultGroupIsGreyedOut() {
        dialog()
        rule.onNodeWithText("Guest").assertIsNotEnabled().performClick()
        assertEquals(emptyList<Pair<Int, Boolean>>(), asked)
    }
}
