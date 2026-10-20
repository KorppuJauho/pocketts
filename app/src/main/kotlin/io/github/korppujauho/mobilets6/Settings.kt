package io.github.korppujauho.mobilets6

import android.content.Context
import io.github.korppujauho.mobilets6.proto.Identity

/** What the app remembers: the last server and nickname, and the TeamSpeak identity (created once). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var nickname: String
        get() = prefs.getString("nickname", "").orEmpty()
        set(v) = prefs.edit().putString("nickname", v).apply()

    var address: String
        get() = prefs.getString("address", "").orEmpty()
        set(v) = prefs.edit().putString("address", v).apply()

    var serverPassword: String
        get() = prefs.getString("serverPassword", "").orEmpty()
        set(v) = prefs.edit().putString("serverPassword", v).apply()

    /** Kept in TeamSpeak's own export format, so it can be moved to and from other TeamSpeak apps later. */
    fun identity(): Identity {
        prefs.getString("identity", null)?.let { saved ->
            try {
                return Identity.import(saved)
            } catch (e: Exception) {
                AppLog.log("The saved identity couldn't be read, making a new one: ${e.message}")
            }
        }
        val id = Identity.generate(securityLevel = 8)
        prefs.edit().putString("identity", id.export()).apply()
        AppLog.log("Made a new identity (security level ${id.securityLevel})")
        return id
    }

    companion object {
        /** "host", "host:port", "[ipv6]:port" or a ts3server:// link; the port defaults to 9987. */
        fun parseAddress(input: String): Pair<String, Int>? {
            val t = input.trim()
                .removePrefix("ts3server://").removePrefix("ts://")
                .substringBefore('?').trimEnd('/')
            if (t.isEmpty()) return null
            return when {
                t.startsWith("[") -> {
                    val host = t.substringAfter('[').substringBefore(']')
                    val port = t.substringAfter("]", "").removePrefix(":").toIntOrNull() ?: 9987
                    host to port
                }
                t.count { it == ':' } == 1 -> {
                    val port = t.substringAfter(':').toIntOrNull() ?: return null
                    t.substringBefore(':') to port
                }
                else -> t to 9987
            }.takeIf { it.first.isNotEmpty() && it.second in 1..65535 }
        }
    }
}
