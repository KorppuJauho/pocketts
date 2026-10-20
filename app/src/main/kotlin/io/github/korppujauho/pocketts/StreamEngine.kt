package io.github.korppujauho.pocketts

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import io.github.korppujauho.pocketts.proto.Command
import io.github.korppujauho.pocketts.proto.ServerModel
import io.github.korppujauho.pocketts.proto.Streams
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpParameters
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Screen and camera streams over WebRTC, the TeamSpeak 6 way (as WebTS does it): watching someone's stream, and
 * sharing ours with everyone who asks. Everything happens on one thread of its own; [send] sends a command.
 */
class StreamEngine(
    private val context: Context,
    private val sendNow: (String) -> Unit,
    private val settings: () -> VoiceSettings,
    private val onState: (State) -> Unit,
) {
    /** What the screen shows about streams. */
    data class State(
        val watching: Int = 0, // the streamer's client id (0 = none)
        val watchStatus: String = "",
        val watchVideo: VideoTrack? = null,
        val watchAudio: org.webrtc.AudioTrack? = null,
        val watchMuted: Boolean = false,
        val sharing: Streams.Kind? = null,
        val shareLive: Boolean = false,
        val shareFront: Boolean = true, // the front camera (with both: the front one is the big picture)
        val shareBoth: Boolean = false, // both cameras at once
        val rotateLocked: Boolean = false, // the camera's turn is locked (else it follows how the phone is held)
        val phoneTurn: Int = 0, // while sharing the camera: how the phone is held, clockwise from upright
        val lockedTurn: Int = 0, // the turn kept while locked (the phone's, from the screen's upright, when locked)
        val watchVolume: Float = 1f, // the watched stream's sound, 0 - 1
        val viewers: Int = 0,
        val localVideo: VideoTrack? = null,
        val message: String? = null, // why a share stopped or couldn't start
    )

    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "streams").apply { isDaemon = true } }
    private val counter = AtomicInteger(0)
    private fun rc(what: String) = "stream-$what-${counter.incrementAndGet()}"

    // What each command was (by its return code), to send it again when the server asks to wait
    private val sent = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
    }

    private fun send(cmd: String) {
        Regex("""return_code=(\S+)""").find(cmd)?.groupValues?.get(1)?.let { synchronized(sent) { sent[it] = cmd } }
        sendNow(cmd)
    }

    val egl: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(context.applicationContext).createAudioDeviceModule())
            .createPeerConnectionFactory()
    }
    private val decoders by lazy {
        factory // loads WebRTC's library first
        DefaultVideoDecoderFactory(egl.eglBaseContext).supportedCodecs.toList()
    }

    @Volatile var state = State()
        private set

    private fun update(f: (State) -> State) {
        state = f(state)
        onState(state)
    }

    private fun log(s: String) = AppLog.log("[Streams] $s")

    private fun config(): PeerConnection.RTCConfiguration {
        val v = settings()
        val servers = Streams.STUN.map { PeerConnection.IceServer.builder(it).createIceServer() }.toMutableList()
        val relay = v.turnUrl.trim()
        if (relay.isNotEmpty()) {
            servers.add(PeerConnection.IceServer.builder(relay).setUsername(v.turnUser).setPassword(v.turnPassword).createIceServer())
        }
        return PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            if (v.relayOnly && relay.isNotEmpty()) iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
    }

    // ====== Small waits for WebRTC's callbacks (on our own thread) ======

    private fun <T> await(what: String, start: (ok: (T) -> Unit, fail: (String) -> Unit) -> Unit): T {
        val done = CountDownLatch(1)
        var result: T? = null
        var error: String? = null
        start({ result = it; done.countDown() }, { error = it; done.countDown() })
        if (!done.await(10, TimeUnit.SECONDS)) throw IllegalStateException("$what took too long")
        error?.let { throw IllegalStateException("$what: $it") }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun sdpObserver(ok: (SessionDescription?) -> Unit, fail: (String) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(sd: SessionDescription) = ok(sd)
        override fun onSetSuccess() = ok(null)
        override fun onCreateFailure(e: String) = fail(e)
        override fun onSetFailure(e: String) = fail(e)
    }

    private fun setRemote(pc: PeerConnection, type: SessionDescription.Type, sdp: String) =
        await<SessionDescription?>("Setting their description") { ok, fail -> pc.setRemoteDescription(sdpObserver(ok, fail), SessionDescription(type, sdp)) }

    private fun setLocal(pc: PeerConnection, sd: SessionDescription) =
        await<SessionDescription?>("Setting our description") { ok, fail -> pc.setLocalDescription(sdpObserver(ok, fail), sd) }

    private fun answer(pc: PeerConnection): SessionDescription =
        await<SessionDescription?>("Making the answer") { ok, fail -> pc.createAnswer(sdpObserver(ok, fail), MediaConstraints()) }!!

    private fun offer(pc: PeerConnection, restart: Boolean = false): SessionDescription =
        await<SessionDescription?>("Making the offer") { ok, fail ->
            pc.createOffer(sdpObserver(ok, fail), MediaConstraints().apply {
                if (restart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            })
        }!!

    private open inner class Observer(val name: String) : PeerConnection.Observer {
        override fun onSignalingChange(s: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) = log("$name: ice $s")
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
        override fun onIceCandidate(c: IceCandidate) {}
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
        override fun onAddStream(s: MediaStream) {}
        override fun onRemoveStream(s: MediaStream) {}
        override fun onDataChannel(d: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) {}
    }

    // ====== Watching ======

    private class Watch(val streamerId: Int, var streamId: String?) {
        var pc: PeerConnection? = null
        val pending = ArrayList<IceCandidate>()
        var original: Map<String, String> = emptyMap()
    }

    private var watch: Watch? = null

    /** Starts watching [streamer]'s stream ([streamId] when known; else asked from the server first). */
    fun watch(streamer: ServerModel.Client, streamId: String?) = exec.execute {
        closeWatch(sendLeave = true)
        watch = Watch(streamer.id, streamId)
        update { it.copy(watching = streamer.id, watchStatus = "Requesting access…", watchVideo = null, watchAudio = null, watchMuted = false) }
        if (streamId == null) send(Streams.requestInfo(streamer.id, rc("info"))) else send(Streams.join(streamId, streamer.id, rc("join")))
    }

    fun stopWatching() = exec.execute { closeWatch(sendLeave = true) }

    /** How loud the watched stream is, 0 - 1. */
    fun setWatchVolume(volume: Float) = exec.execute {
        state.watchAudio?.setVolume(volume.toDouble())
        update { it.copy(watchVolume = volume) }
    }

    /** The stream's sound off or on (kept while the player becomes a small window and back). */
    fun setWatchMuted(muted: Boolean) = exec.execute {
        state.watchAudio?.setEnabled(!muted)
        update { it.copy(watchMuted = muted) }
    }

    private fun closeWatch(sendLeave: Boolean) {
        val w = watch ?: return
        watch = null
        if (sendLeave) w.streamId?.let { send(Streams.leave(it, w.streamerId, rc("leave"))) }
        w.pc?.dispose()
        update { it.copy(watching = 0, watchStatus = "", watchVideo = null, watchAudio = null) }
    }

    private fun endWatch(message: String) {
        val w = watch ?: return
        update { it.copy(watchStatus = message) }
        exec.execute {
            Thread.sleep(2500)
            if (watch === w) closeWatch(sendLeave = false)
        }
    }

    /** The streamer's offer: answered, on a new connection or (a further offer) the same one. */
    private fun onOffer(streamId: String, offerSdp: String) {
        val w = watch ?: return
        if (w.streamId != null && w.streamId != streamId) return
        w.streamId = streamId
        try {
            val profiles = decoders.filter { it.name.equals("H264", true) }
                .mapNotNull { it.params["profile-level-id"]?.take(4)?.lowercase() }
            val profile = if ("640c" in profiles) null else listOf("6400", "42e0", "4200").firstOrNull { it in profiles }
            val relabel = Streams.relabelOfferH264(offerSdp, profile, Streams.offerHasOther(offerSdp, decoders.map { it.name }))
            w.original = relabel.original
            val pc = w.pc ?: createViewerConnection(w) ?: return
            setRemote(pc, SessionDescription.Type.OFFER, relabel.sdp)
            w.pending.forEach { pc.addIceCandidate(it) }
            w.pending.clear()
            val local = answer(pc)
            setLocal(pc, local)
            val sdp = Streams.withOfferSessionVersion(Streams.restoreH264(pc.localDescription.description, w.original), offerSdp)
            send(Streams.answer(streamId, w.streamerId, sdp, rc("answer")))
            update { it.copy(watchStatus = "Connecting…") }
        } catch (e: Throwable) {
            log("Watching failed: $e")
            endWatch("The stream couldn't be opened.")
        }
    }

    private fun createViewerConnection(w: Watch): PeerConnection? {
        val pc = factory.createPeerConnection(config(), object : Observer("watch") {
            override fun onIceCandidate(c: IceCandidate) {
                val id = w.streamId ?: return
                send(Streams.candidate(id, w.streamerId, c.sdp, c.sdpMid, c.sdpMLineIndex, rc("ice")))
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                log("watch: $s")
                exec.execute {
                    if (watch !== w) return@execute
                    when (s) {
                        PeerConnection.PeerConnectionState.CONNECTED -> update { it.copy(watchStatus = if (it.watchVideo != null) "" else "Connected, waiting for the picture…") }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            update { it.copy(watchStatus = "Connection problem, still trying…") }
                            exec.execute {
                                Thread.sleep(20_000)
                                if (watch === w && w.pc?.connectionState() == PeerConnection.PeerConnectionState.FAILED) endWatch("Connection lost.")
                            }
                        }
                        else -> {}
                    }
                }
            }
            override fun onTrack(t: RtpTransceiver) {
                val track = t.receiver.track()
                exec.execute {
                    if (watch !== w) return@execute
                    when (track) {
                        is VideoTrack -> update { it.copy(watchVideo = track, watchStatus = "") }
                        is org.webrtc.AudioTrack -> {
                            track.setEnabled(!state.watchMuted)
                            track.setVolume(state.watchVolume.toDouble())
                            update { it.copy(watchAudio = track) }
                        }
                    }
                }
            }
        }) ?: return null
        w.pc = pc
        return pc
    }

    // ====== Sharing ======

    private class Viewer(val id: Int, val pc: PeerConnection) {
        val pending = ArrayList<IceCandidate>()
        val outgoing = ArrayList<IceCandidate>()
        var answered = false
        var offerSent = false
        var original: Map<String, String> = emptyMap()
    }

    private var shareId: String? = null
    private var sharePending = false
    private val viewers = HashMap<Int, Viewer>()
    private var capturer: VideoCapturer? = null
    private var source: VideoSource? = null
    private var helper: SurfaceTextureHelper? = null
    private var track: VideoTrack? = null
    private var bitrate = 2500
    private var fps = 30
    private val turn by lazy {
        CameraTurn(context).also { t -> t.onTurn = { deg -> exec.execute { if (state.sharing == Streams.Kind.CAMERA || deg == 0) update { it.copy(phoneTurn = deg) } } } }
    }
    private var dual: DualCamera? = null

    /** Starts sharing the camera ([front] or back, [hd] 720p else 480p). */
    fun shareCamera(front: Boolean, quality: Int, name: String) = exec.execute {
        if (state.sharing != null) return@execute
        val cams = Camera2Enumerator(context)
        val device = cams.deviceNames.firstOrNull { cams.isFrontFacing(it) == front } ?: cams.deviceNames.firstOrNull() ?: run {
            log("No camera")
            return@execute
        }
        bitrate = when { quality >= 1080 -> 4500; quality >= 720 -> 2500; else -> 1000 }
        update { it.copy(shareFront = cams.isFrontFacing(device), shareBoth = false) }
        turn.start(settings().cameraRotateLock)
        startCapture(cams.createCapturer(device, null), false, widthFor(quality), quality, Streams.Kind.CAMERA, name) {
            turn.observer(it) { state.shareFront }
        }
    }

    /** Both cameras at once: the back one big, the front one small in a corner (Flip swaps them). */
    fun shareBothCameras(quality: Int, name: String) = exec.execute {
        if (state.sharing != null) return@execute
        val ids = DualCamera.pair(context) ?: run {
            update { it.copy(message = "This phone can't use both cameras at the same time.") }
            return@execute
        }
        bitrate = when { quality >= 1080 -> 5000; quality >= 720 -> 3000; else -> 1200 }
        update { it.copy(shareFront = false, shareBoth = true) }
        turn.start(settings().cameraRotateLock)
        val cam = DualCamera(context, egl.eglBaseContext, ids) { front -> turn.extra(front) }
        dual = cam
        startCapture(cam, false, widthFor(quality), quality, Streams.Kind.CAMERA, name)
    }

    // 16:9 for HD and Full HD, 4:3 for the data saver (as cameras offer them)
    private fun widthFor(height: Int) = when {
        height >= 1080 -> 1920
        height >= 720 -> 1280
        else -> 640
    }

    /** The warning about a share was closed. */
    fun clearMessage() = exec.execute {
        if (state.message != null) update { it.copy(message = null) }
    }

    /** The camera's turn locked, or following how the phone is held. */
    fun setRotateLock(on: Boolean) = exec.execute {
        turn.lock(on)
        update { it.copy(rotateLocked = on, lockedTurn = turn.extra(false)) }
    }

    /** Starts sharing the screen, with the permission Android gave ([projection]: its result). */
    fun shareScreen(projection: Intent, width: Int, height: Int, name: String) = exec.execute {
        if (state.sharing != null) return@execute
        val cap = ScreenCapturerAndroid(projection, object : MediaProjection.Callback() {
            override fun onStop() = stopShare()
        })
        // At most 1920 on the long side, the screen's shape kept
        val scale = minOf(1.0, 1920.0 / maxOf(width, height))
        bitrate = 4000
        startCapture(cap, true, (width * scale).toInt() / 2 * 2, (height * scale).toInt() / 2 * 2, Streams.Kind.SCREEN, name)
    }

    private fun startCapture(
        cap: VideoCapturer, screen: Boolean, w: Int, h: Int, kind: Streams.Kind, name: String,
        observer: (org.webrtc.CapturerObserver) -> org.webrtc.CapturerObserver = { it },
    ) {
        try {
            val src = factory.createVideoSource(screen)
            val help = SurfaceTextureHelper.create("capture", egl.eglBaseContext)
            cap.initialize(help, context.applicationContext, observer(src.capturerObserver))
            cap.startCapture(w, h, fps)
            capturer = cap; source = src; helper = help
            track = factory.createVideoTrack("share", src)
            sharePending = true
            update { it.copy(sharing = kind, shareLive = false, viewers = 0, localVideo = track, message = null, rotateLocked = turn.locked, lockedTurn = turn.extra(false)) }
            send(Streams.setup(name, kind, bitrate, audio = false, returnCode = rc("setup")))
            log("Share starting: $kind ${w}x$h")
        } catch (e: Exception) {
            log("Share failed to start: $e")
            releaseCapture()
            update { it.copy(sharing = null, localVideo = null, message = "The share couldn't start.") }
        }
    }

    /** The other camera (front ↔ back) while sharing it. */
    fun flipCamera() = exec.execute {
        dual?.let { d ->
            d.frontBig = !d.frontBig
            update { it.copy(shareFront = d.frontBig) }
            return@execute
        }
        val cam = capturer as? org.webrtc.CameraVideoCapturer ?: return@execute
        cam.switchCamera(object : org.webrtc.CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(front: Boolean) = exec.execute { update { it.copy(shareFront = front) } }
            override fun onCameraSwitchError(e: String) = log("Camera switch failed: $e")
        })
    }

    fun stopShare() = exec.execute {
        shareId?.let { send(Streams.stop(it)) }
        endShare()
    }

    private fun endShare() {
        shareId = null
        sharePending = false
        viewers.values.forEach { it.pc.dispose() }
        viewers.clear()
        releaseCapture()
        update { it.copy(sharing = null, shareLive = false, viewers = 0, localVideo = null, phoneTurn = 0) }
    }

    private fun releaseCapture() {
        turn.stop()
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose(); capturer = null
        dual = null
        track?.dispose(); track = null
        source?.dispose(); source = null
        helper?.dispose(); helper = null
    }

    /** Someone asks to watch our share: everyone may, so they get our offer right away. */
    private fun addViewer(clid: Int) {
        val id = shareId ?: return
        if (clid in viewers) return
        val t = track ?: return
        lateinit var v: Viewer
        val pc = factory.createPeerConnection(config(), object : Observer("viewer $clid") {
            override fun onIceCandidate(c: IceCandidate) = exec.execute {
                // TeamSpeak refuses a viewer's addresses sent before our offer let them in: they wait until it's gone
                if (viewers[clid] !== v) return@execute
                if (v.offerSent) send(Streams.candidate(id, clid, c.sdp, c.sdpMid, c.sdpMLineIndex, rc("ice"))) else v.outgoing.add(c)
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                log("viewer $clid: $s")
                if (s == PeerConnection.PeerConnectionState.FAILED) exec.execute {
                    Thread.sleep(20_000)
                    if (viewers[clid] === v && v.pc.connectionState() == PeerConnection.PeerConnectionState.FAILED) dropViewer(clid)
                }
            }
        }) ?: return
        v = Viewer(clid, pc)
        viewers[clid] = v
        try {
            val video = pc.addTransceiver(t, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, listOf("share")))
            preferH264(video)
            // A sound line without a track: the TeamSpeak app never answers an offer with video only
            pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
            val local = offer(pc)
            setLocal(pc, local)
            applyLimits(pc)
            val relabel = Streams.relabelShareOfferH264(Streams.withoutVideoOrientation(Streams.withoutStrayPayloadLines(pc.localDescription.description)))
            v.original = relabel.original
            send(Streams.accept(id, clid, relabel.sdp, rc("accept")))
            v.offerSent = true
            v.outgoing.forEach { send(Streams.candidate(id, clid, it.sdp, it.sdpMid, it.sdpMLineIndex, rc("ice"))) }
            v.outgoing.clear()
            update { it.copy(viewers = viewers.size) }
        } catch (e: Throwable) {
            log("Couldn't offer the share to $clid: $e")
            dropViewer(clid)
        }
    }

    /** H.264 first when this phone can encode it in hardware (as WebTS prefers it for the TeamSpeak app). */
    private fun preferH264(t: RtpTransceiver) {
        try {
            val caps = factory.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
            val order = listOf("H264", "VP8", "VP9", "AV1")
            val sorted = caps.sortedBy { c -> order.indexOfFirst { it.equals(c.name, true) }.let { if (it < 0) order.size else it } }
            t.setCodecPreferences(sorted)
        } catch (e: Throwable) {
            log("Codec order not applied: ${e.message}")
        }
    }

    /** The real limits are on the connection: bitrate and frame rate, keeping the resolution when it struggles. */
    private fun applyLimits(pc: PeerConnection) {
        for (sender in pc.senders) {
            if (sender.track() == null) continue
            val p = sender.parameters
            p.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
            p.encodings.forEach {
                it.maxBitrateBps = bitrate * 1000
                it.maxFramerate = fps
            }
            sender.parameters = p
        }
    }

    private fun dropViewer(clid: Int) {
        viewers.remove(clid)?.pc?.dispose()
        update { it.copy(viewers = viewers.size) }
    }

    private fun onShareSignal(s: Streams.Signal) {
        val v = viewers[s.from] ?: return
        val id = shareId ?: return
        try {
            when (s.cmd) {
                "answer" -> {
                    val sdp = s.sdp ?: return
                    setRemote(v.pc, SessionDescription.Type.ANSWER, Streams.withShareSendLevel(Streams.restoreH264(sdp, v.original)))
                    v.answered = true
                    v.pending.forEach { v.pc.addIceCandidate(it) }
                    v.pending.clear()
                }
                "iceCandidate" -> {
                    val c = IceCandidate(s.mid ?: "0", s.mLine ?: 0, s.candidate ?: return)
                    if (v.answered) v.pc.addIceCandidate(c) else v.pending.add(c)
                }
                "reconnect" -> {
                    v.offerSent = false
                    val local = offer(v.pc, restart = true)
                    setLocal(v.pc, local)
                    applyLimits(v.pc)
                    val relabel = Streams.relabelShareOfferH264(Streams.withoutVideoOrientation(Streams.withoutStrayPayloadLines(v.pc.localDescription.description)))
                    v.original = relabel.original
                    v.answered = false
                    send(Streams.reconnectOffer(id, s.from, relabel.sdp, rc("reoffer")))
                    v.offerSent = true
                    v.outgoing.forEach { send(Streams.candidate(id, s.from, it.sdp, it.sdpMid, it.sdpMLineIndex, rc("ice"))) }
                    v.outgoing.clear()
                }
                else -> log("Unhandled signal from ${s.from}: ${s.cmd}")
            }
        } catch (e: Exception) {
            log("Viewer ${s.from}: ${e.message}")
            dropViewer(s.from)
        }
    }

    // ====== From the server ======

    /** Every command (any thread); [model] is read on our thread only for ids, so copies are passed. */
    fun onCommand(cmd: Command, ownId: Int, ownChannel: () -> Int, channelOf: (Int) -> Int?) {
        when (cmd.name) {
            "notifystreaminfo", "notifystreamstarted", "notifystreamstopped", "notifyrespondjoinstreamrequest",
            "notifystreamsignaling", "notifyjoinstreamrequest", "notifystreamclientleft", "notifyclientmoved", "notifyclientleftview", "error",
            -> exec.execute { handle(cmd, ownId, ownChannel, channelOf) }
        }
    }

    private fun handle(cmd: Command, ownId: Int, ownChannel: () -> Int, channelOf: (Int) -> Int?) {
        val g = cmd.params
        when (cmd.name) {
            "notifystreaminfo", "notifystreamstarted" -> {
                val id = g["id"] ?: g["stream_id"] ?: return
                val clid = g["clid"]?.toIntOrNull() ?: return
                if (cmd.name == "notifystreamstarted" && clid == ownId && sharePending) {
                    sharePending = false
                    shareId = id
                    update { it.copy(shareLive = true) }
                    log("Share live as $id")
                }
                val w = watch
                if (w != null && w.streamerId == clid && w.streamId == null) {
                    w.streamId = id
                    send(Streams.join(id, clid, rc("join")))
                }
            }
            "notifystreamstopped" -> {
                val id = g["id"] ?: g["stream_id"]
                val clid = g["clid"]?.toIntOrNull() ?: 0
                if (id != null && id == shareId) endShare()
                val w = watch
                if (w != null && (id == w.streamId || clid == w.streamerId)) endWatch("Stream ended.")
            }
            "notifyrespondjoinstreamrequest" -> {
                val w = watch ?: return
                val offer = g["offer"]
                if (g["decision"] == "1" && !offer.isNullOrEmpty()) onOffer(g["id"] ?: g["stream_id"] ?: w.streamId ?: return, offer)
                else endWatch(g["msg"]?.ifEmpty { null } ?: "The stream request was denied.")
            }
            "notifystreamsignaling" -> {
                val s = Streams.readSignal(cmd) ?: return
                if (shareId != null && s.streamId == shareId) return onShareSignal(s)
                val w = watch ?: return
                val streamId = s.streamId.ifEmpty { w.streamId ?: return }
                when (s.cmd) {
                    "offer", "reconnectOffer" -> s.sdp?.let { onOffer(streamId, it) }
                    "joinResponse" -> if (s.args["decision"] != false) (s.args["offer"] as? String)?.let { onOffer(streamId, it) }
                    "iceCandidate" -> {
                        val c = IceCandidate(s.mid ?: "0", s.mLine ?: 0, s.candidate ?: return)
                        val pc = w.pc
                        if (pc?.remoteDescription != null) pc.addIceCandidate(c) else w.pending.add(c)
                    }
                }
            }
            "notifyjoinstreamrequest" -> {
                val clid = g["clid"]?.toIntOrNull() ?: return
                if (g["id"] != shareId || shareId == null) return
                if (g["is_remove"] == "1") dropViewer(clid) else addViewer(clid)
            }
            "notifystreamclientleft" -> {
                if (g["id"] == shareId) g["clid"]?.toIntOrNull()?.let { dropViewer(it) }
            }
            "notifyclientmoved", "notifyclientleftview" -> {
                // Like the desktop app: watching ends when you and the streamer are no longer in the same channel
                val w = watch ?: return
                val here = ownChannel()
                val theirs = channelOf(w.streamerId)
                if (here > 0 && theirs != here) endWatch("Stream ended (you are no longer in the same channel).")
            }
            "error" -> {
                val code = g["return_code"].orEmpty()
                if (g["id"] == "0" || !code.startsWith("stream-")) return
                // "client is flooding": the server says how long to wait; then the same command again
                if (g["id"] == "524") {
                    val again = synchronized(sent) { sent.remove(code) } ?: return
                    val wait = Regex("""(\d+)\s*ms""").find(g["extra_msg"].orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: 5000
                    log("Flood limit: $code again in ${wait}ms")
                    exec.execute {
                        Thread.sleep(wait + 300)
                        send(again)
                    }
                    return
                }
                log("Refused: ${g["msg"]} ($code)")
                when {
                    code.startsWith("stream-setup") -> {
                        endShare()
                        update { it.copy(message = "TeamSpeak refused the share: ${g["msg"].orEmpty()}") }
                    }
                    code.startsWith("stream-join") || code.startsWith("stream-info") -> endWatch(g["msg"]?.ifEmpty { null } ?: "The stream couldn't be opened.")
                }
            }
        }
    }

    /** The connection to the server ended: everything stops. */
    fun reset() = exec.execute {
        closeWatch(sendLeave = false)
        endShare()
    }

    /** Lets a picture sink show the stream we watch (or stop). */
    fun attach(sink: VideoSink, track: VideoTrack) = exec.execute { track.addSink(sink) }
    fun detach(sink: VideoSink, track: VideoTrack) = exec.execute { try { track.removeSink(sink) } catch (_: Exception) {} }
}
