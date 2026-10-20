package io.github.korppujauho.mobilets6

import android.content.Context
import io.github.korppujauho.mobilets6.proto.Identity

/** What the app remembers: the last server and nickname, and the TeamSpeak identity (created once). */
class Settings(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secrets = secretPrefs(context)

    var nickname: String
        get() = prefs.getString("nickname", "").orEmpty()
        set(v) = prefs.edit().putString("nickname", v).apply()

    var address: String
        get() = prefs.getString("address", "").orEmpty()
        set(v) = prefs.edit().putString("address", v).apply()

    /** Encrypted on this phone and not copied to a new one (see [secretPrefs]), as are the identity and the other passwords. */
    var serverPassword: String
        get() = try {
            secrets.getSecret("serverPassword", "settings/serverPassword", old = prefs).orEmpty()
        } catch (e: SecretBox.Unreadable) {
            AppLog.log("The remembered server password can't be read: ${e.message}")
            ""
        }
        set(v) = secrets.edit().putSecret("serverPassword", "settings/serverPassword", v).apply()

    /** How loud each person is for us, by their identity (1 = as is). */
    fun volumes(): Map<String, Float> =
        context.getSharedPreferences("volumes", Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? Float)?.let { k to it } }.toMap()

    fun setVolume(uid: String, volume: Float) {
        val p = context.getSharedPreferences("volumes", Context.MODE_PRIVATE).edit()
        if (volume == 1f) p.remove(uid) else p.putFloat(uid, volume)
        p.apply()
    }

    /** Takes an identity exported by a TeamSpeak app, or its .ini file (used from the next connect); null when it worked, else why not. */
    fun importIdentity(text: String): String? = try {
        val id = Identity.import(text)
        secrets.edit().putSecret("identity", IDENTITY, id.export()).apply()
        AppLog.log("Imported an identity (security level ${id.securityLevel})")
        null
    } catch (e: Exception) {
        "That isn't a TeamSpeak identity."
    }

    /** Why the saved identity can't be used, when it can't (it's then left as it is, not replaced by a new one). */
    fun identityProblem(): String? = try {
        secrets.getSecret("identity", IDENTITY, old = prefs)
        null
    } catch (e: SecretBox.Unreadable) {
        UNREADABLE
    }

    /** Lets go of the saved identity: a new one is made at the next connect. */
    fun forgetIdentity() {
        secrets.edit().remove("identity").apply()
        prefs.edit().remove("identity").apply()
        AppLog.log("Let go of the saved identity; a new one is made at the next connect")
    }

    /**
     * Kept in TeamSpeak's own export format (encrypted on this phone, and not copied to a new one: export it to move
     * it), so it can be moved to and from other TeamSpeak apps. Throws [IdentityUnreadable] when the saved one can't be decrypted: that one is kept, not silently replaced.
     */
    fun identity(): Identity {
        val saved = try {
            secrets.getSecret("identity", IDENTITY, old = prefs)
        } catch (e: SecretBox.Unreadable) {
            AppLog.log("The saved identity can't be decrypted, keeping it and not making a new one: ${e.message}")
            throw IdentityUnreadable()
        }
        saved?.let {
            try {
                return Identity.import(it)
            } catch (e: Exception) {
                AppLog.log("The saved identity couldn't be read, making a new one: ${e.message}")
            }
        }
        val id = Identity.generate(securityLevel = 8)
        secrets.edit().putSecret("identity", IDENTITY, id.export()).apply()
        AppLog.log("Made a new identity (security level ${id.securityLevel})")
        return id
    }

    class IdentityUnreadable : Exception(UNREADABLE)

    private companion object {
        const val IDENTITY = "settings/identity"
        const val UNREADABLE = "The saved identity can't be opened on this phone. Import yours in Settings › General › " +
            "TeamSpeak identity, or make a new one there."
    }
}
