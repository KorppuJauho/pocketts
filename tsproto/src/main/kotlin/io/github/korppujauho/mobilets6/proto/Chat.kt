package io.github.korppujauho.mobilets6.proto

/**
 * The chat as WebTS and the TeamSpeak app keep it: a conversation per channel and one per person (private, kept by
 * their identity so a reconnect continues it), with join and leave lines in the channel's. Messages to the whole
 * server are left out, as the TeamSpeak 6 app has no server chat.
 *
 * [handle] must see each command before [ServerModel.apply] does: a move's line needs where the person was.
 */
class Chat(private val keep: Int = 300) {

    sealed interface Key {
        data class Channel(val id: Int) : Key
        data class Private(val uid: String) : Key
    }

    /** A line's pieces: plain text, or a name or channel shown bold. */
    data class Part(val text: String, val bold: Boolean = false)

    data class Entry(
        val id: Long,
        val time: Long,
        val senderId: Int = 0,
        val senderName: String = "",
        val senderUid: String = "",
        val text: String = "", // a message
        val event: List<Part>? = null, // or a join/leave line
        val outgoing: Boolean = false,
    )

    data class Poke(val id: Long, val time: Long, val fromId: Int, val fromName: String, val fromUid: String, val text: String)

    private var nextId = 1L
    private val conversations = LinkedHashMap<Key, ArrayDeque<Entry>>()
    /** Private conversations: their partner's latest name. */
    val privateNames = LinkedHashMap<String, String>()
    // Private messages we sent, oldest first, until the server echoes them back (it doesn't say to whom)
    private val sentPrivate = ArrayDeque<Pair<Int, String>>()

    var now: () -> Long = { System.currentTimeMillis() }

    fun entries(key: Key): List<Entry> = conversations[key]?.toList() ?: emptyList()
    fun keys(): Set<Key> = conversations.keys

    fun clear() {
        conversations.clear(); privateNames.clear(); sentPrivate.clear()
    }

    /** A private conversation's tab closed: its lines stay (in the history too), and come back when it opens again. */
    fun close(uid: String) {
        privateNames.remove(uid)
    }

    /** The lines kept from before ([ChatHistory]), oldest first and ahead of anything new; they get ids here. */
    fun restore(history: Map<out Key, List<Entry>>) {
        for ((key, lines) in history) {
            val list = conversations.getOrPut(key) { ArrayDeque() }
            list.addAll(0, lines.map { it.copy(id = nextId++, senderId = 0) }) // today's ids are someone else's
            while (list.size > keep) list.removeFirst()
        }
    }

    /** Forgets every line (the history was cleared); open private tabs stay, empty. */
    fun clearLines() {
        conversations.clear()
        for (uid in privateNames.keys) conversations[Key.Private(uid)] = ArrayDeque()
    }

    /** Opens (or keeps) a private conversation with someone. */
    fun open(uid: String, name: String) {
        privateNames[uid] = name
        conversations.getOrPut(Key.Private(uid)) { ArrayDeque() }
    }

    /** Before a private message goes out: whom it's for (the server's echo doesn't tell). */
    fun sendingPrivate(clientId: Int, uid: String) {
        sentPrivate.addLast(clientId to uid)
        while (sentPrivate.size > 20) sentPrivate.removeFirst()
    }

    private fun add(key: Key, entry: Entry): Key {
        val list = conversations.getOrPut(key) { ArrayDeque() }
        list.addLast(entry)
        while (list.size > keep) list.removeFirst()
        return key
    }

    private fun event(cid: Int, parts: List<Part>?): Key? =
        if (parts == null || cid <= 0) null else add(Key.Channel(cid), Entry(nextId++, now(), event = parts))

    /** The results of one command: where lines went, and a poke if it was one. */
    data class Result(val changed: Set<Key>, val poke: Poke? = null, val message: Pair<Key, Entry>? = null)

