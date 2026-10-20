package io.github.korppujauho.mobilets6.proto

/** What we know about the server: channels, who is where, who is talking. Fed with every command (on one thread). */
class ServerModel {
    private companion object {
        val SPACER = Regex("""^\[([^\]]*)spacer[^\]]*](.*)$""")
        val LINES = mapOf(
            "---" to Spacer.Line.DASHED, "..." to Spacer.Line.DOTTED, "-.-" to Spacer.Line.DASHED,
            "___" to Spacer.Line.SOLID, "-.." to Spacer.Line.DOTTED, "" to Spacer.Line.SOLID,
        )
    }

    data class Channel(
        val id: Int,
        val parent: Int,
        val order: Int, // the channel this one comes after among its siblings (0 = first)
        val name: String,
        val topic: String = "",
        val isDefault: Boolean = false,
        val hasPassword: Boolean = false,
        val codec: Int = 4,
        val neededTalkPower: Int = 0,
        val maxClients: Int = -1,
        val permanent: Boolean = true,
        val semiPermanent: Boolean = false,
        val iconId: Long = 0,
        val description: String = "",
    ) {
        /** A spacer as TeamSpeak decides it: a top-level permanent channel named `[<kind>spacer<anything>]<text>`. */
        val spacer: Spacer? get() {
            if (parent != 0 || !permanent) return null
            val m = SPACER.matchEntire(name) ?: return null
            val kind = m.groupValues[1]
            val text = m.groupValues[2]
            val align = when (kind) { "c" -> Spacer.Align.CENTER; "r" -> Spacer.Align.RIGHT; else -> Spacer.Align.LEFT }
            val line = LINES[text]
            return Spacer(align, if (line != null) "" else text, repeat = kind == "*" && line == null, line = line)
        }

        /** The name without a spacer's [...spacer...] prefix. */
        val displayName: String get() = name.replace(Regex("""^\[.*spacer.*]""", RegexOption.IGNORE_CASE), "")
    }

    /** How a spacer channel is drawn: text placed left/centre/right (or repeated across), or a line. */
    data class Spacer(val align: Align, val text: String, val repeat: Boolean, val line: Line?) {
        enum class Align { LEFT, CENTER, RIGHT }
        enum class Line { SOLID, DASHED, DOTTED }
    }

    data class Client(
        val id: Int,
        val channel: Int,
        val nickname: String,
        val uid: String = "",
        val type: Int = 0, // 1 = ServerQuery (not shown)
        val away: Boolean = false,
        val awayMessage: String = "",
        val inputMuted: Boolean = false,
        val outputMuted: Boolean = false,
        val inputHardware: Boolean = true,
        val outputHardware: Boolean = true,
        val talkPower: Int = 0,
        val isTalker: Boolean = false,
        val prioritySpeaker: Boolean = false,
        val talking: Boolean = false,
        val serverGroups: List<Int> = emptyList(),
        val channelGroup: Int = 0,
        val iconId: Long = 0,
        val avatar: String = "", // the avatar's hash ("" = none)
        val description: String = "",
        val country: String = "",
        val platform: String = "",
        val version: String = "",
    )

    /** A channel with its sub-channels and the people in it, in the server's order. */
    data class Node(val channel: Channel, val clients: List<Client>, val children: List<Node>)

    var serverName = ""
        private set
    var welcomeMessage = ""
        private set
    var ownClientId = 0
        private set

    val channels = LinkedHashMap<Int, Channel>()
    val clients = LinkedHashMap<Int, Client>()

    val ownClient: Client? get() = clients[ownClientId]
    val ownChannel: Channel? get() = ownClient?.let { channels[it.channel] }

    fun clear() {
        serverName = ""; welcomeMessage = ""; ownClientId = 0
        channels.clear(); clients.clear()
    }

    /** Returns true when something shown changed. */
    fun apply(cmd: Command): Boolean {
        when (cmd.name) {
            "initserver" -> {
                ownClientId = cmd["aclid"]?.toIntOrNull() ?: 0
                serverName = cmd["virtualserver_name"].orEmpty()
                welcomeMessage = cmd["virtualserver_welcomemessage"].orEmpty()
            }
            "notifyserverupdated" -> cmd["virtualserver_name"]?.let { serverName = it }
            "channellist", "notifychannelcreated" -> for (g in cmd.groups) {
                val id = g["cid"]?.toIntOrNull() ?: continue
                channels[id] = channelFrom(g, channels[id] ?: Channel(id, 0, 0, "Channel $id"))
            }
            "notifychanneledited" -> {
                val id = cmd["cid"]?.toIntOrNull() ?: return false
                val old = channels[id] ?: return false
                channels[id] = channelFrom(cmd.params, old)
            }
            "notifychannelmoved" -> {
                val id = cmd["cid"]?.toIntOrNull() ?: return false
                val old = channels[id] ?: return false
                channels[id] = old.copy(
                    parent = cmd["cpid"]?.toIntOrNull() ?: old.parent,
                    order = cmd["order"]?.toIntOrNull() ?: old.order,
                )
            }
            "notifychanneldeleted" -> for (g in cmd.groups) g["cid"]?.toIntOrNull()?.let { channels.remove(it) }
            "notifycliententerview" -> {
                // In a list, the target channel may be given only once, in the first entry.
                var target = 0
                for (g in cmd.groups) {
                    target = g["ctid"]?.toIntOrNull() ?: target
                    val id = g["clid"]?.toIntOrNull() ?: continue
                    clients[id] = clientFrom(g, Client(id, target, "Client $id")).copy(channel = target)
                }
            }
            "notifyclientleftview" -> for (g in cmd.groups) g["clid"]?.toIntOrNull()?.let { clients.remove(it) }
            "notifyclientmoved" -> {
                var target = 0
                for (g in cmd.groups) {
                    target = g["ctid"]?.toIntOrNull() ?: target
                    val id = g["clid"]?.toIntOrNull() ?: continue
                    clients[id]?.let { clients[id] = it.copy(channel = target, talking = false) }
                }
            }
            "notifyclientupdated" -> for (g in cmd.groups) {
                val id = g["clid"]?.toIntOrNull() ?: continue
                clients[id]?.let { clients[id] = clientFrom(g, it) }
            }
            "notifytalkstatuschange" -> {
                val id = cmd["clid"]?.toIntOrNull() ?: return false
                clients[id]?.let { clients[id] = it.copy(talking = cmd["status"] == "1") }
            }
            else -> return false
        }
        return true
    }

