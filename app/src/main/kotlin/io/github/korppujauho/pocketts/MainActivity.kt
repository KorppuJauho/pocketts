package io.github.korppujauho.pocketts

import android.Manifest
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.korppujauho.pocketts.proto.Chat
import io.github.korppujauho.pocketts.proto.ServerModel
import io.github.korppujauho.pocketts.ui.ConnectActions
import io.github.korppujauho.pocketts.ui.ConnectScreen
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.ServerActions
import io.github.korppujauho.pocketts.ui.ServerScreen
import io.github.korppujauho.pocketts.ui.SettingsActions

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Session.micPermission(this, granted)
    }

    private var cameraWanted: (() -> Unit)? = null
    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val start = cameraWanted ?: return@registerForActivityResult
        cameraWanted = null
        if (granted) start()
    }

    /** The camera: asked for first if needed. */
    fun shareCamera(front: Boolean, quality: Int, sound: Boolean) = withCamera { Session.shareCamera(front, quality, sound) }

    fun shareBothCameras(quality: Int, sound: Boolean) = withCamera { Session.shareBothCameras(quality, sound) }

    private fun withCamera(start: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            cameraWanted = start
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    // ====== The watched stream as a small window (picture-in-picture), like a video app ======

    private fun pipParams(): android.app.PictureInPictureParams {
        // Android allows shapes between 1:2.39 and 2.39:1
        val w = io.github.korppujauho.pocketts.ui.StreamSize.width
        val h = io.github.korppujauho.pocketts.ui.StreamSize.height
        val ratio = (w * 1000L / h).coerceIn(419L, 2390L).toInt()
        val b = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(ratio, 1000))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(Session.state.value.stream.watching != 0).setSeamlessResizeEnabled(false)
        return b.build()
    }

    /** Leaving the app while watching makes the stream a small window (Android 12 and later do it by themselves). */
    fun updatePip() {
        try { setPictureInPictureParams(pipParams()) } catch (e: Exception) { AppLog.log("Small window: $e") }
    }

    fun enterPip() {
        try { enterPictureInPictureMode(pipParams()) } catch (e: Exception) { AppLog.log("Small window: $e") }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && Session.state.value.stream.watching != 0) enterPip()
    }

    private val askScreen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val m = resources.displayMetrics
            Session.shareScreen(data, m.widthPixels, m.heightPixels)
        }
    }

    /** The screen: Android asks the person first. */
    fun shareScreen() {
        val pm = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        askScreen.launch(pm.createScreenCaptureIntent())
    }

    /** Asks for the microphone if it isn't allowed yet; true when it already is. */
    fun ensureMic(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        askMic.launch(Manifest.permission.RECORD_AUDIO)
        return false
    }

    // Volume buttons as push-to-talk (Settings > General), while the app is on screen
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val s = Session.state.value
        val volumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (volumeKey && s.phase == Session.Phase.CONNECTED && s.voice.volumeKeysPtt &&
            s.voice.activation == VoiceSettings.Activation.PTT
        ) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) Session.setPtt(true)
                KeyEvent.ACTION_UP -> Session.setPtt(false)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.start()
        ServerFiles.install(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        current = this
        favorites.value = Favorites.load(this)
        Session.load(this)
        if (savedInstanceState == null && Session.state.value.phase == Session.Phase.IDLE && !Session.autoConnected) {
            Session.autoConnected = true
            favorites.value.firstOrNull { it.auto }?.let { connectFavorite(applicationContext, it) }
        }
        TalkOverlay.start(this)
        HeadsetButton.start(this)
        io.github.korppujauho.pocketts.ui.StreamSize.onChange = { runOnUiThread { current?.updatePip() } }
        addOnPictureInPictureModeChangedListener { info ->
            inPip.value = info.isInPictureInPictureMode
            Session.pip = info.isInPictureInPictureMode
            // The small window closed (not opened full again): watching ends, as in a video app
            if (!info.isInPictureInPictureMode && lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED) Session.stopWatching()
        }
        setContent { PocketTsTheme { App() } }
    }

    override fun onStart() {
        super.onStart()
        Session.foreground = true
    }

    override fun onStop() {
        Session.foreground = false
        super.onStop()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        var current: MainActivity? = null
    }
}

