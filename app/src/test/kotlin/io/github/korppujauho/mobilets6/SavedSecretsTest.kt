package io.github.korppujauho.mobilets6

import android.content.Context
import io.github.korppujauho.mobilets6.proto.Identity
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What an update finds saved by an older version (in plain text) gets encrypted, and nothing is lost. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SavedSecretsTest {
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
    fun oldPlainValuesAreKeptAndEncrypted() {
        val id = Identity.generate(securityLevel = 0)
        prefs("settings").edit().putString("identity", id.export()).putString("serverPassword", "server pw").commit()
        prefs("voice").edit().putString("turnPassword", "relay pw").commit()
        prefs("favorites").edit()
            .putString("list", """[{"address":"ts.example.com","name":"Friends","password":"fav pw","auto":true},{"address":"b.example.com"}]""")
            .commit()

        assertEquals(id.uid, Settings(context).identity().uid)
        assertEquals("server pw", Settings(context).serverPassword)
        assertEquals("relay pw", VoiceSettings.load(context).turnPassword)
        val favs = Favorites.load(context)
        assertEquals(listOf("fav pw", ""), favs.map { it.password })
        assertTrue(favs[0].auto)

        // Now saved encrypted, and read the same again
        for (stored in listOf(
            prefs("settings").getString("identity", null)!!, prefs("settings").getString("serverPassword", null)!!,
            prefs("voice").getString("turnPassword", null)!!,
            JSONArray(prefs("favorites").getString("list", null)).getJSONObject(0).getString("password"),
        )) assertTrue(stored, SecretBox.isSealed(stored))
        assertEquals("", JSONArray(prefs("favorites").getString("list", null)).getJSONObject(1).getString("password"))
        assertEquals(id.uid, Settings(context).identity().uid)
        assertEquals("server pw", Settings(context).serverPassword)
        assertEquals("relay pw", VoiceSettings.load(context).turnPassword)
        assertEquals("fav pw", Favorites.load(context)[0].password)
    }

    @Test
    fun newValuesAreSavedEncrypted() {
        Settings(context).serverPassword = "typed pw"
        VoiceSettings(turnPassword = "relay pw").save(context)
        Favorites.save(context, listOf(Favorite("ts.example.com", password = "fav pw")))
        assertNotNull(Settings(context).importIdentity("not an identity"))
        val id = Identity.generate(securityLevel = 0)
        assertNull(Settings(context).importIdentity("[Identity]\nid=Main\nidentity=\"${id.export()}\"\n"))

        assertTrue(SecretBox.isSealed(prefs("settings").getString("serverPassword", null)!!))
        assertTrue(SecretBox.isSealed(prefs("voice").getString("turnPassword", null)!!))
        assertTrue(SecretBox.isSealed(prefs("settings").getString("identity", null)!!))
        assertTrue(prefs("favorites").getString("list", null)!!.let { "fav pw" !in it })
        assertEquals("typed pw", Settings(context).serverPassword)
        assertEquals("relay pw", VoiceSettings.load(context).turnPassword)
        assertEquals("fav pw", Favorites.load(context)[0].password)
        assertEquals(id.uid, Settings(context).identity().uid)
    }

    @Test
    fun unreadableIdentityIsKeptNotReplaced() {
        Settings(context).identity()
        Settings(context).serverPassword = "server pw"
        val saved = prefs("settings").getString("identity", null)
        Secrets.box = SecretBox(SoftwareKey()) // as if the key were gone

        assertThrows(Settings.IdentityUnreadable::class.java) { Settings(context).identity() }
        assertEquals(saved, prefs("settings").getString("identity", null))
        assertNotNull(Settings(context).identityProblem())
        assertEquals("", Settings(context).serverPassword)

        // Settings › New identity: a new one at the next connect
        Settings(context).forgetIdentity()
        assertNull(Settings(context).identityProblem())
        val fresh = Settings(context).identity()
        assertTrue(SecretBox.isSealed(prefs("settings").getString("identity", null)!!))
        assertEquals(fresh.uid, Settings(context).identity().uid)
    }
}
