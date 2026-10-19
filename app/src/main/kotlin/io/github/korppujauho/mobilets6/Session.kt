package io.github.korppujauho.mobilets6

import android.content.Context
import io.github.korppujauho.mobilets6.proto.Command
import io.github.korppujauho.mobilets6.proto.Commands
import io.github.korppujauho.mobilets6.proto.ServerModel
import io.github.korppujauho.mobilets6.proto.TsClient
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The one connection the app has (or not), and what the screen shows about it. */
object Session : TsClient.Listener {

    enum class Phase { IDLE, CONNECTING, CONNECTED }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val error: String? = null,
        val address: String = "",
        val serverName: String = "",
        val tree: List<ServerModel.Node> = emptyList(),
        val ownId: Int = 0,
        val ownChannel: Int = 0,
        val talking: Set<Int> = emptySet(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private lateinit var app: Context
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "session").apply { isDaemon = true } }
    private var client: TsClient? = null
    private var player: VoicePlayer? = null
    private val model = ServerModel() // used on the connection's thread only

    fun connect(context: Context, address: String, nickname: String, serverPassword: String) {
        app = context.applicationContext
        if (_state.value.phase != Phase.IDLE) return
        val target = Settings.parseAddress(address) ?: run {
            _state.update { it.copy(error = "That doesn't look like a server address") }
            return
        }
        _state.value = UiState(phase = Phase.CONNECTING, address = address)
        ConnectionService.start(app, "Connecting to $address")
        worker.execute {
            val identity = Settings(app).identity() // made on first use, which takes a moment
            AppLog.log("Connecting to ${target.first}:${target.second}")
            model.clear()
            player = VoicePlayer(::setTalking)
            client = TsClient(this) { AppLog.log(it) }.also {
                it.connect(
                    TsClient.Options(
                        host = target.first, port = target.second, identity = identity,
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

    // ====== From the connection (its own thread) ======

    override fun onConnected(clientId: Int) {
        AppLog.log("Connected as client $clientId")
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
        if (model.apply(cmd)) publish(_state.value.phase)
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
        _state.value = UiState(phase = Phase.IDLE, error = reason, address = _state.value.address)
        if (::app.isInitialized) ConnectionService.stop(app)
    }

    private fun setTalking(clientId: Int, talking: Boolean) {
        _state.update { s -> s.copy(talking = if (talking) s.talking + clientId else s.talking - clientId) }
    }

    private fun publish(phase: Phase) {
        val tree = model.tree()
        val me = model.ownClient
        _state.update {
            it.copy(
                phase = phase, serverName = model.serverName, tree = tree,
                ownId = model.ownClientId, ownChannel = me?.channel ?: 0,
            )
        }
    }
}
