package io.github.korppujauho.mobilets6

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
import io.github.korppujauho.mobilets6.proto.Chat
import io.github.korppujauho.mobilets6.proto.ServerModel
import io.github.korppujauho.mobilets6.ui.ConnectActions
import io.github.korppujauho.mobilets6.ui.ConnectScreen
import io.github.korppujauho.mobilets6.ui.MobileTsTheme
import io.github.korppujauho.mobilets6.ui.ServerActions
import io.github.korppujauho.mobilets6.ui.ServerScreen
import io.github.korppujauho.mobilets6.ui.SettingsActions

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Session.micPermission(this, granted)
    }

    private var cameraWanted: Pair<Boolean, Boolean>? = null
    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val (front, hd) = cameraWanted ?: return@registerForActivityResult
        cameraWanted = null
        if (granted) Session.shareCamera(front, hd)
    }

    /** The camera: asked for first if needed. */
    fun shareCamera(front: Boolean, hd: Boolean) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Session.shareCamera(front, hd)
        } else {
            cameraWanted = front to hd
            askCamera.launch(Manifest.permission.CAMERA)
        }
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
        if (savedInstanceState == null && Session.state.value.phase == Session.Phase.IDLE && !Session.autoConnected) {
            Session.autoConnected = true
            favorites.value.firstOrNull { it.auto }?.let { connectFavorite(applicationContext, it) }
        }
        setContent { MobileTsTheme { App() } }
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

/** The favourites, shared by the screens (they change from both). */
private val favorites = kotlinx.coroutines.flow.MutableStateFlow<List<Favorite>>(emptyList())

@Composable
private fun App() {
    val state by Session.state.collectAsState()
    val favs by favorites.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    if (state.phase == Session.Phase.IDLE) {
        Connect(state, favs)
    } else {
        ServerScreen(state, SessionActions, AppSettingsActions(ctx), settings.nickname, favorites = favs, egl = Session.streams().egl)
    }
}

/** Connects (the microphone is asked for once; talking starts when it's allowed). */
private fun connect(context: Context, address: String, nickname: String, password: String) {
    Settings(context).let {
        it.nickname = nickname
        it.address = address
    }
    MainActivity.current?.ensureMic()
    Session.connect(context, address, nickname, password)
}

private fun connectFavorite(context: Context, f: Favorite) =
    connect(context, f.address, f.nickname.ifBlank { Settings(context).nickname }, f.password)

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
        object : ConnectActions {
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
    )
}

private object SessionActions : ServerActions {
    override fun join(channel: ServerModel.Channel, password: String) = Session.joinChannel(channel.id, password)
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
    override fun sendPicture(uri: android.net.Uri) {
        MainActivity.current?.let { Session.sendPicture(it.applicationContext, uri) }
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
    override fun shareCamera(front: Boolean, hd: Boolean) {
        MainActivity.current?.shareCamera(front, hd)
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
    override fun addFavorite(f: Favorite) {
        val ctx = MainActivity.current?.applicationContext ?: return
        saveFavorites(ctx, favorites.value.map { if (f.auto) it.copy(auto = false) else it } + f)
    }
}

private class AppSettingsActions(private val context: Context) : SettingsActions {
    override fun save(voice: VoiceSettings, nickname: String) {
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
    override fun exportIdentity() {
        val activity = MainActivity.current ?: return
        val text = Settings(context).identity().export()
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, text)
            .putExtra(android.content.Intent.EXTRA_SUBJECT, "TeamSpeak identity")
        activity.startActivity(android.content.Intent.createChooser(send, "Export the identity"))
    }
    override fun importIdentity(text: String): String? = Settings(context).importIdentity(text)
}
