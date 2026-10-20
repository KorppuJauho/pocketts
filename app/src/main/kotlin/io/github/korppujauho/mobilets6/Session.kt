package io.github.korppujauho.mobilets6

import android.content.Context
import io.github.korppujauho.mobilets6.proto.Command
import io.github.korppujauho.mobilets6.proto.Commands
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
        val typed = ServerAddress.parse(address) ?: run {
            _state.update { it.copy(error = "That doesn't look like a server address") }
            return
        }
        _state.value = UiState(phase = Phase.CONNECTING, address = address, inputMuted = _state.value.inputMuted, outputMuted = _state.value.outputMuted)
        ConnectionService.start(app, "Connecting to $address")
        worker.execute {
            val identity = Settings(app).identity() // made on first use, which takes a moment
            val target = ServerAddress.resolve(typed, AndroidDns) { AppLog.log(it) }
            AppLog.log("Connecting to ${target.host}:${target.port} (${target.how})")
            model.clear()
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
        val s = _state.value
        _state.value = UiState(phase = Phase.IDLE, error = reason, address = s.address, inputMuted = s.inputMuted, outputMuted = s.outputMuted)
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
                cannotTalk = model.clients.values.filter { c -> !model.canTalk(c) }.map { c -> c.id }.toSet(),
            )
        }
    }
}
