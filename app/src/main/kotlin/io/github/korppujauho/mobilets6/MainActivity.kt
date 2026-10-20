package io.github.korppujauho.mobilets6

import android.Manifest
import android.os.Build
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.start()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
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
}

@Composable
private fun App() {
    val state by Session.state.collectAsState()
    if (state.phase == Session.Phase.IDLE) Connect(state) else ServerScreen(state, SessionActions)
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
        Session.connect(ctx, address, nickname.trim(), password)
    }
}

private object SessionActions : ServerActions {
    override fun join(channel: ServerModel.Channel, password: String) = Session.joinChannel(channel.id, password)
    override fun disconnect() = Session.disconnect()
    override fun toggleMic() = Session.toggleMic()
    override fun toggleSound() = Session.toggleSound()
    override fun openSettings() {}
    override fun send(key: Chat.Key, text: String) = Session.send(key, text)
    override fun openPrivate(person: ServerModel.Client) = Session.openPrivate(person)
    override fun closePrivate(uid: String) = Session.closePrivate(uid)
    override fun poke(person: ServerModel.Client, text: String) = Session.poke(person, text)
    override fun dismissPoke(id: Long) = Session.dismissPoke(id)
    override fun setVolume(person: ServerModel.Client, volume: Float) = Session.setVolume(person, volume)
    override fun viewing(key: Chat.Key?) {
        Session.viewing = key
    }
}