    /** Talking as seen from the voice itself (some servers send no talk status). */
    fun setTalking(clientId: Int, talking: Boolean): Boolean {
        val c = clients[clientId] ?: return false
        if (c.talking == talking) return false
        clients[clientId] = c.copy(talking = talking)
        return true
    }

    /** Whether [client] (by default we) can be heard in their channel. */
    fun canTalk(client: Client? = ownClient): Boolean {
        val c = client ?: return false
        val ch = channels[c.channel] ?: return true
        return c.isTalker || c.talkPower >= ch.neededTalkPower
    }

    fun tree(): List<Node> {
        val byParent = channels.values.groupBy { it.parent }
        val byChannel = clients.values.filter { it.type == 0 }.groupBy { it.channel }
        fun build(parent: Int, depth: Int): List<Node> {
            if (depth > 64) return emptyList()
            return ordered(byParent[parent].orEmpty()).map { ch ->
                Node(ch, byChannel[ch.id].orEmpty().sortedWith(clientOrder), build(ch.id, depth + 1))
            }
        }
        return build(0, 0)
    }

    private val clientOrder = compareByDescending<Client> { it.talkPower }.thenBy { it.nickname.lowercase() }

    /** Siblings follow each other: each names the one it comes after. */
    private fun ordered(siblings: List<Channel>): List<Channel> {
        val after = siblings.associateBy { it.order }
        val out = ArrayList<Channel>(siblings.size)
        val seen = HashSet<Int>()
        var cur = after[0]
        while (cur != null && seen.add(cur.id)) {
            out.add(cur)
            cur = after[cur.id]
        }
        siblings.filter { it.id !in seen }.sortedBy { it.id }.forEach { out.add(it) } // anything left over
        return out
    }

    private fun channelFrom(g: Map<String, String>, old: Channel) = old.copy(
        parent = g["cpid"]?.toIntOrNull() ?: g["pid"]?.toIntOrNull() ?: old.parent,
        order = g["channel_order"]?.toIntOrNull() ?: old.order,
        name = g["channel_name"] ?: old.name,
        topic = g["channel_topic"] ?: old.topic,
        isDefault = g["channel_flag_default"]?.let { it == "1" } ?: old.isDefault,
        hasPassword = g["channel_flag_password"]?.let { it == "1" } ?: old.hasPassword,
        codec = g["channel_codec"]?.toIntOrNull() ?: old.codec,
        neededTalkPower = g["channel_needed_talk_power"]?.toIntOrNull() ?: old.neededTalkPower,
        maxClients = g["channel_maxclients"]?.toIntOrNull() ?: old.maxClients,
        permanent = g["channel_flag_permanent"]?.let { it == "1" } ?: old.permanent,
        semiPermanent = g["channel_flag_semi_permanent"]?.let { it == "1" } ?: old.semiPermanent,
        iconId = g["channel_icon_id"]?.let { unsignedId(it) } ?: old.iconId,
        description = g["channel_description"] ?: old.description,
    )

    /** Icon ids come as signed or unsigned 32-bit numbers; always unsigned here. */
    private fun unsignedId(s: String): Long? = s.toLongOrNull()?.let { if (it < 0) it + (1L shl 32) else it }

    private fun clientFrom(g: Map<String, String>, old: Client) = old.copy(
        nickname = g["client_nickname"] ?: old.nickname,
        uid = g["client_unique_identifier"] ?: old.uid,
        type = g["client_type"]?.toIntOrNull() ?: old.type,
        away = g["client_away"]?.let { it == "1" } ?: old.away,
        awayMessage = g["client_away_message"] ?: old.awayMessage,
        inputMuted = g["client_input_muted"]?.let { it == "1" } ?: old.inputMuted,
        outputMuted = g["client_output_muted"]?.let { it == "1" } ?: old.outputMuted,
        inputHardware = g["client_input_hardware"]?.let { it == "1" } ?: old.inputHardware,
        outputHardware = g["client_output_hardware"]?.let { it == "1" } ?: old.outputHardware,
        talkPower = g["client_talk_power"]?.toIntOrNull() ?: old.talkPower,
        isTalker = g["client_is_talker"]?.let { it == "1" } ?: old.isTalker,
        prioritySpeaker = g["client_is_priority_speaker"]?.let { it == "1" } ?: old.prioritySpeaker,
        serverGroups = g["client_servergroups"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: old.serverGroups,
        channelGroup = g["client_channel_group_id"]?.toIntOrNull() ?: old.channelGroup,
        iconId = g["client_icon_id"]?.let { unsignedId(it) } ?: old.iconId,
        avatar = g["client_flag_avatar"] ?: old.avatar,
        description = g["client_description"] ?: old.description,
        country = g["client_country"] ?: old.country,
        platform = g["client_platform"] ?: old.platform,
        version = g["client_version"] ?: old.version,
    )
}
