package io.github.korppujauho.pocketts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebTS's favourites: a server's address, a name for the lists, the nickname used there, its password (kept only on
 * this phone), whether to connect to it when the app opens (one at most), and the channel to join there (its name, or
 * its path for a subchannel; the server's default channel when empty) with that channel's password. The channel's id is
 * learned when it's first joined, so it's found again after it's renamed or moved. The folder it's shown in is just its
 * name ("" is the first one, shown as "Favourites" until renamed): a folder is there while a favourite names it.
 */
data class Favorite(
    val address: String,
    val name: String = "",
    val nickname: String = "",
    val password: String = "",
    val auto: Boolean = false,
    val filePort: Int? = null, // the server's file transfer port when it isn't the one the server tells (advanced)
    val channel: String = "",
    val channelPassword: String = "",
    val channelId: Int = 0,
    val folder: String = "",
) {
    val label: String get() = name.ifBlank { address }
}

object Favorites {
    private fun prefs(c: Context) = c.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private const val PASSWORD = "favorites/password" // one in the list, as an earlier version kept it
    private const val PASSWORDS = "favorites/passwords"
    private const val CHANNEL_PASSWORDS = "favorites/channelPasswords"

    /**
     * The passwords (the servers' and the channels') are kept apart from the list, encrypted with the other secrets (see [secretPrefs]): the list comes
     * along when the app's data is copied to a new phone, the passwords don't. Ones an earlier version kept in the list
     * are moved there now.
     */
    fun load(c: Context): List<Favorite> = try {
        val a = JSONArray(prefs(c).getString("list", "[]"))
        val inList = (0 until a.length()).any { a.getJSONObject(it).has("password") }
        val passwords = if (inList) JSONArray() else passwords(c, "favorites", PASSWORDS)
        val channelPasswords = passwords(c, "favoriteChannels", CHANNEL_PASSWORDS)
        val list = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Favorite(
                o.optString("address"), o.optString("name"), o.optString("nickname"),
                if (inList) password(o.optString("password")) else passwords.optString(i), o.optBoolean("auto"),
                o.optInt("filePort", 0).takeIf { it in 1..65535 }, o.optString("channel"), channelPasswords.optString(i),
                o.optInt("channelId", 0), o.optString("folder"),
            )
        }.filter { it.address.isNotBlank() }
        if (inList) save(c, list)
        list
    } catch (_: Exception) {
        emptyList()
    }

    /** The passwords saved under [name], in the list's order. */
    private fun passwords(c: Context, name: String, label: String): JSONArray = try {
        JSONArray(secretPrefs(c).getSecret(name, label) ?: "[]")
    } catch (e: SecretBox.Unreadable) {
        AppLog.log("The favourites' passwords can't be read: ${e.message}")
        JSONArray()
    }

    private fun password(stored: String) = try {
        Secrets.box.open(stored, PASSWORD)
    } catch (e: SecretBox.Unreadable) {
        AppLog.log("A favourite's password can't be read: ${e.message}")
        ""
    }

    fun save(c: Context, list: List<Favorite>) {
        val a = JSONArray()
        // At most one connects automatically: the last one marked
        val autoAt = list.indexOfLast { it.auto }
        list.forEachIndexed { i, f ->
            a.put(
                JSONObject().put("address", f.address.trim()).put("name", f.name.trim()).put("nickname", f.nickname.trim())
                    .put("auto", i == autoAt).apply {
                        f.filePort?.let { put("filePort", it) }
                        if (f.channel.isNotBlank()) put("channel", f.channel.trim())
                        if (f.channel.isNotBlank() && f.channelId > 0) put("channelId", f.channelId)
                        if (f.folder.isNotBlank()) put("folder", f.folder.trim())
                    }
            )
        }
        val secrets = secretPrefs(c).edit()
        if (list.all { it.password.isEmpty() }) secrets.remove("favorites")
        else secrets.putSecret("favorites", PASSWORDS, JSONArray(list.map { it.password }).toString())
        if (list.all { it.channelPassword.isEmpty() }) secrets.remove("favoriteChannels")
        else secrets.putSecret("favoriteChannels", CHANNEL_PASSWORDS, JSONArray(list.map { it.channelPassword }).toString())
        secrets.commit()
        prefs(c).edit().putString("list", a.toString()).apply()
    }

    /** The favourites by folder: the folders in the order their first favourite comes, each one's favourites in theirs. */
    fun byFolder(list: List<Favorite>): List<Pair<String, List<Favorite>>> = list.groupBy { it.folder }.toList()

    /** A favourite edited: in its place, or last when it went to another folder (a new folder then comes last). */
    fun edited(list: List<Favorite>, old: Favorite, new: Favorite): List<Favorite> {
        // At most one connects automatically
        val others = if (new.auto) list.map { if (it == old) it else it.copy(auto = false) } else list
        return if (new.folder == old.folder) others.map { if (it == old) new else it } else others.filter { it != old } + new
    }

    /** A folder renamed; a name another folder has joins the two (the favourites keep their order). */
    fun renamed(list: List<Favorite>, folder: String, name: String): List<Favorite> {
        val to = name.trim()
        return list.map { if (it.folder == folder) it.copy(folder = to) else it }
    }

    /** The folders folded shut on the connect screen. */
    fun folded(c: Context): Set<String> = prefs(c).getStringSet("folded", emptySet()).orEmpty().toSet()

    fun setFolded(c: Context, folders: Set<String>) = prefs(c).edit().putStringSet("folded", folders).apply()

    /** The same server, however it was typed ("Example.com:9987" is "example.com"). */
    fun same(a: String, b: String): Boolean = normal(a) == normal(b)

    private fun normal(s: String) = s.trim().lowercase().removePrefix("ts3server://").removeSuffix("/").removeSuffix(":9987")
}