    fun handle(cmd: Command, model: ServerModel): Result {
        val me = model.ownClientId
        val here = model.ownClient?.channel ?: 0
        fun label(cid: Int) = channelLabel(model.channels[cid]?.name)
        val changed = LinkedHashSet<Key>()

        when (cmd.name) {
            "notifytextmessage" -> {
                val g = cmd.params
                val mode = g["targetmode"]?.toIntOrNull() ?: 2
                val from = g["invokerid"]?.toIntOrNull() ?: 0
                val fromName = g["invokername"] ?: "System"
                val fromUid = g["invokeruid"].orEmpty()
                val outgoing = from == me
                val key = when (mode) {
                    1 -> {
                        val uid = if (outgoing) {
                            val target = g["target"]?.toIntOrNull()
                            val sent = sentPrivate.removeFirstOrNull()
                            target?.let { model.clients[it]?.uid } ?: sent?.second
                        } else {
                            fromUid.ifEmpty { model.clients[from]?.uid.orEmpty() }
                        }
                        if (uid.isNullOrEmpty()) return Result(emptySet())
                        if (!outgoing) privateNames[uid] = fromName
                        else if (uid !in privateNames) privateNames[uid] = model.clients.values.firstOrNull { it.uid == uid }?.nickname ?: "Private chat"
                        Key.Private(uid)
                    }
                    3 -> return Result(emptySet()) // to the whole server: no such chat any more
                    else -> if (here > 0) Key.Channel(here) else return Result(emptySet())
                }
                val entry = Entry(nextId++, now(), from, fromName, fromUid, text = g["msg"].orEmpty(), outgoing = outgoing)
                add(key, entry)
                return Result(setOf(key), message = key to entry)
            }

            "notifyclientpoke" -> {
                val g = cmd.params
                return Result(
                    emptySet(),
                    poke = Poke(nextId++, now(), g["invokerid"]?.toIntOrNull() ?: 0, g["invokername"] ?: "Someone", g["invokeruid"].orEmpty(), g["msg"].orEmpty()),
                )
            }

            "notifycliententerview" -> {
                var target = 0
                var reason = 0
                for (g in cmd.groups) {
                    target = g["ctid"]?.toIntOrNull() ?: target
                    reason = g["reasonid"]?.toIntOrNull() ?: reason
                    val id = g["clid"]?.toIntOrNull() ?: continue
                    if (id == me || g["client_type"] == "1" || here <= 0 || target != here) continue
                    val cfid = g["cfid"]?.toIntOrNull() ?: 0
                    val who = g["client_nickname"] ?: "Client $id"
                    event(here, enterEvent(who, if (cfid > 0) label(cfid) else null))?.let { changed.add(it) }
                }
            }

            "notifyclientleftview" -> {
                var target = 0
                var reason = 0
                var invoker = ""
                var why = ""
                for (g in cmd.groups) {
                    target = g["ctid"]?.toIntOrNull() ?: target
                    reason = g["reasonid"]?.toIntOrNull() ?: reason
                    g["invokername"]?.let { invoker = it }
                    g["reasonmsg"]?.let { why = it }
                    val id = g["clid"]?.toIntOrNull() ?: continue
                    val gone = model.clients[id]
                    if (id == me) {
                        event(here, leaveEvent("", true, reason, invoker, why))?.let { changed.add(it) }
                    } else if (gone != null && gone.channel == here && gone.type == 0) {
                        val parts = if (target > 0 && reason in listOf(0, 1, 4)) {
                            moveEvent(Move(gone.nickname, self = false, intoHere = false, reason, label(gone.channel), label(target), invoker, why))
                        } else {
                            leaveEvent(gone.nickname, false, reason, invoker, why)
                        }
                        event(here, parts)?.let { changed.add(it) }
                    }
                }
            }

            "notifyclientmoved" -> {
                var target = 0
                var reason = 0
                var invoker = ""
                var why = ""
                for (g in cmd.groups) {
                    target = g["ctid"]?.toIntOrNull() ?: target
                    reason = g["reasonid"]?.toIntOrNull() ?: reason
                    g["invokername"]?.let { invoker = it }
                    g["reasonmsg"]?.let { why = it }
                    val id = g["clid"]?.toIntOrNull() ?: continue
                    val mover = model.clients[id] ?: continue
                    val m = Move(mover.nickname, false, false, reason, label(mover.channel), label(target), invoker, why)
                    if (id == me) {
                        // Our own move goes into the new channel's chat
                        event(target, moveEvent(m.copy(self = true)))?.let { changed.add(it) }
                        continue
                    }
                    val parts = when {
                        mover.type != 0 -> null
                        target == here -> moveEvent(m.copy(intoHere = true))
                        mover.channel == here -> moveEvent(m)
                        else -> null
                    }
                    event(here, parts)?.let { changed.add(it) }
                }
            }
        }
        return Result(changed)
    }

