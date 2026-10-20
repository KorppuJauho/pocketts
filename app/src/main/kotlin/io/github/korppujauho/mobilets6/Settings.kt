package io.github.korppujauho.mobilets6

import android.content.Context
import io.github.korppujauho.mobilets6.proto.Identity

/** What the app remembers: the last server and nickname, and the TeamSpeak identity (created once). */
class Settings(private val context: Context) {
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

    /** How loud each person is for us, by their identity (1 = as is). */
    fun volumes(): Map<String, Float> =
        context.getSharedPreferences("volumes", Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? Float)?.let { k to it } }.toMap()

    fun setVolume(uid: String, volume: Float) {
        val p = context.getSharedPreferences("volumes", Context.MODE_PRIVATE).edit()
        if (volume == 1f) p.remove(uid) else p.putFloat(uid, volume)
        p.apply()
    }

    /** Takes an identity exported by a TeamSpeak app (used from the next connect); null when it worked, else why not. */
    fun importIdentity(text: String): String? = try {
        val id = Identity.import(text.trim())
        prefs.edit().putString("identity", id.export()).apply()
        AppLog.log("Imported an identity (security level ${id.securityLevel})")
        null
    } catch (e: Exception) {
        "That isn't a TeamSpeak identity."
    }

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
}
