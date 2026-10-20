package io.github.korppujauho.mobilets6

import android.content.Context
import io.github.korppujauho.mobilets6.proto.Chat
import io.github.korppujauho.mobilets6.proto.Command
import io.github.korppujauho.mobilets6.proto.Commands
import io.github.korppujauho.mobilets6.proto.MessageText
import io.github.korppujauho.mobilets6.proto.ServerAddress
import io.github.korppujauho.mobilets6.proto.ServerModel
import io.github.korppujauho.mobilets6.proto.TsClient
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The one connection the app has (or not), and what the screen shows about it. */
object Session : TsClient.Listener {

    enum class Phase { IDLE, CONNECTING, CONNECTED }

    /** A private conversation's tab: whose (by identity) and their name. */
    data class PrivateTab(val uid: String, val name: String)

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val error: String? = null,
        val address: String = "",
        val serverName: String = "",
        val tree: List<ServerModel.Node> = emptyList(),
        val ownId: Int = 0,
        val ownChannel: Int = 0,
        val talking: Set<Int> = emptySet(),
        val cannotTalk: Set<Int> = emptySet(), // people without the talk power for their channel
        val inputMuted: Boolean = false,
        val outputMuted: Boolean = false,
        val chat: Map<Chat.Key, List<Chat.Entry>> = emptyMap(),
        val privateTabs: List<PrivateTab> = emptyList(),
        val unread: Set<Chat.Key> = emptySet(),
        val pokes: List<Chat.Poke> = emptyList(),
        val server: Map<String, String> = emptyMap(), // virtualserver_* details
        val channelCount: Int = 0,
        val clientCount: Int = 0,
        val volumes: Map<String, Float> = emptyMap(), // per person (by identity), 1 = as is
    ) {
        val ownChannelName: String get() = findChannel(tree, ownChannel)?.displayName.orEmpty()

        fun client(id: Int): ServerModel.Client? = findClient(tree, id)
        fun clientByUid(uid: String): ServerModel.Client? = findClient(tree) { it.uid == uid && it.id != ownId }

        private companion object {
            fun findChannel(nodes: List<ServerModel.Node>, id: Int): ServerModel.Channel? {
                for (n in nodes) {
                    if (n.channel.id == id) return n.channel
                    findChannel(n.children, id)?.let { return it }
                }
                return null
            }

            fun findClient(nodes: List<ServerModel.Node>, id: Int) = findClient(nodes) { it.id == id }

            fun findClient(nodes: List<ServerModel.Node>, match: (ServerModel.Client) -> Boolean): ServerModel.Client? {
                for (n in nodes) {
                    n.clients.firstOrNull(match)?.let { return it }
                    findClient(n.children, match)?.let { return it }
                }
                return null
            }
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private lateinit var app: Context
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "session").apply { isDaemon = true } }
    private var client: TsClient? = null
    private var player: VoicePlayer? = null
    // Used on the connection's thread only
    private val model = ServerModel()
    private val chat = Chat()

    /** The conversation on screen (null: the chat isn't showing): it gets no unread mark. */
    @Volatile var viewing: Chat.Key? = null
        set(value) {
            field = value
            if (value != null) _state.update { it.copy(unread = it.unread - value) }
        }

    /** Whether the app is on screen; away, private messages and pokes also come as notifications. */
    @Volatile var foreground = false

    fun connect(context: Context, address: String, nickname: String, serverPassword: String) {
        app = context.applicationContext
        if (_state.value.phase != Phase.IDLE) return
        val typed = ServerAddress.parse(address) ?: run {
            _state.update { it.copy(error = "That doesn't look like a server address") }
            return
        }
        val prev = _state.value
        _state.value = UiState(
            phase = Phase.CONNECTING, address = address, inputMuted = prev.inputMuted, outputMuted = prev.outputMuted,
            volumes = Settings(app).volumes(),
        )
        ConnectionService.start(app, "Connecting to $address")
        worker.execute {
            val identity = Settings(app).identity() // made on first use, which takes a moment
            val target = ServerAddress.resolve(typed, AndroidDns) { AppLog.log(it) }
            AppLog.log("Connecting to ${target.host}:${target.port} (${target.how})")
            model.clear()
            chat.clear()
            player = VoicePlayer(::setTalking).also { it.muted = _state.value.outputMuted }
            client = TsClient(this) { AppLog.log(it) }.also {
                it.connect(
                    TsClient.Options(
                        host = target.host, port = target.port, identity = identity,
                        nickname = nickname.ifBlank { "MobileTS6 user" }, serverPassword = serverPassword,
                    )
                )
            }
        }
    }

    fun disconnect() {
        val c = client
        if (c == null) {
            finish(null)
            return
        }
        c.disconnect()
    }

    fun joinChannel(channelId: Int, password: String = "") {
        val c = client ?: return
        c.sendCommand(Commands.build("clientmove", "cid" to channelId, "clid" to c.clientId, "cpw" to password.ifEmpty { null }))
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Our microphone on/off, as the others see it. */
    fun toggleMic() {
        val muted = !_state.value.inputMuted
        _state.update { it.copy(inputMuted = muted) }
        client?.sendCommand(Commands.build("clientupdate", "client_input_muted" to muted))
    }

    /** Our sound on/off: nothing is played, and the others see it. */
    fun toggleSound() {
        val muted = !_state.value.outputMuted
        _state.update { it.copy(outputMuted = muted) }
        player?.muted = muted
        client?.sendCommand(Commands.build("clientupdate", "client_output_muted" to muted))
    }

    // ====== Chat ======

    /** Sends a message to a conversation: our channel, the server, or a person (who must be on the server). */
    fun send(key: Chat.Key, text: String) {
        val c = client ?: return
        val msg = text.trim().take(1024)
        if (msg.isEmpty()) return
        when (key) {
            is Chat.Key.Channel -> c.sendCommand(Commands.build("sendtextmessage", "targetmode" to 2, "target" to key.id, "msg" to msg))
            is Chat.Key.Server -> c.sendCommand(Commands.build("sendtextmessage", "targetmode" to 3, "target" to 0, "msg" to msg))
            is Chat.Key.Private -> c.post {
                val person = model.clients.values.firstOrNull { it.uid == key.uid && it.id != model.ownClientId } ?: run {
                    _state.update { it.copy(error = "${chat.privateNames[key.uid] ?: "They"} left the server") }
                    return@post
                }
                chat.sendingPrivate(person.id, key.uid)
                c.sendCommand(Commands.build("sendtextmessage", "targetmode" to 1, "target" to person.id, "msg" to msg))
            }
        }
    }

    /** Opens a private conversation with someone (their tab). */
    fun openPrivate(person: ServerModel.Client) {
        val c = client ?: return
        c.post {
            chat.open(person.uid, person.nickname)
            publishChat()
        }
    }

    fun closePrivate(uid: String) {
        client?.post {
            chat.close(uid)
            publishChat()
        } ?: _state.update { s -> s.copy(privateTabs = s.privateTabs.filter { it.uid != uid }) }
    }

    fun poke(person: ServerModel.Client, text: String) {
        client?.sendCommand(Commands.build("clientpoke", "clid" to person.id, "msg" to text.trim().take(100)))
    }

    fun dismissPoke(id: Long) = _state.update { s -> s.copy(pokes = s.pokes.filter { it.id != id }) }

    /** How loud someone is for us (0 to 2), kept by their identity. */
    fun setVolume(person: ServerModel.Client, volume: Float) {
        if (person.uid.isEmpty()) return
        val v = volume.coerceIn(0f, 2f)
        _state.update { it.copy(volumes = it.volumes + (person.uid to v)) }
        if (::app.isInitialized) Settings(app).setVolume(person.uid, v)
        updateGains()
    }

    // ====== From the connection (its own thread) ======

    override fun onConnected(clientId: Int) {
        AppLog.log("Connected as client $clientId")
        val s = _state.value
        if (s.inputMuted || s.outputMuted) {
            client?.sendCommand(Commands.build("clientupdate", "client_input_muted" to s.inputMuted, "client_output_muted" to s.outputMuted))
        }
        publish(Phase.CONNECTED)
        ConnectionService.start(app, "Connected to ${model.serverName.ifEmpty { _state.value.address }}")
    }

    override fun onCommand(cmd: Command) {
        when (cmd.name) {
            // See everyone on the server, not only our own channel; a new channel gets subscribed too.
            "channellistfinished" -> client?.sendCommand("channelsubscribeall")
            "notifychannelcreated" -> client?.sendCommand(Commands.build("channelsubscribe", "cid" to cmd["cid"]))
            "error" -> if (cmd["id"] != "0") {
                AppLog.log("Server error ${cmd["id"]}: ${cmd["msg"]}")
                if (_state.value.phase == Phase.CONNECTED) _state.update { it.copy(error = cmd["msg"]) }
            }
        }
        val result = chat.handle(cmd, model) // before the model: a move's line needs where they were
        val changed = model.apply(cmd)
        result.poke?.let { poked(it) }
        val message = result.message
        if (result.changed.isNotEmpty() || message != null) {
            val view = viewing
            // Unread: lines in a conversation not on screen (our own messages don't count)
            val fresh = result.changed.filter { it != view && !(message != null && message.second.outgoing && message.first == it) }
            if (fresh.isNotEmpty()) _state.update { it.copy(unread = it.unread + fresh) }
            if (message != null && !message.second.outgoing) messageArrived(message.first, message.second)
            publishChat()
        }
        if (changed) {
            publish(_state.value.phase)
            if (cmd.name.startsWith("notifycliententerview") || cmd.name == "notifyclientleftview") updateGains()
        }
    }

    override fun onVoice(voice: TsClient.Voice) {
        player?.onVoice(voice)
    }

    override fun onDisconnected(reason: String?) {
        AppLog.log("Disconnected" + (reason?.let { ": $it" } ?: ""))
        finish(reason)
    }

    private fun finish(reason: String?) {
        player?.release()
        player = null
        client = null
        val s = _state.value
        _state.value = UiState(
            phase = Phase.IDLE, error = reason, address = s.address, inputMuted = s.inputMuted, outputMuted = s.outputMuted,
            volumes = s.volumes,
        )
        if (::app.isInitialized) ConnectionService.stop(app)
    }

    private fun setTalking(clientId: Int, talking: Boolean) {
        _state.update { s -> s.copy(talking = if (talking) s.talking + clientId else s.talking - clientId) }
    }

    private fun poked(p: Chat.Poke) {
        _state.update { s -> s.copy(pokes = (s.pokes + p).takeLast(5)) }
        if (!foreground && ::app.isInitialized) {
            ConnectionService.notifyMessage(app, "${p.fromName} poked you", MessageText.plain(p.text))
        }
    }

    private fun messageArrived(key: Chat.Key, e: Chat.Entry) {
        if (foreground || !::app.isInitialized) return
        // Away from the app: private messages come as notifications (channel chat doesn't, like the desktop app)
        if (key is Chat.Key.Private) ConnectionService.notifyMessage(app, e.senderName, MessageText.plain(e.text))
    }

    private fun updateGains() {
        val volumes = _state.value.volumes
        val c = client ?: return
        c.post {
            player?.gains = model.clients.values.mapNotNull { p -> volumes[p.uid]?.let { p.id to it } }.toMap()
        }
    }

    private fun publishChat() {
        val keys = chat.keys()
        _state.update { s ->
            s.copy(
                chat = keys.associateWith { chat.entries(it) },
                privateTabs = chat.privateNames.map { (uid, name) -> PrivateTab(uid, name) },
            )
        }
    }

    private fun publish(phase: Phase) {
        val tree = model.tree()
        val me = model.ownClient
        _state.update {
            it.copy(
                phase = phase, serverName = model.serverName, tree = tree,
                ownId = model.ownClientId, ownChannel = me?.channel ?: 0,
                cannotTalk = model.clients.values.filter { c -> !model.canTalk(c) }.map { c -> c.id }.toSet(),
                server = HashMap(model.serverVars),
                channelCount = model.channels.size,
                clientCount = model.clients.values.count { c -> c.type == 0 },
            )
        }
    }
}
