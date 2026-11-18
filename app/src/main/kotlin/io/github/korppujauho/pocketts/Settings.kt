package io.github.korppujauho.pocketts

import android.content.Context
import io.github.korppujauho.pocketts.proto.Identity
import org.json.JSONArray
import org.json.JSONObject

/**
 * One of the TeamSpeak identities kept on this phone: a [name] only the person sees, and the [nickname] it connects
 * with. The identity itself is a secret, kept apart under its [key].
 */
data class IdentityEntry(val key: String, val name: String, val nickname: String)

/** What the app remembers: the last server, and the TeamSpeak identities (each created once) with their nicknames. */
class Settings(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secrets = secretPrefs(context)

    /**
     * The identities. Before there were several, the phone had one identity and a nickname of its own: they become
     * the first entry, the default, with the identity kept where it was.
     */
    fun identities(): List<IdentityEntry> {
        prefs.getString("identities", null)?.let { saved ->
            try {
                val a = JSONArray(saved)
                val list = (0 until a.length()).map { a.getJSONObject(it) }
                    .map { IdentityEntry(it.getString("key"), it.optString("name"), it.optString("nickname")) }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {
            }
        }
        val first = IdentityEntry(MAIN, context.getString(R.string.identity_first_name), prefs.getString("nickname", "").orEmpty())
        saveIdentities(listOf(first))
        prefs.edit().remove("nickname").apply()
        return listOf(first)
    }

    private fun saveIdentities(list: List<IdentityEntry>) {
        val a = JSONArray()
        for (e in list) a.put(JSONObject().put("key", e.key).put("name", e.name.trim()).put("nickname", e.nickname.trim()))
        prefs.edit().putString("identities", a.toString()).apply()
    }

    /** The identity used unless a favourite or the connect screen picks another: one of [identities]. */
    var defaultIdentity: String
        get() {
            val all = identities()
            val key = prefs.getString("defaultIdentity", null)
            return all.firstOrNull { it.key == key }?.key ?: all.first().key
        }
        set(v) = prefs.edit().putString("defaultIdentity", v).apply()

    /** [key] while it's one of the identities, else the default one (a favourite's identity may have been deleted). */
    fun identityKey(key: String?): String = identities().firstOrNull { it.key == key }?.key ?: defaultIdentity

    fun identityEntry(key: String? = null): IdentityEntry {
        val k = identityKey(key)
        return identities().first { it.key == k }
    }

    /** An identity's name and nickname changed. */
    fun editIdentity(key: String, name: String, nickname: String) =
        saveIdentities(identities().map { if (it.key == key) it.copy(name = name, nickname = nickname) else it })

    /** The default identity's nickname. */
    var nickname: String
        get() = identityEntry().nickname
        set(v) = setNickname(defaultIdentity, v)

    fun setNickname(key: String, nickname: String) =
        saveIdentities(identities().map { if (it.key == key) it.copy(nickname = nickname.trim()) else it })

    /**
     * A new identity named [name], made now or taken from [imported] (an identity exported by a TeamSpeak app, or its
     * .ini file): its key, or null when [imported] isn't one.
     */
    fun newIdentity(name: String, nickname: String, imported: String? = null): String? {
        val id = if (imported != null) {
            try {
                Identity.import(imported)
            } catch (e: Exception) {
                return null
            }
        } else {
            Identity.generate(securityLevel = 8)
        }
        val key = java.util.UUID.randomUUID().toString().take(8)
        secrets.edit().putSecret(secretName(key), label(key), id.export()).apply()
        saveIdentities(identities() + IdentityEntry(key, name.trim(), nickname.trim()))
        AppLog.log((if (imported != null) "Imported" else "Made") + " a new identity (security level ${id.securityLevel})")
        return key
    }

    /**
     * Deletes an identity; the default one moves to the first left. The last one isn't deleted but let go of (as
     * [forgetIdentity]): a new one is made in its place at the next connect.
     */
    fun deleteIdentity(key: String) {
        val all = identities()
        if (all.size <= 1) return forgetIdentity(key)
        val wasDefault = key == defaultIdentity
        secrets.edit().remove(secretName(key)).apply()
        if (key == MAIN) prefs.edit().remove("identity").apply()
        saveIdentities(all.filter { it.key != key })
        if (wasDefault) defaultIdentity = identities().first().key
        AppLog.log("Deleted an identity")
    }

    /** Settings › General's identities box was left open (it's folded at first). */
    var identitiesOpen: Boolean
        get() = prefs.getBoolean("identitiesOpen", false)
        set(v) = prefs.edit().putBoolean("identitiesOpen", v).apply()

    /** The connect screen's Connect and Community panel is folded shut to its bar. */
    var connectHidden: Boolean
        get() = prefs.getBoolean("connectHidden", false)
        set(v) = prefs.edit().putBoolean("connectHidden", v).apply()

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
    fun importIdentity(text: String, key: String = defaultIdentity): String? = try {
        val id = Identity.import(text)
        secrets.edit().putSecret(secretName(key), label(key), id.export()).apply()
        AppLog.log("Imported an identity (security level ${id.securityLevel})")
        null
    } catch (e: Exception) {
        context.getString(R.string.identity_not_valid)
    }

    /** Why the saved identity can't be used, when it can't (it's then left as it is, not replaced by a new one). */
    fun identityProblem(key: String = defaultIdentity): String? = try {
        secrets.getSecret(secretName(key), label(key), old = if (key == MAIN) prefs else null)
        null
    } catch (e: SecretBox.Unreadable) {
        context.getString(R.string.identity_unreadable)
    }

    /** Saves the identity in place of the old one (the same identity with a raised security level). */
    fun saveIdentity(id: Identity, key: String = defaultIdentity) {
        secrets.edit().putSecret(secretName(key), label(key), id.export()).apply()
    }

    /** Lets go of a saved identity: a new one is made in its place at the next connect. */
    fun forgetIdentity(key: String = defaultIdentity) {
        secrets.edit().remove(secretName(key)).apply()
        if (key == MAIN) prefs.edit().remove("identity").apply()
        AppLog.log("Let go of the saved identity; a new one is made at the next connect")
    }

    /**
     * Kept in TeamSpeak's own export format (encrypted on this phone, and not copied to a new one: export it to move
     * it), so it can be moved to and from other TeamSpeak apps. Throws [IdentityUnreadable] when the saved one can't be decrypted: that one is kept, not silently replaced.
     */
    fun identity(key: String = defaultIdentity): Identity {
        val saved = try {
            secrets.getSecret(secretName(key), label(key), old = if (key == MAIN) prefs else null)
        } catch (e: SecretBox.Unreadable) {
            AppLog.log("The saved identity can't be decrypted, keeping it and not making a new one: ${e.message}")
            throw IdentityUnreadable(context.getString(R.string.identity_unreadable))
        }
        saved?.let {
            try {
                return Identity.import(it)
            } catch (e: Exception) {
                AppLog.log("The saved identity couldn't be read, making a new one: ${e.message}")
            }
        }
        val id = Identity.generate(securityLevel = 8)
        secrets.edit().putSecret(secretName(key), label(key), id.export()).apply()
        AppLog.log("Made a new identity (security level ${id.securityLevel})")
        return id
    }

    class IdentityUnreadable(message: String) : Exception(message)

    private companion object {
        const val IDENTITY = "settings/identity"
        // The identity the phone had before there were several stays where it was, so nothing is moved
        const val MAIN = "main"

        fun secretName(key: String) = if (key == MAIN) "identity" else "identity.$key"
        fun label(key: String) = if (key == MAIN) IDENTITY else "$IDENTITY/$key"
    }
}
