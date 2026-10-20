package io.github.korppujauho.mobilets6

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebTS's favourites: a server's address, a name for the lists, the nickname used there, its password (kept only on
 * this phone), and whether to connect to it when the app opens (one at most).
 */
data class Favorite(
    val address: String,
    val name: String = "",
    val nickname: String = "",
    val password: String = "",
    val auto: Boolean = false,
) {
    val label: String get() = name.ifBlank { address }
}

object Favorites {
    private fun prefs(c: Context) = c.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(c: Context): List<Favorite> = try {
        val a = JSONArray(prefs(c).getString("list", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Favorite(o.optString("address"), o.optString("name"), o.optString("nickname"), o.optString("password"), o.optBoolean("auto"))
        }.filter { it.address.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(c: Context, list: List<Favorite>) {
        val a = JSONArray()
        // At most one connects automatically: the last one marked
        val autoAt = list.indexOfLast { it.auto }
        list.forEachIndexed { i, f ->
            a.put(
                JSONObject().put("address", f.address.trim()).put("name", f.name.trim()).put("nickname", f.nickname.trim())
                    .put("password", f.password).put("auto", i == autoAt)
            )
        }
        prefs(c).edit().putString("list", a.toString()).apply()
    }

    /** The same server, however it was typed ("Example.com:9987" is "example.com"). */
    fun same(a: String, b: String): Boolean = normal(a) == normal(b)

    private fun normal(s: String) = s.trim().lowercase().removePrefix("ts3server://").removeSuffix("/").removeSuffix(":9987")
}