/** The app is a small picture-in-picture window. */
private val inPip = mutableStateOf(false)

/** Whether this phone can use both cameras at once (asked once). */
private val canBoth by lazy { MainActivity.current?.let { DualCamera.pair(it) != null } ?: false }

/** The favourites, shared by the screens (they change from both). */
private val favorites = kotlinx.coroutines.flow.MutableStateFlow<List<Favorite>>(emptyList())

@Composable
private fun App() {
    val state by Session.state.collectAsState()
    val favs by favorites.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val pip by inPip
    androidx.compose.runtime.LaunchedEffect(state.stream.watching) { MainActivity.current?.updatePip() }
    if (state.phase == Session.Phase.IDLE) {
        Connect(state, favs)
    } else {
        val egl = Session.streams().egl
        androidx.compose.foundation.layout.Box {
            ServerScreen(
                state, SessionActions, AppSettingsActions(ctx), settings.nickname, favorites = favs, egl = egl,
                canBoth = canBoth, pip = pip,
            )
            if (pip) io.github.korppujauho.pocketts.ui.PipPlayer(state.stream, egl)
        }
    }
}

/** Connects (the microphone is asked for once; talking starts when it's allowed). */
private fun connect(context: Context, address: String, nickname: String, password: String, filePort: Int? = null) {
    Settings(context).let {
        it.nickname = nickname
        it.address = address
    }
    MainActivity.current?.ensureMic()
    Session.connect(context, address, nickname, password, filePort)
}

private fun connectFavorite(context: Context, f: Favorite) =
    connect(context, f.address, f.nickname.ifBlank { Settings(context).nickname }, f.password, f.filePort)

private fun saveFavorites(context: Context, list: List<Favorite>) {
    Favorites.save(context, list)
    favorites.value = Favorites.load(context)
}

@Composable
private fun Connect(state: Session.UiState, favs: List<Favorite>) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    ConnectScreen(
        settings.nickname, settings.address, settings.serverPassword, favs, state.error,
        actions = object : ConnectActions {
            override fun connect(address: String, nickname: String, password: String, remember: Boolean, favorite: Boolean) {
                settings.serverPassword = if (remember) password else ""
                if (favorite) saveFavorites(ctx, favs + Favorite(address, password = if (remember) password else ""))
                connect(ctx, address, nickname, password)
            }
            override fun connectFavorite(f: Favorite) = connectFavorite(ctx, f)
            override fun saveFavorite(old: Favorite?, new: Favorite) =
                saveFavorites(ctx, favs.map { if (it == old) new else if (new.auto) it.copy(auto = false) else it })
            override fun removeFavorite(f: Favorite) = saveFavorites(ctx, favs - f)
            override fun toggleAuto(f: Favorite) = saveFavorites(ctx, favs.map { it.copy(auto = if (it == f) !f.auto else false) })
        },
        voice = state.voice, settingsActions = AppSettingsActions(ctx), onDismissError = Session::clearError,
    )
}

