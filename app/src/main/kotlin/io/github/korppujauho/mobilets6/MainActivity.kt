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

@Composable
private fun App() {
    val state by Session.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    if (state.phase == Session.Phase.IDLE) Connect(state) else ServerScreen(state, SessionActions, AppSettingsActions(ctx), settings.nickname)
}

@Composable
private fun Connect(state: Session.UiState) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    var nickname by remember { mutableStateOf(settings.nickname) }
    var address by remember { mutableStateOf(settings.address) }
    var password by remember { mutableStateOf(settings.serverPassword) }
    ConnectScreen(
        nickname, { nickname = it }, address, { address = it }, password, { password = it }, state.error,
    ) {
        settings.nickname = nickname.trim()
        settings.address = address.trim()
        settings.serverPassword = password
        MainActivity.current?.ensureMic() // asked once; talking starts when it's allowed
        Session.connect(ctx, address, nickname.trim(), password)
    }
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
}
