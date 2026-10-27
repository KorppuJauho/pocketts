package io.github.korppujauho.pocketts.proto

import java.io.ByteArrayOutputStream

/**
 * TeamSpeak's invite links (`tmspk.gg/<code>`, made with "Create Invite" in the TeamSpeak app): the code is looked up
 * from TeamSpeak's invite service, as the TeamSpeak app does, which answers with the server's address and name and,
 * when the invite is to a channel, the channel. An invite expires (after 30 days by default); then the service no
 * longer knows it.
 */
object Invites {
    data class Invite(val address: String, val name: String, val channelId: Int?, val channelName: String)

    /** Asks the invite service: the code in, its answer (JSON) out; null when it knows no such invite. */
    fun interface Lookup {
        fun query(code: String): String?
    }

    /** TeamSpeak's own invite service. Gives up after a few seconds. */
    object TeamSpeak : Lookup {
        private const val SERVICE = "https://invites.teamspeak.com/query/?id="

        override fun query(code: String): String? {
            val c = java.net.URI(SERVICE + java.net.URLEncoder.encode(code, "UTF-8")).toURL().openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.instanceFollowRedirects = false
            try {
                return when (val status = c.responseCode) {
                    200 -> c.inputStream.use { input ->
                        // An invite is a few hundred bytes
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(4096)
                        while (out.size() < 16_384) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                        out.toString("UTF-8")
                    }
                    404 -> null
                    else -> throw IllegalStateException("HTTP $status")
                }
            } finally {
                c.disconnect()
            }
        }
    }

    private val SPACER = Regex("""^\[.*spacer.*]""", RegexOption.IGNORE_CASE)

    /**
     * The invite in the service's answer, or null when it isn't one PocketTS can use: not to a server, expired by its
     * own time ([now], seconds), or without an address.
     */
    fun parse(json: String, now: Long = System.currentTimeMillis() / 1000): Invite? {
        @Suppress("UNCHECKED_CAST")
        val m = try { Json.parse(json) as? Map<String, Any?> } catch (_: Exception) { null } ?: return null
        val type = m["type"]?.toString()
        if (type != null && type != "server") return null
        val expires = (m["expires"] as? Number)?.toLong()
        if (expires != null && expires in 1 until now) return null
        val given = m["address"]?.toString()?.trim().orEmpty()
        val typed = ServerAddress.parse(given) ?: return null
        // The TeamSpeak app writes the default port into the invite even when the server was typed without one: for a
        // name, that's left off, so its DNS entry (which may name another host and port) is used as for a typed name
        val address = if (typed.port == ServerAddress.DEFAULT_PORT && !ServerAddress.isIpLiteral(typed.host)) typed.host else given
        val channelId = m["channel_id"]?.toString()?.toIntOrNull()?.takeIf { it > 0 }
        return Invite(
            address = address,
            name = m["name"]?.toString()?.trim().orEmpty().take(80),
            channelId = channelId,
            channelName = if (channelId == null) "" else m["channel_name"]?.toString().orEmpty().replace(SPACER, "").trim().take(80),
        )
    }

    /**
     * [link] (an invite) with its server filled in: the address, the server's and the channel's names, and the channel
     * to join first. Null when there's no such invite (or it has expired); trouble reaching the service throws.
     */
    fun resolve(link: JoinLink, lookup: Lookup = TeamSpeak): JoinLink? {
        if (link.invite.isEmpty()) return link
        val invite = parse(lookup.query(link.invite) ?: return null) ?: return null
        return link.copy(
            address = invite.address, invite = "", serverName = invite.name,
            channel = invite.channelId?.let { "/$it" } ?: link.channel, channelName = invite.channelName,
        )
    }
}