private object SessionActions : ServerActions {
    override fun join(channel: ServerModel.Channel, password: String, remember: Boolean, quiet: Boolean) =
        Session.joinChannel(channel.id, password, remember, quiet)
    override fun savedPassword(channel: ServerModel.Channel) = Session.savedChannelPassword(channel.id)
    override fun passwordAsked() = Session.passwordAsked()
    override fun disconnect() = Session.disconnect()
    override fun toggleMic() {
        if (!Session.state.value.micAllowed) {
            if (MainActivity.current?.ensureMic() == true) Session.micPermission(MainActivity.current!!, true)
            return
        }
        Session.toggleMic()
    }
    override fun toggleSound() = Session.toggleSound()
    override fun openSettings() {}
    override fun send(key: Chat.Key, text: String) = Session.send(key, text)
    override fun openPrivate(person: ServerModel.Client) = Session.openPrivate(person)
    override fun closePrivate(uid: String) = Session.closePrivate(uid)
    override fun poke(person: ServerModel.Client, text: String) = Session.poke(person, text)
    override fun dismissPoke(id: Long) = Session.dismissPoke(id)
    override fun setVolume(person: ServerModel.Client, volume: Float) = Session.setVolume(person, volume)
    override fun setPtt(held: Boolean) = Session.setPtt(held)
    override fun sendPicture(uri: android.net.Uri, text: String) {
        MainActivity.current?.let { Session.sendPicture(it.applicationContext, uri, text) }
    }
    override fun viewing(key: Chat.Key?) {
        Session.viewing = key
    }
    override fun setAway(away: Boolean, message: String) = Session.setAway(away, message)
    override fun switchTo(f: Favorite) {
        val ctx = MainActivity.current?.applicationContext ?: return
        Session.switchTo { connectFavorite(ctx, f) }
    }
    override fun watch(person: ServerModel.Client) {
        Session.watch(person)
    }
    override fun stopWatching() {
        Session.stopWatching()
    }
    override fun shareCamera(front: Boolean, quality: Int, sound: Boolean) {
        MainActivity.current?.shareCamera(front, quality, sound)
    }
    override fun shareScreen() {
        MainActivity.current?.shareScreen()
    }
    override fun stopShare() {
        Session.stopShare()
    }
    override fun flipCamera() {
        Session.flipCamera()
    }
    override fun shareBothCameras(quality: Int, sound: Boolean) {
        MainActivity.current?.shareBothCameras(quality, sound)
    }
    override fun setRotateLock(on: Boolean) = Session.setRotateLock(on)
    override fun setWatchMuted(muted: Boolean) {
        Session.setWatchMuted(muted)
    }
    override fun dismissError() = Session.clearError()
    override fun setWatchVolume(volume: Float) {
        Session.setWatchVolume(volume)
    }
    override fun pictureInPicture() {
        MainActivity.current?.enterPip()
    }
    override fun addFavorite(f: Favorite) {
        val ctx = MainActivity.current?.applicationContext ?: return
        saveFavorites(ctx, favorites.value.map { if (f.auto) it.copy(auto = false) else it } + f)
    }
}

private class AppSettingsActions(private val context: Context) : SettingsActions {
    override fun save(voice: VoiceSettings, nickname: String) {
        // Turned on: Android's own page to allow it, if not allowed yet
        if (voice.overlayPtt && !Session.state.value.voice.overlayPtt && !TalkOverlay.allowed(context)) {
            MainActivity.current?.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
            )
        }
        Session.updateVoice(voice)
        if (nickname.isNotEmpty() && nickname != Settings(context).nickname) {
            Settings(context).nickname = nickname
            Session.setNickname(nickname)
        }
    }
    override fun micLevel() = Session.micLevel()
    override fun micTest(on: Boolean) = Session.micTest(on)
    override fun playTestSound() = TestSound.play(Session.state.value.voice)
    override fun setAvatar(uri: android.net.Uri) = Session.setAvatar(context, uri)
    override fun removeAvatar() = Session.removeAvatar()
    override fun exportIdentity(uri: android.net.Uri): String? = try {
        // Into the file the person chose, as TeamSpeak apps save it (so they can open it too); nowhere else
        val s = Settings(context)
        val ini = s.identity().exportIni("PocketTS", s.nickname)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(ini.toByteArray(Charsets.UTF_8)) }
            ?: throw java.io.IOException("no file")
        AppLog.log("Identity saved to a file")
        null
    } catch (e: Settings.IdentityUnreadable) {
        "The identity can't be read, so it can't be saved."
    } catch (e: Exception) {
        AppLog.log("Couldn't save the identity: ${e.javaClass.simpleName}")
        "That file couldn't be written."
    }
    override fun importIdentity(text: String): String? = Settings(context).importIdentity(text)
    override fun importIdentityFile(uri: android.net.Uri): String? = try {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            // An identity file is small: past 64 KB it isn't one
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < 64 * 1024) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.toString(Charsets.UTF_8.name())
        }
        if (text == null) "That file can't be opened." else importIdentity(text)
    } catch (e: Exception) {
        AppLog.log("Couldn't read the identity file: ${e.javaClass.simpleName}")
        "That file can't be opened."
    }
    override fun identityProblem(): String? = Settings(context).identityProblem()
    override fun forgetIdentity() = Settings(context).forgetIdentity()
    override fun clearChatHistory() = Session.clearChatHistory(context)
    override fun forgetChannelPasswords() = ChannelPasswords(context).clear()
}
