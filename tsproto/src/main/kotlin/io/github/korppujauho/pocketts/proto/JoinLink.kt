package io.github.korppujauho.pocketts.proto

import java.net.URLDecoder

/**
 * A TeamSpeak link, as web pages and other apps give them:
 * `ts3server://host[:port]?port=…&nickname=…&password=…&channel=Parent/Child&channelpassword=…&token=…`, the same with
 * `teamspeak://`, and `teamspeak://connect?address=host:port&…`; and TeamSpeak's own links: `tmspk.gg/s=host?…` (the
 * same as `teamspeak://host?…`) and invites, `tmspk.gg/<code>` (`teamspeak://invite=<code>`), whose server [Invites]
 * looks up.
 *
 * Based on Stefan3Dx's version in his fork (see CREDITS.md).
 */
data class JoinLink(
    val address: String, // host, or host:port
    val nickname: String = "",
    val password: String = "", // the server's
    val channel: String = "", // a path of channel names, "Parent/Child", or "/<channel id>"
    val channelPassword: String = "",
    val token: String = "", // a privilege key: gives this server's rights to whoever uses it
    val invite: String = "", // an invite's code, until [Invites] has looked up its server ([address] is empty till then)
    val serverName: String = "", // the server's name, as an invite tells it
    val channelName: String = "", // the channel's name, as an invite tells it ([channel] is then its id)
) {
    companion object {
        private val SCHEMES = listOf("ts3server://", "teamspeak://")

        private val TMSPK = Regex("""^(?:https?://)?(?:www\.)?tmspk\.gg/(.*)$""", RegexOption.IGNORE_CASE)
        private val CODE = Regex("""^[A-Za-z0-9_-]{4,64}$""")

        /** Whether [link] is a TeamSpeak link at all (it may still be broken). */
        fun isLink(link: String) = link.trim().let { s -> SCHEMES.any { s.startsWith(it, ignoreCase = true) } || TMSPK.matches(s) }

        /** The link's parts, or null when it isn't one or names no usable address. */
        fun parse(link: String): JoinLink? {
            var s = link.trim()
            // tmspk.gg: what its page turns into (s= an address, else an invite's code)
            TMSPK.matchEntire(s)?.let { m ->
                val path = m.groupValues[1]
                s = if (path.startsWith("s=") || path.startsWith("s/")) "teamspeak://" + path.substring(2)
                else "teamspeak://invite=" + path.substringBefore('?').substringBefore('#').trimEnd('/')
            }
            val scheme = SCHEMES.firstOrNull { s.startsWith(it, ignoreCase = true) } ?: return null
            val rest = s.substring(scheme.length)
            // An invite: only its code here; its server comes from TeamSpeak's invite service
            val head = rest.substringBefore('?').substringBefore('#').trimEnd('/')
            if (head.startsWith("invite=", ignoreCase = true) || head.startsWith("invite/", ignoreCase = true)) {
                val code = decode(head.substring(7))
                return if (CODE.matches(code)) JoinLink(address = "", invite = code) else null
            }
            val q = rest.substringAfter('?', "").split('&').filter { '=' in it }.associate {
                it.substringBefore('=').lowercase() to decode(it.substringAfter('='))
            }
            var host = decode(rest.substringBefore('?').substringBefore('#').trimEnd('/'))
            if (host.equals("connect", ignoreCase = true) || host.isEmpty()) host = q["address"] ?: q["addr"] ?: q["server"] ?: return null
            host = host.substringBefore('/')
            // The address's own port wins over ?port=
            val typed = ServerAddress.parse(host) ?: return null
            val port = typed.port ?: q["port"]?.let { p -> p.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null }
            val address = when {
                port == null -> typed.host
                typed.host.contains(':') -> "[${typed.host}]:$port"
                else -> "${typed.host}:$port"
            }
            val cid = q["cid"]?.toIntOrNull()?.takeIf { it > 0 }
            return JoinLink(
                address = address,
                nickname = q["nickname"].orEmpty().trim().take(30),
                password = q["password"].orEmpty(),
                channel = q["channel"]?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: cid?.let { "/$it" }.orEmpty(),
                channelPassword = q["channelpassword"].orEmpty(),
                token = (q["token"] ?: q["privilegekey"]).orEmpty().trim(),
            )
        }

        private fun decode(s: String): String = try {
            // A plus is a plus in these links (not a space)
            URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            s
        }
    }
}
