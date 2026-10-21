package io.github.korppujauho.pocketts

import android.content.Context
import io.github.korppujauho.pocketts.proto.Chat
import io.github.korppujauho.pocketts.proto.Command
import io.github.korppujauho.pocketts.proto.Commands
import io.github.korppujauho.pocketts.proto.Crypto
import io.github.korppujauho.pocketts.proto.FileTransfer
import io.github.korppujauho.pocketts.proto.MessageText
import io.github.korppujauho.pocketts.proto.ServerAddress
import io.github.korppujauho.pocketts.proto.ServerModel
import io.github.korppujauho.pocketts.proto.TsClient
import io.github.korppujauho.pocketts.UiSounds.Cue
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The one connection the app has (or not), and what the screen shows about it. */
object Session : TsClient.Listener {
    private const val WRONG_CHANNEL_PASSWORD = "781" // "invalid channel password"
    private const val AVATAR_TOO_BIG = 2565 // over the server's max avatar file size


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
        val voice: VoiceSettings = VoiceSettings(),
        val micAllowed: Boolean = false, // the microphone permission
        val pttHeld: Boolean = false,
        val badges: Map<Int, List<Long>> = emptyMap(), // per person: their group and own icons
        val serverIcon: Long = 0,
        val sendingPicture: Boolean = false,
        val retryPassword: Int = 0, // a channel whose password was just refused: its password is asked again
        val retryRemember: Boolean = false, // ... and whether that one was to be remembered
        val avatarStatus: String? = null, // the profile picture's last change, as words
        val reconnecting: Int = 0, // the try, while getting a lost connection back (0 = not)
        val stream: StreamEngine.State = StreamEngine.State(),
        val streams: Map<Int, String> = emptyMap(), // running streams: whose and their id
        val away: Boolean = false,
        val awayMessage: String = "",
    ) {
        /** Files on this server (for pictures). */
        val serverKey: String get() = address
        val ownChannelName: String get() = findChannel(tree, ownChannel)?.displayName.orEmpty()

        fun client(id: Int): ServerModel.Client? = findClient(tree, id)
        fun channel(id: Int): ServerModel.Channel? = findChannel(tree, id)
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
    @Volatile private var files: FileTransfer? = null
    private var engine: StreamEngine? = null

    /** The stream engine (made once, kept across connections: WebRTC is slow to start). */
    fun streams(): StreamEngine {
        engine?.let { return it }
        return StreamEngine(
            app,
            sendNow = { client?.sendCommand(it) },
            soundPaused = { streamSoundPaused() },
            settings = { _state.value.voice },
            onState = { st ->
                val before = _state.value.stream
                _state.update { it.copy(stream = st) }
                if (before.sharing != st.sharing) refreshService()
            },
        ).also { engine = it }
    }

    /** The service's kinds follow what we do: the microphone, and the camera or the screen while shared. */
    private fun refreshService() {
        if (!::app.isInitialized || _state.value.phase == Phase.IDLE) return
        val kind = _state.value.stream.sharing
        ConnectionService.start(
            app, "Connected to ${_state.value.serverName.ifEmpty { _state.value.address }}",
            camera = kind == io.github.korppujauho.pocketts.proto.Streams.Kind.CAMERA,
            projection = kind == io.github.korppujauho.pocketts.proto.Streams.Kind.SCREEN,
        )
    }

    fun watch(person: ServerModel.Client) = streams().watch(person, _state.value.streams[person.id])
    fun stopWatching() = engine?.stopWatching()
    fun stopShare() = engine?.stopShare()
    fun flipCamera() = engine?.flipCamera()
    fun setWatchMuted(muted: Boolean) = engine?.setWatchMuted(muted)
    fun setWatchVolume(volume: Float) = engine?.setWatchVolume(volume)

    /** The warning on screen was closed (or its time ran out). */
    fun clearError() {
        _state.update { it.copy(error = null) }
        engine?.clearMessage()
    }

    /** The camera's turn locked or following the phone; remembered for the next share. */
    fun setRotateLock(on: Boolean) {
        updateVoice(_state.value.voice.copy(cameraRotateLock = on))
        engine?.setRotateLock(on)
    }

    fun shareCamera(front: Boolean, quality: Int, sound: Boolean) {
        val name = _state.value.client(_state.value.ownId)?.nickname ?: "Camera"
        if (_state.value.voice.cameraSound != sound) updateVoice(_state.value.voice.copy(cameraSound = sound))
        // The service says "camera" before the camera opens (Android's rule for using it in the background)
        ConnectionService.start(app, "Sharing your camera", camera = true)
        streams().shareCamera(front, quality, name, sound && micPermitted())
    }

    fun shareBothCameras(quality: Int, sound: Boolean) {
        val name = _state.value.client(_state.value.ownId)?.nickname ?: "Camera"
        if (_state.value.voice.cameraSound != sound) updateVoice(_state.value.voice.copy(cameraSound = sound))
        ConnectionService.start(app, "Sharing your cameras", camera = true)
        streams().shareBothCameras(quality, name, sound && micPermitted())
    }

    // The camera stream's sound is silent while the microphone is muted or we talk in the channel (else viewers
    // would hear us twice, a moment apart)
    private fun streamSoundPaused(): Boolean {
        val s = _state.value
        return s.inputMuted || !s.micAllowed || s.ownId in s.talking
    }

    /** Screen sharing, with Android's permission ([projection]); the service must say "screen" before it starts. */
    fun shareScreen(projection: android.content.Intent, width: Int, height: Int) {
        val name = _state.value.client(_state.value.ownId)?.nickname ?: "Screen"
        ConnectionService.onForeground = { streams().shareScreen(projection, width, height, name) }
        ConnectionService.start(app, "Sharing your screen", projection = true)
    }
    private var player: VoicePlayer? = null
    private var sender: MicSender? = null
    private var route: AudioRoute? = null
    @Volatile private var ownCodec = 4 // our channel's (Opus Voice 4 or Opus Music 5)
    // Used on the connection's thread only
    private val model = ServerModel()
    private val chat = Chat()

    // The chat history: the server whose history is loaded (null while none is), and its writes, one at a time
    private val historyLock = Any()
    private var historyServer: String? = null
    private var historySave: java.util.concurrent.ScheduledFuture<*>? = null
    private val historyWriter = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "chat-history").apply { isDaemon = true } }
    private val history by lazy { ChatHistoryStore(java.io.File(app.noBackupFilesDir, "chat")) }

    /** The conversation on screen (null: the chat isn't showing): it gets no unread mark. */
    @Volatile var viewing: Chat.Key? = null
        set(value) {
            field = value
            if (value != null) _state.update { it.copy(unread = it.unread - value) }
        }

    /** The saved settings, for the screens before any connection. */
    fun load(context: Context) {
        app = context.applicationContext
        if (_state.value.phase == Phase.IDLE) _state.update { it.copy(voice = VoiceSettings.load(app), micAllowed = micPermitted()) }
    }

    /** The favourite marked to connect when the app opens has been tried (once per app start). */
    @Volatile var autoConnected = false

    /** Leaves this server and then runs [next] (connecting to another). */
    fun switchTo(next: () -> Unit) {
        if (_state.value.phase == Phase.IDLE) {
            next()
            return
        }
        pendingSwitch = next
        disconnect()
    }

    @Volatile private var pendingSwitch: (() -> Unit)? = null

    /** Whether the app is on screen; away, private messages and pokes also come as notifications. */
    @Volatile var foreground = false
        set(value) {
            field = value
            TalkOverlay.refresh()
            if (!value) flushHistory()
        }

    /** The app is only the small stream window (picture-in-picture). */
    @Volatile var pip = false
        set(value) {
            field = value
            TalkOverlay.refresh()
        }

    /** What the last connect asked for, to get a lost connection back. */
    private data class Target(val address: String, val nickname: String, val password: String, val filePort: Int? = null)
    private var target: Target? = null
    private var retries = 0
    @Volatile private var wasConnected = false // only a connection that worked is tried again
    private val retryTimer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "reconnect").apply { isDaemon = true } }
    private var retryTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var network: android.net.ConnectivityManager.NetworkCallback? = null

    fun connect(context: Context, address: String, nickname: String, serverPassword: String, filePort: Int? = null) {
        app = context.applicationContext
        if (_state.value.phase != Phase.IDLE) return
        retries = 0
        wasConnected = false
        target = Target(address, nickname, serverPassword, filePort)
        start(keepChat = false)
    }

    /** Connects to [target]; a reconnect keeps the chat and the pokes. */
    private fun start(keepChat: Boolean) {
        val (address, nickname, serverPassword, filePort) = target ?: return
        val typed = ServerAddress.parse(address) ?: run {
            _state.update { it.copy(error = "That doesn't look like a server address") }
            return
        }
        val prev = _state.value
        if (!keepChat) synchronized(historyLock) { historyServer = null }
        _state.value = UiState(
            phase = Phase.CONNECTING, address = address, inputMuted = prev.inputMuted, outputMuted = prev.outputMuted,
            volumes = Settings(app).volumes(), voice = VoiceSettings.load(app), micAllowed = micPermitted(),
            reconnecting = if (keepChat) retries else 0,
            chat = if (keepChat) prev.chat else emptyMap(), privateTabs = if (keepChat) prev.privateTabs else emptyList(),
            pokes = if (keepChat) prev.pokes else emptyList(), away = prev.away && keepChat, awayMessage = prev.awayMessage,
        )
        watchNetwork()
        ConnectionService.start(app, "Connecting to $address")
        worker.execute {
            val identity = try {
                Settings(app).identity() // made on first use, which takes a moment
            } catch (e: Settings.IdentityUnreadable) {
                finish(e.message)
                return@execute
            }
            val target = ServerAddress.resolve(typed, AndroidDns) { AppLog.log(it) }
            AppLog.log("Connecting to ${target.host}:${target.port} (${target.how})")
            model.clear()
            if (!keepChat) {
                chat.clear()
                restoreHistory(address)
            }
            startAudio()
            client = TsClient(this) { AppLog.log(it) }.also {
                files = FileTransfer(it, portOverride = filePort)
                it.connect(
                    TsClient.Options(
                        host = target.host, port = target.port, identity = identity,
                        nickname = nickname.ifBlank { "PocketTS user" }, serverPassword = serverPassword,
                    )
                )
            }
        }
    }

    fun disconnect() {
        target = null // we leave: no reconnecting
        retryTask?.cancel(false)
        if (_state.value.reconnecting > 0 && client == null) {
            finish(null)
            return
        }
        val c = client
        if (c == null) {
            finish(null)
            return
        }
        c.disconnect()
    }

    // The last join asked for, until the server answers it (by its return code)
    private class PendingJoin(val channel: Int, val password: String, val remember: Boolean, val code: String)
    @Volatile private var pendingJoin: PendingJoin? = null
    private var joins = 0

    /**
     * Joins a channel; with [remember], its [password] is saved (encrypted) once the server lets us in. A [quiet] one
     * shows no warning when refused: it's the try without a password, which works for those allowed to skip
     * channel passwords (a refusal then asks for the password).
     */
    fun joinChannel(channelId: Int, password: String = "", remember: Boolean = false, quiet: Boolean = false) {
        val c = client ?: return
        val code = (if (quiet) "quiet-join-" else "join-") + (++joins)
        pendingJoin = PendingJoin(channelId, password, remember, code)
        c.sendCommand(
            Commands.build(
                "clientmove", "cid" to channelId, "clid" to c.clientId, "cpw" to Crypto.hashPassword(password).ifEmpty { null },
                "return_code" to code,
            )
        )
    }

    /** The password remembered for a channel on this server, if any. */
    fun savedChannelPassword(channelId: Int): String? =
        if (::app.isInitialized) ChannelPasswords(app).get(_state.value.address, channelId) else null

    /** The refused channel's password is being asked again. */
    fun passwordAsked() = _state.update { it.copy(retryPassword = 0) }

    fun forgetChannelPasswords() {
        if (::app.isInitialized) ChannelPasswords(app).clear()
    }

    // The server's answer to a join: a password that worked is kept (when asked to); a refused one is forgotten and
    // asked again, so a mistyped or changed password gets replaced
    private fun joinAnswered(join: PendingJoin, cmd: Command) {
        if (!::app.isInitialized) return
        val saved = ChannelPasswords(app)
        val address = _state.value.address
        val id = cmd["id"]
        when {
            id == "0" -> when {
                join.password.isEmpty() -> {}
                join.remember -> saved.put(address, join.channel, join.password)
                else -> saved.remove(address, join.channel) // remembering was turned off
            }
            id == WRONG_CHANNEL_PASSWORD || cmd["msg"].orEmpty().contains("password", ignoreCase = true) -> {
                saved.remove(address, join.channel)
                _state.update { it.copy(retryPassword = join.channel, retryRemember = join.remember) }
            }
            // The quiet try's other refusals (a full channel, no permission) are shown as any other
            join.code.startsWith("quiet-") -> {
                AppLog.log("Server error $id: ${cmd["msg"]}")
                _state.update { it.copy(error = cmd["msg"]) }
            }
        }
    }

    /** Our microphone on/off, as the others see it. */
    fun toggleMic() {
        val muted = !_state.value.inputMuted
        _state.update { it.copy(inputMuted = muted) }
        UiSounds.play(if (muted) Cue.MIC_OFF else Cue.MIC_ON, _state.value.voice, _state.value.outputMuted)
        sender?.muted = muted
        engine?.refreshSound()
        client?.sendCommand(Commands.build("clientupdate", "client_input_muted" to muted))
    }

    // ====== Talking ======

    private fun micPermitted() = ::app.isInitialized &&
        app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** The microphone permission was answered. */
    fun micPermission(context: Context, granted: Boolean) {
        app = context.applicationContext
        _state.update { it.copy(micAllowed = granted) }
        if (granted && _state.value.phase != Phase.IDLE) {
            // Now the service may use the microphone in the background too
            ConnectionService.start(app, "Connected to ${_state.value.serverName.ifEmpty { _state.value.address }}")
            if (sender == null && player != null) startSender()
        }
    }

    /** A new nickname, now (also kept for next time by the caller). */
    fun setNickname(name: String) {
        client?.sendCommand(Commands.build("clientupdate", "client_nickname" to name))
    }

    /** Push-to-talk held or let go (the button, or a volume button). */
    fun setPtt(held: Boolean) {
        sender?.pttHeld = held
        if (_state.value.pttHeld != held) _state.update { it.copy(pttHeld = held) }
    }

    /** The microphone's level and whether it's "on", for the settings' meter. */
    fun micLevel(): Pair<Float, Boolean> = sender?.let { it.level to it.active } ?: (0f to false)

    /** Measuring only (settings open): nothing is sent. */
    fun micTest(on: Boolean) {
        sender?.testing = on
    }

    fun updateVoice(v: VoiceSettings) {
        val old = _state.value.voice
        v.save(app)
        _state.update { it.copy(voice = v) }
        player?.voiceGain = v.voiceGain
        if (v.activation != VoiceSettings.Activation.PTT) setPtt(false)
        if (_state.value.phase == Phase.IDLE) return
        if (v.echoCancellation != old.echoCancellation) {
            // Call audio or media: playback and the microphone start again
            worker.execute { stopAudio(); startAudio() }
        } else if (v.noiseSuppression != old.noiseSuppression || v.autoGainControl != old.autoGainControl) {
            sender?.reconfigure()
        }
    }

    private fun startAudio() {
        val v = _state.value.voice
        if (v.echoCancellation) route = AudioRoute(app).also { it.start() }
        player = VoicePlayer(v.echoCancellation, ::setTalking).also {
            it.muted = _state.value.outputMuted
            it.voiceGain = v.voiceGain
        }
        updateGains()
        if (micPermitted()) startSender()
    }

    private fun startSender() {
        sender = MicSender(
            settings = { _state.value.voice },
            canSend = { _state.value.phase == Phase.CONNECTED && _state.value.ownId !in _state.value.cannotTalk },
            send = { client?.sendVoice(ownCodec, it) },
            sendStop = { client?.sendVoiceStop(ownCodec) },
            onTalking = { talking -> _state.value.ownId.takeIf { it > 0 }?.let { setTalking(it, talking) } },
        ).also { it.muted = _state.value.inputMuted }
    }

    private fun stopAudio() {
        sender?.release()
        sender = null
        player?.release()
        player = null
        route?.stop()
        route = null
    }

    /** Our sound on/off: nothing is played, and the others see it. */
    fun toggleSound() {
        val muted = !_state.value.outputMuted
        _state.update { it.copy(outputMuted = muted) }
        UiSounds.play(if (muted) Cue.SOUND_OFF else Cue.SOUND_ON, _state.value.voice, muted)
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

    // ====== Chat history ======

    /** The server's chat from before, into the chat (before the connection starts, on the worker). */
    private fun restoreHistory(address: String) {
        val server = io.github.korppujauho.pocketts.proto.ChatHistory.serverKey(address) ?: return
        val saved = history.load(server, System.currentTimeMillis())
        if (saved.isNotEmpty()) {
            chat.restore(saved)
            publishChat()
            AppLog.log("Chat history: ${saved.values.sumOf { it.size }} lines from before")
        }
        synchronized(historyLock) { historyServer = server }
    }

    /** New lines are written together, at most every few seconds (and when the connection ends). */
    private fun saveHistorySoon(delaySeconds: Long = 10) {
        synchronized(historyLock) {
            if (historyServer == null || historySave?.isDone == false) return
            historySave = historyWriter.schedule({ saveHistory() }, delaySeconds, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private fun saveHistory() {
        val (server, lines) = synchronized(historyLock) { (historyServer ?: return) to _state.value.chat }
        history.save(server, lines, System.currentTimeMillis())
    }

    /** Now, and no more for this server (the connection ended). Before the chat on screen is emptied. */
    private fun closeHistory() {
        synchronized(historyLock) {
            val server = historyServer ?: return
            val lines = _state.value.chat
            historyServer = null
            historySave?.cancel(false)
            historyWriter.execute { history.save(server, lines, System.currentTimeMillis()) }
        }
    }

    /** Settings › Clear chat history: every server's, and the lines on screen now. */
    fun clearChatHistory(context: Context) {
        app = context.applicationContext
        val c = client
        if (c != null) {
            c.post {
                chat.clearLines()
                publishChat()
                historyWriter.execute { history.clear() } // after any write of the lines just cleared
            }
        } else {
            // Between connections, or waiting to reconnect: the chat is the worker's then
            worker.execute {
                chat.clearLines()
                publishChat()
                historyWriter.execute { history.clear() }
            }
        }
        AppLog.log("Chat history cleared")
    }

    /** Leaving the app: lines waiting to be written go now (Android may stop the app later). */
    private fun flushHistory() {
        synchronized(historyLock) {
            val pending = historySave ?: return
            if (pending.isDone || !pending.cancel(false)) return
            historySave = historyWriter.schedule({ saveHistory() }, 0, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

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
        retries = 0
        wasConnected = true
        if (s.reconnecting > 0) _state.update { it.copy(reconnecting = 0) }
        if (s.away) client?.sendCommand(Commands.build("clientupdate", "client_away" to true, "client_away_message" to s.awayMessage))
        if (s.inputMuted || s.outputMuted) {
            client?.sendCommand(Commands.build("clientupdate", "client_input_muted" to s.inputMuted, "client_output_muted" to s.outputMuted))
        }
        // The groups (for their icons) and the rest of the server's info; refused ones just stay out
        for ((i, cmd) in listOf("servergrouplist", "channelgrouplist", "servergetvariables").withIndex()) {
            client?.sendCommand(Commands.build(cmd, "return_code" to "quiet-$i"))
        }
        publish(Phase.CONNECTED)
        ConnectionService.start(app, "Connected to ${model.serverName.ifEmpty { _state.value.address }}")
        kicked = false
        soundsFrom = System.currentTimeMillis() + 3000
        UiSounds.play(Cue.CONNECTED, s.voice, s.outputMuted)
    }

    // ====== Sounds for events ======

    // Quiet for the first seconds after connecting, while the server lists who is already there (as in WebTS)
    @Volatile private var soundsFrom = 0L
    @Volatile private var kicked = false

    private fun sound(cue: Cue) {
        if (System.currentTimeMillis() < soundsFrom) return
        UiSounds.play(cue, _state.value.voice, _state.value.outputMuted)
    }

    /** The sounds a command brings: read before the model takes it in (it still knows where everyone was). */
    private fun eventCues(cmd: Command): List<Cue> {
        val me = model.ownClientId
        val here = model.ownClient?.channel ?: 0
        val out = ArrayList<Cue>()
        var reason = 0
        fun person(id: Int) = model.clients[id]?.takeIf { it.type == 0 }
        when (cmd.name) {
            "notifyclientmoved" -> for (g in cmd.groups) {
                g["reasonid"]?.toIntOrNull()?.let { reason = it }
                val clid = g["clid"]?.toIntOrNull() ?: continue
                val to = g["ctid"]?.toIntOrNull() ?: continue
                if (clid == me) {
                    // Switched yourself, moved by someone, or kicked from the channel
                    out += when (reason) { 1 -> Cue.MOVED; 4 -> Cue.KICKED; else -> Cue.CHANNEL_SWITCHED }
                } else {
                    val p = person(clid) ?: continue
                    if (to == here && here > 0) out += Cue.SOMEONE_JOINED
                    else if (p.channel == here) out += Cue.SOMEONE_LEFT
                }
            }
            // Connected into your channel, or came from a channel you don't see
            "notifycliententerview" -> for (g in cmd.groups) {
                val clid = g["clid"]?.toIntOrNull() ?: continue
                if (clid == me || g["client_type"] == "1") continue
                if (g["ctid"]?.toIntOrNull() == here && here > 0) out += Cue.SOMEONE_JOINED
            }
            // Left the server from your channel; or you were kicked or banned
            "notifyclientleftview" -> for (g in cmd.groups) {
                g["reasonid"]?.toIntOrNull()?.let { reason = it }
                val clid = g["clid"]?.toIntOrNull() ?: continue
                if (clid == me) {
                    if (reason == 5 || reason == 6) { kicked = true; out += Cue.KICKED }
                } else if (person(clid)?.channel == here) out += Cue.SOMEONE_LEFT
            }
            // A stream in your channel (yours too) starts or stops
            "notifystreamstarted", "notifystreamstopped" -> {
                val g = cmd.params
                val id = g["id"] ?: g["stream_id"]
                val clid = g["clid"]?.toIntOrNull()?.takeIf { it > 0 } ?: model.streams.entries.firstOrNull { it.value == id }?.key ?: 0
                if (clid > 0 && (clid == me || model.clients[clid]?.channel == here)) {
                    out += if (cmd.name == "notifystreamstarted") Cue.STREAM_STARTED else Cue.STREAM_STOPPED
                }
            }
        }
        return out.distinct()
    }

    // ====== Files: pictures, avatars, icons ======

    /** A file from the server's files (blocking; for pictures on screen). */
    fun downloadFile(server: String, cid: Int, path: String, maxBytes: Long): ByteArray {
        val f = files
        if (f == null || server != _state.value.serverKey || _state.value.phase != Phase.CONNECTED) throw IllegalStateException("not connected")
        return f.download(cid, path, maxBytes)
    }

    /**
     * Sends a picture into our channel's chat: into the channel's files (/PocketTS), then one message with the picture and
     * [text] (if any) under it, as the TeamSpeak app sends them.
     */
    fun sendPicture(context: Context, uri: android.net.Uri, text: String = "") {
        val c = client ?: return
        val f = files ?: return
        val cid = _state.value.ownChannel
        _state.update { it.copy(sendingPicture = true) }
        worker.execute {
            try {
                val p = ServerFiles.prepareChatPicture(context, uri)
                val name = io.github.korppujauho.pocketts.proto.Pictures.pictureName(p.type, java.util.UUID.randomUUID().toString().take(6))
                f.createDirectory(cid, io.github.korppujauho.pocketts.proto.Pictures.PICTURE_FOLDER)
                f.upload(cid, "${io.github.korppujauho.pocketts.proto.Pictures.PICTURE_FOLDER}/$name", p.bytes)
                val picture = io.github.korppujauho.pocketts.proto.Pictures.pictureMessage(name, cid)
                val words = text.trim().take(1000 - picture.length)
                val msg = if (words.isEmpty()) picture else "$picture\n$words"
                c.sendCommand(Commands.build("sendtextmessage", "targetmode" to 2, "target" to cid, "msg" to msg))
                AppLog.log("Sent a picture (${p.bytes.size / 1024} KB)")
            } catch (e: FileTransfer.Error) {
                _state.update { it.copy(error = "The picture wasn't sent: ${e.message}" + if (e.code == 2568) " (no permission in this channel)" else "") }
            } catch (e: Exception) {
                _state.update { it.copy(error = "The picture wasn't sent: ${e.message}") }
            } finally {
                _state.update { it.copy(sendingPicture = false) }
            }
        }
    }

    /** A new profile picture: uploaded as the server's /avatar, then everyone is told (its MD5). */
    fun setAvatar(context: Context, uri: android.net.Uri) {
        val c = client ?: return
        val f = files ?: return
        _state.update { it.copy(avatarStatus = "Uploading…") }
        worker.execute {
            try {
                // Over the server's size limit, the next smaller one is tried (as the TeamSpeak app asks for a smaller one)
                val versions = ServerFiles.prepareAvatar(context, uri)
                var sent: ByteArray? = null
                for (data in versions) {
                    try {
                        f.upload(0, io.github.korppujauho.pocketts.proto.Pictures.AVATAR_UPLOAD_NAME, data, overwrite = true)
                        sent = data
                        break
                    } catch (e: FileTransfer.Error) {
                        if (e.code != AVATAR_TOO_BIG || data === versions.last()) throw e
                        AppLog.log("Profile picture of ${data.size / 1024} KB too big for the server, trying a smaller one")
                    }
                }
                val data = sent ?: return@execute
                c.sendCommand(Commands.build("clientupdate", "client_flag_avatar" to io.github.korppujauho.pocketts.proto.Pictures.avatarFlag(data)))
                AppLog.log("Profile picture set (${data.size / 1024} KB)")
                _state.update { it.copy(avatarStatus = "Profile picture set.") }
            } catch (e: FileTransfer.Error) {
                _state.update {
                    it.copy(avatarStatus = when (e.code) {
                        AVATAR_TOO_BIG -> "This server allows only a very small profile picture, or none."
                        2568 -> "This server doesn't allow you a profile picture."
                        else -> "The profile picture wasn't set: ${e.message}"
                    })
                }
            } catch (e: Exception) {
                _state.update { it.copy(avatarStatus = "The profile picture wasn't set: ${e.message}") }
            }
        }
    }

    /** No profile picture: the flag is cleared, so nobody shows it (the file itself can't be deleted). */
    fun removeAvatar() {
        client?.sendCommand(Commands.build("clientupdate", "client_flag_avatar" to ""))
        _state.update { it.copy(avatarStatus = "Profile picture removed.") }
    }

    override fun onCommand(cmd: Command) {
        when (cmd.name) {
            // See everyone on the server, not only our own channel; a new channel gets subscribed too.
            "channellistfinished" -> client?.sendCommand("channelsubscribeall")
            "notifychannelcreated" -> client?.sendCommand(Commands.build("channelsubscribe", "cid" to cmd["cid"]))
            "error" -> {
                pendingJoin?.takeIf { it.code == cmd["return_code"] }?.let {
                    pendingJoin = null
                    joinAnswered(it, cmd)
                }
                if (cmd["id"] != "0" && cmd["return_code"]?.let { it.startsWith("ft-") || it.startsWith("quiet-") } != true) {
                    AppLog.log("Server error ${cmd["id"]}: ${cmd["msg"]}")
                    if (_state.value.phase == Phase.CONNECTED) _state.update { it.copy(error = cmd["msg"]) }
                }
            }
        }
        files?.onCommand(cmd)
        engine?.onCommand(cmd, model.ownClientId, { _state.value.ownChannel }, { id -> _state.value.client(id)?.channel })
        val cues = eventCues(cmd)
        val result = chat.handle(cmd, model) // before the model: a move's line needs where they were
        val changed = model.apply(cmd)
        result.poke?.let { poked(it) }
        val message = result.message
        cues.forEach { sound(it) }
        if (result.poke != null) sound(Cue.POKE)
        if (message != null) sound(if (message.second.outgoing) Cue.MESSAGE_OUT else Cue.MESSAGE_IN)
        if (result.changed.isNotEmpty() || message != null) {
            val view = viewing
            // Unread: a message in a conversation not on screen (not our own, not join and leave lines)
            if (message != null && !message.second.outgoing && message.first != view) _state.update { it.copy(unread = it.unread + message.first) }
            if (message != null && !message.second.outgoing) messageArrived(message.first, message.second)
            publishChat()
            saveHistorySoon()
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
        // Lost (no answer, or the network itself went: a failed try while reconnecting counts too)
        val lost = client?.lostConnection == true || _state.value.reconnecting > 0
        if (lost && wasConnected && target != null && retries < 20) {
            // The network went away (Wi-Fi to mobile data, a tunnel...): try again, sooner when a network comes back
            files?.onDisconnected()
            files = null
            engine?.reset()
            stopAudio()
            client = null
            retries++
            if (retries == 1) sound(Cue.CONNECTION_LOST)
            _state.update { it.copy(phase = Phase.CONNECTING, reconnecting = retries, error = reason, talking = emptySet()) }
            ConnectionService.start(app, "Reconnecting to ${_state.value.address}…")
            scheduleRetry(minOf(30L, 2L * retries))
            return
        }
        finish(reason)
    }

    private fun scheduleRetry(seconds: Long) {
        retryTask?.cancel(false)
        retryTask = retryTimer.schedule({ retryNow() }, seconds, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun retryNow() {
        if (target == null || client != null || _state.value.reconnecting == 0) return
        AppLog.log("Reconnecting (try $retries)")
        start(keepChat = true)
    }

    /**
     * The phone's network: when it goes or changes (Wi-Fi to mobile data, airplane mode), the connection is lost at
     * once, instead of after the server's silence; a network coming back hurries a waiting reconnect.
     */
    @Volatile private var defaultNetwork: android.net.Network? = null

    private fun watchNetwork() {
        if (network != null) return
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: android.net.Network) {
                val before = defaultNetwork
                defaultNetwork = n
                // Another network took over: the connection went out the old one, and the server won't follow
                if (before != null && before != n) client?.networkGone("The network changed")
                if (_state.value.reconnecting > 0 && client == null) scheduleRetry(1)
            }

            override fun onLost(n: android.net.Network) {
                if (n != defaultNetwork) return
                defaultNetwork = null
                client?.networkGone("The network went away")
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            network = cb
        } catch (_: Exception) {
        }
    }

    /** Away (with a message) or back, as the others see it. */
    fun setAway(away: Boolean, message: String = "") {
        _state.update { it.copy(away = away, awayMessage = if (away) message else "") }
        client?.sendCommand(Commands.build("clientupdate", "client_away" to away, "client_away_message" to if (away) message else ""))
    }

    private fun finish(reason: String?) {
        if (_state.value.phase != Phase.IDLE && !kicked) UiSounds.play(Cue.DISCONNECTED, _state.value.voice, _state.value.outputMuted)
        kicked = false
        engine?.reset()
        retryTask?.cancel(false)
        target = null
        files?.onDisconnected()
        files = null
        stopAudio()
        client = null
        closeHistory()
        val s = _state.value
        _state.value = UiState(
            phase = Phase.IDLE, error = reason, address = s.address, inputMuted = s.inputMuted, outputMuted = s.outputMuted,
            volumes = s.volumes, voice = s.voice, micAllowed = s.micAllowed,
        )
        if (::app.isInitialized) ConnectionService.stop(app)
        pendingSwitch?.let { next ->
            pendingSwitch = null
            android.os.Handler(android.os.Looper.getMainLooper()).post { next() }
        }
    }

    private fun setTalking(clientId: Int, talking: Boolean) {
        _state.update { s -> s.copy(talking = if (talking) s.talking + clientId else s.talking - clientId) }
        if (clientId == _state.value.ownId) engine?.refreshSound()
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
        if (key is Chat.Key.Private) {
            val text = io.github.korppujauho.pocketts.proto.Pictures.teamSpeakFile(e.text)?.text ?: MessageText.plain(e.text)
            ConnectionService.notifyMessage(app, e.senderName, text)
        }
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
        ownCodec = model.ownChannel?.codec?.takeIf { it == 4 || it == 5 } ?: 4
        _state.update {
            it.copy(
                phase = phase, serverName = model.serverName, tree = tree,
                ownId = model.ownClientId, ownChannel = me?.channel ?: 0,
                cannotTalk = model.clients.values.filter { c -> !model.canTalk(c) }.map { c -> c.id }.toSet(),
                server = HashMap(model.serverVars),
                badges = model.clients.values.associate { c -> c.id to model.badges(c) },
                streams = HashMap(model.streams),
                serverIcon = model.serverVars["virtualserver_icon_id"]?.toLongOrNull()?.let { v -> if (v < 0) v + (1L shl 32) else v } ?: 0,
                channelCount = model.channels.size,
                clientCount = model.clients.values.count { c -> c.type == 0 },
            )
        }
    }
}
