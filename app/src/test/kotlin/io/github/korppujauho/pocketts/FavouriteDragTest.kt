package io.github.korppujauho.pocketts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import io.github.korppujauho.pocketts.ui.ConnectActions
import io.github.korppujauho.pocketts.ui.ConnectScreen
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A favourite's row on the connect screen: a tap connects; held and dragged it's moved to another place in its
 * folder, and held it connects nothing. A folder's header held opens its edit window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class FavouriteDragTest {
    @get:Rule val rule = createComposeRule()
    private val favs = listOf(
        Favorite("ts.example.com", "Friends' server"), Favorite("voice.example.org:9988"), Favorite("third.example.org", "Third"),
        Favorite("clan.example.net", "Clan", folder = "Gaming"),
    )

    private class Seen : ConnectActions {
        val moves = mutableListOf<Triple<String, String, Int>>()
        val connects = mutableListOf<String>()
        override fun connect(address: String, nickname: String, password: String, remember: Boolean, favorite: Boolean) {}
        override fun connectFavorite(f: Favorite) { connects += f.address }
        override fun saveFavorite(old: Favorite?, new: Favorite) {}
        override fun removeFavorite(f: Favorite) {}
        override fun toggleAuto(f: Favorite) {}
        override fun moveFavorite(f: Favorite, folder: String, to: Int) { moves += Triple(f.address, folder, to) }
    }

    private fun screen(seen: Seen) = rule.setContent {
        PocketTsTheme { ConnectScreen("Jamie", "", "", favs, null, seen, settingsActions = Example.noSettings, connectHidden = true) }
    }

    @Test
    fun heldAndDraggedItMovesAndConnectsNothing() {
        val seen = Seen()
        screen(seen)
        rule.onNodeWithText("Friends' server").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(0f, 170f)) // a row down
        }
        rule.waitForIdle() // the row is lifted and the others have made room
        rule.onAllNodesWithText("Friends' server")[0].performTouchInput { up() } // the row itself: its floating copy has the name too
        rule.waitForIdle()
        assertEquals(listOf(Triple("ts.example.com", "", 1)), seen.moves)
        assertEquals(emptyList<String>(), seen.connects)
    }

    @Test
    fun draggedOverAnotherFolderItMovesThere() {
        val seen = Seen()
        screen(seen)
        rule.onNodeWithText("Third").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(0f, 330f)) // past its folder's end, onto the next folder
        }
        rule.waitForIdle()
        rule.onAllNodesWithText("Third")[0].performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, seen.moves.size)
        assertEquals("third.example.org" to "Gaming", seen.moves[0].first to seen.moves[0].second)
        assertEquals(emptyList<String>(), seen.connects)
    }

    @Test
    fun heldWithoutDraggingItStaysAndConnectsNothing() {
        val seen = Seen()
        screen(seen)
        rule.onNodeWithText("Third").performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(emptyList<Triple<String, String, Int>>(), seen.moves)
        assertEquals(emptyList<String>(), seen.connects)
    }

    @Test
    fun aTapConnects() {
        val seen = Seen()
        screen(seen)
        rule.onNodeWithText("Third").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(listOf("third.example.org"), seen.connects)
    }

    @Test
    fun aFoldersHeaderHeldOpensItsEditWindow() {
        screen(Seen())
        rule.onNodeWithText("Gaming").performTouchInput { longClick() }
        rule.onNodeWithText("Edit folder").assertExists()
        rule.onNodeWithText("Move up").assertExists()
    }
}
