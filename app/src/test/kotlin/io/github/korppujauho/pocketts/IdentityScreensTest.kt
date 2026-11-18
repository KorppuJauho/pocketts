package io.github.korppujauho.pocketts

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.korppujauho.pocketts.ui.ConnectActions
import io.github.korppujauho.pocketts.ui.ConnectScreen
import io.github.korppujauho.pocketts.ui.FavoriteDialog
import io.github.korppujauho.pocketts.ui.IdentityInfo
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.SettingsActions
import io.github.korppujauho.pocketts.ui.SettingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The identities on screen: listed in Settings › General with the default one starred, one opens with a tap; with
 * several, the connect form and Edit favourite let one be chosen; with one, nothing more shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class IdentityScreensTest {
    @get:Rule val rule = createComposeRule()

    private class Seen : ConnectActions {
        val connects = mutableListOf<Pair<String, String>>() // nickname and identity
        override fun connect(address: String, nickname: String, password: String, remember: Boolean, favorite: Boolean, identity: String) {
            connects += nickname to identity
        }
        override fun connectFavorite(f: Favorite) {}
        override fun saveFavorite(old: Favorite?, new: Favorite) {}
        override fun removeFavorite(f: Favorite) {}
        override fun toggleAuto(f: Favorite) {}
    }

    private fun settings(list: List<IdentityInfo>) = object : SettingsActions by Example.noSettings {
        override fun identities() = list
    }

    @Test
    fun generalListsThemAndOneOpens() {
        rule.setContent { PocketTsTheme { SettingsContent(VoiceSettings(), settings(Example.identities), {}, modifier = Modifier.fillMaxSize()) } }
        // Folded at first: how many, and which one connects
        rule.onNodeWithText("Identities").assertIsDisplayed()
        rule.onNodeWithText("Main").assertIsDisplayed()
        rule.onNodeWithText("Clan").assertDoesNotExist()
        rule.onNodeWithText("Identities").performClick()
        rule.onNodeWithText("Jamie · Level 24").assertIsDisplayed()
        rule.onNodeWithText("New identity").assertIsDisplayed()
        rule.onNodeWithContentDescription("Default").assertIsDisplayed()
        rule.onNodeWithText("Clan").performClick()
        rule.waitForIdle()
        // Its window: the name and nickname to edit, and Make default (it isn't the default one)
        rule.onAllNodesWithText("Jamie|TAG")[0].assertIsDisplayed()
        rule.onNodeWithText("Make default").assertIsDisplayed()
    }

    @Test
    fun theConnectFormConnectsAsTheOneChosen() {
        val seen = Seen()
        rule.setContent {
            PocketTsTheme { ConnectScreen("Jamie", "ts.example.com", "", emptyList(), null, seen, settingsActions = settings(Example.identities)) }
        }
        rule.onNodeWithText("Main").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Clan").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Connect")[1].performClick() // the button; [0] is the page switch
        assertEquals(listOf("Jamie|TAG" to "clan"), seen.connects)
    }

    @Test
    fun withOneIdentityTheFormIsAsBefore() {
        rule.setContent {
            PocketTsTheme {
                ConnectScreen("Jamie", "ts.example.com", "", emptyList(), null, Seen(), settingsActions = settings(Example.identities.take(1)))
            }
        }
        assertEquals(0, rule.onAllNodesWithText("Identity").fetchSemanticsNodes().size)
    }

    @Test
    fun aFavouriteKeepsTheIdentityChosen() {
        var saved: Favorite? = null
        rule.setContent {
            PocketTsTheme { FavoriteDialog(Favorite("ts.example.com", "Friends"), { saved = it }, onDelete = null, identities = Example.identities) {} }
        }
        rule.onNodeWithText("Default (Main)").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Clan").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Save").performClick()
        assertEquals("clan", saved?.identity)
    }
}
