package io.github.korppujauho.pocketts

import android.content.Context
import io.github.korppujauho.pocketts.proto.Identity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Several identities: the one an older version kept becomes the first, the default one, with its nickname, and nothing
 * is moved; new ones are kept apart, each encrypted; a deleted one is gone, and its favourites use the default one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IdentitiesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val original = Secrets.box

    @Before
    fun softwareKey() {
        Secrets.box = SecretBox(SoftwareKey())
    }

    @After
    fun keystoreKey() {
        Secrets.box = original
    }

    @Test
    fun theOldIdentityAndNicknameBecomeTheDefaultOne() {
        // As 1.8.0 left them: one identity and the nickname of its own
        val id = Identity.generate(securityLevel = 0)
        prefs("secrets").edit().putString("identity", Secrets.box.seal(id.export(), "settings/identity")).commit()
        prefs("settings").edit().putString("nickname", "Jamie").commit()

        val s = Settings(context)
        val all = s.identities()
        assertEquals(1, all.size)
        assertEquals("Jamie", all.single().nickname)
        assertEquals(context.getString(R.string.identity_first_name), all.single().name)
        assertEquals(all.single().key, s.defaultIdentity)
        assertEquals(id.uid, s.identity().uid)
        assertEquals("Jamie", s.nickname)
        assertFalse(prefs("settings").contains("nickname"))
        // Asked again, the same one (not made a second time)
        assertEquals(all, Settings(context).identities())
    }

    @Test
    fun aNewIdentityIsKeptApartAndEncrypted() {
        val s = Settings(context)
        val main = s.identity()
        val key = s.newIdentity("Clan", "Jamie|TAG")!!
        assertEquals(listOf(s.defaultIdentity, key), s.identities().map { it.key })
        val clan = s.identity(key)
        assertNotEquals(main.uid, clan.uid)
        assertEquals(main.uid, s.identity().uid) // the default one is still the first
        assertTrue(SecretBox.isSealed(prefs("secrets").getString("identity.$key", null)!!))
        assertEquals("Jamie|TAG", s.identityEntry(key).nickname)

        s.defaultIdentity = key
        assertEquals(clan.uid, Settings(context).identity().uid)
        assertEquals("Jamie|TAG", Settings(context).nickname)
    }

    @Test
    fun anImportedIdentityIsTheSameOne() {
        val s = Settings(context)
        assertNull(s.newIdentity("Old PC", "Jamie", "not an identity"))
        assertEquals(1, s.identities().size)
        val id = Identity.generate(securityLevel = 0)
        val key = s.newIdentity("Old PC", "Jamie", "[Identity]\nid=Main\nidentity=\"${id.export()}\"\n")!!
        assertEquals(id.uid, s.identity(key).uid)
    }

    @Test
    fun deletingTheDefaultOneMakesTheNextOneDefault() {
        val s = Settings(context)
        val first = s.defaultIdentity
        s.identity()
        val second = s.newIdentity("Clan", "Jamie")!!
        s.deleteIdentity(first)
        assertEquals(listOf(second), s.identities().map { it.key })
        assertEquals(second, s.defaultIdentity)
        assertNull(prefs("secrets").getString("identity", null))
        // A favourite's identity that's gone: the default one
        assertEquals(second, s.identityKey(first))
        assertEquals(second, s.identityKey(""))
    }

    @Test
    fun theLastOneIsWipedNotDeleted() {
        val s = Settings(context)
        val old = s.identity()
        s.deleteIdentity(s.defaultIdentity)
        assertEquals(1, s.identities().size)
        assertNotEquals(old.uid, s.identity().uid) // a new one, made at the next use
    }

    @Test
    fun renamingKeepsTheIdentity() {
        val s = Settings(context)
        val id = s.identity()
        s.editIdentity(s.defaultIdentity, "Phone", "Lee")
        assertEquals("Phone", s.identityEntry().name)
        assertEquals("Lee", s.nickname)
        assertEquals(id.uid, s.identity().uid)
    }

    @Test
    fun aFavouriteKeepsItsIdentity() {
        Favorites.save(context, listOf(Favorite("ts.example.com", identity = "abc12345"), Favorite("b.example.com")))
        val loaded = Favorites.load(context)
        assertEquals("abc12345", loaded[0].identity)
        assertEquals("", loaded[1].identity)
        assertNotNull(prefs("favorites").getString("list", null))
    }
}
