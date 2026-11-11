package io.github.korppujauho.pocketts

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Favourites in folders: a folder is there while a favourite names it, in the order its first favourite comes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FavouriteFoldersTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val original = Secrets.box

    @Before
    fun softwareKey() {
        Secrets.box = SecretBox(SoftwareKey())
    }

    @After
    fun keystoreKey() {
        Secrets.box = original
    }

    private val a = Favorite("a.example.com")
    private val b = Favorite("b.example.com", folder = "Friends")
    private val c = Favorite("c.example.com")

    @Test
    fun foldersComeInTheirFirstFavouritesOrder() {
        assertEquals(listOf("" to listOf(a, c), "Friends" to listOf(b)), Favorites.byFolder(listOf(a, b, c)))
        assertEquals(emptyList<Pair<String, List<Favorite>>>(), Favorites.byFolder(emptyList()))
    }

    @Test
    fun theFolderIsSavedWithTheFavourite() {
        Favorites.save(context, listOf(a, b.copy(password = "pw")))
        assertEquals(listOf("", "Friends"), Favorites.load(context).map { it.folder })
        assertEquals(listOf("", "pw"), Favorites.load(context).map { it.password })
    }

    @Test
    fun movedToAnotherFolderItComesLast() {
        // Edited in place while it stays in its folder
        assertEquals(listOf(a.copy(name = "A"), b, c), Favorites.edited(listOf(a, b, c), a, a.copy(name = "A")))
        // To a new folder: that folder comes last, and the old one stays while it has favourites
        val moved = Favorites.edited(listOf(a, b, c), a, a.copy(folder = "Work"))
        assertEquals(listOf("Friends", "", "Work"), Favorites.byFolder(moved).map { it.first })
        // The last one out of a folder: the folder is gone
        assertEquals(listOf(""), Favorites.byFolder(Favorites.edited(listOf(a, b, c), b, b.copy(folder = ""))).map { it.first })
        // Only one connects automatically
        val auto = Favorites.edited(listOf(a.copy(auto = true), b), b, b.copy(auto = true))
        assertEquals(listOf(false, true), auto.map { it.auto })
    }

    @Test
    fun aFolderIsRenamedOrJoinedWithAnother() {
        assertEquals(listOf("Home", "Friends", "Home"), Favorites.renamed(listOf(a, b, c), "", "Home").map { it.folder })
        // A name another folder has: the two become one
        val joined = Favorites.renamed(listOf(a, b, c), "Friends", "")
        assertEquals(listOf("" to listOf(a, b.copy(folder = ""), c)), Favorites.byFolder(joined))
        assertEquals(listOf(a, b, c), Favorites.renamed(listOf(a, b, c), "Friends", " Friends "))
    }

    @Test
    fun aFavouriteIsDraggedWithinItsFolder() {
        val d = Favorite("d.example.com")
        // a, c and d are in the first folder, b (Friends) between them in the list
        assertEquals(listOf(c, b, d, a), Favorites.moved(listOf(a, b, c, d), a, 2))
        assertEquals(listOf(d, b, a, c), Favorites.moved(listOf(a, b, c, d), d, 0))
        assertEquals(listOf(a, b, c, d), Favorites.moved(listOf(a, b, c, d), c, 1)) // where it was
        assertEquals(listOf(c, b, d, a), Favorites.moved(listOf(a, b, c, d), a, 9)) // past the end: last
        // The folders are as they were
        assertEquals(listOf("", "Friends"), Favorites.byFolder(Favorites.moved(listOf(a, b, c, d), a, 2)).map { it.first })
    }

    @Test
    fun aFolderIsMovedUpOrDown() {
        val w = Favorite("w.example.com", folder = "Work")
        val list = listOf(a, b, c, w)
        assertEquals(listOf("Friends", "", "Work"), Favorites.byFolder(Favorites.folderMoved(list, "Friends", up = true)).map { it.first })
        assertEquals(listOf("", "Work", "Friends"), Favorites.byFolder(Favorites.folderMoved(list, "Friends", up = false)).map { it.first })
        // The first one up, the last one down: as it was
        assertEquals(list, Favorites.folderMoved(list, "", up = true))
        assertEquals(list, Favorites.folderMoved(list, "Work", up = false))
        // Its favourites keep their order
        assertEquals(listOf(b, a, c, w), Favorites.folderMoved(list, "Friends", up = true))
    }

    @Test
    fun foldedFoldersAreRemembered() {
        assertEquals(emptySet<String>(), Favorites.folded(context))
        Favorites.setFolded(context, setOf("", "Friends"))
        assertEquals(setOf("", "Friends"), Favorites.folded(context))
    }
}