    data class Move(
        val who: String, val self: Boolean, val intoHere: Boolean, val reason: Int,
        val from: String, val to: String, val invoker: String = "", val reasonMsg: String = "",
    )

    companion object {
        private val SPACER = Regex("""^\[[^\]]*spacer[^\]]*](.*)$""", RegexOption.IGNORE_CASE)

        /** A channel's name as the tree shows it (a spacer's tag left out). */
        fun channelLabel(name: String?): String {
            val raw = name.orEmpty()
            val m = SPACER.matchEntire(raw)
            return (m?.groupValues?.get(1)?.trim() ?: raw).ifEmpty { raw }.ifEmpty { "another channel" }
        }

        private fun b(s: String) = Part(s, bold = true)
        private fun t(s: String) = Part(s)
        private fun by(invoker: String) = if (invoker.isNotEmpty()) listOf(t(" by "), b(invoker)) else emptyList()
        private fun why(msg: String) = if (msg.isNotEmpty()) listOf(t(" ($msg)")) else emptyList()

        /** Someone appeared in our channel: connected, or came over from a channel we don't see. */
        fun enterEvent(who: String, from: String?): List<Part> =
            if (from != null) listOf(b(who), t(" joined coming from "), b(from)) else listOf(b(who), t(" connected"))

        /** A move between channels; null when it needs no line (our own switch: we know). */
        fun moveEvent(m: Move): List<Part>? {
            if (m.self) {
                return when (m.reason) {
                    1 -> listOf(t("You were moved to "), b(m.to)) + by(m.invoker)
                    4 -> listOf(t("You were kicked from "), b(m.from)) + by(m.invoker) + why(m.reasonMsg)
                    else -> null
                }
            }
            val who = b(m.who)
            if (m.intoHere) {
                return when (m.reason) {
                    1 -> listOf(who, t(" was moved here from "), b(m.from)) + by(m.invoker)
                    4 -> listOf(who, t(" was kicked from "), b(m.from)) + by(m.invoker) + why(m.reasonMsg)
                    else -> listOf(who, t(" joined coming from "), b(m.from))
                }
            }
            return when (m.reason) {
                1 -> listOf(who, t(" was moved to "), b(m.to)) + by(m.invoker)
                4 -> listOf(who, t(" was kicked from the channel")) + by(m.invoker) + why(m.reasonMsg)
                else -> listOf(who, t(" left to "), b(m.to))
            }
        }

        /** Someone left the server (3 lost, 5 kicked, 6 banned, 8 left, 11 shut down); null when no line. */
        fun leaveEvent(who: String, self: Boolean, reason: Int, invoker: String = "", reasonMsg: String = ""): List<Part>? {
            if (self) {
                return when (reason) {
                    5 -> listOf(t("You were kicked from the server")) + by(invoker) + why(reasonMsg)
                    6 -> listOf(t("You were banned")) + by(invoker) + why(reasonMsg)
                    else -> null
                }
            }
            val w = b(who)
            return when (reason) {
                3 -> listOf(w, t(" lost connection"))
                5 -> listOf(w, t(" was kicked from the server")) + by(invoker) + why(reasonMsg)
                6 -> listOf(w, t(" was banned")) + by(invoker) + why(reasonMsg)
                11 -> null
                else -> listOf(w, t(" disconnected")) + why(reasonMsg)
            }
        }
    }
}
