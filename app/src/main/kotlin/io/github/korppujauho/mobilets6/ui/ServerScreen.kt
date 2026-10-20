package io.github.korppujauho.mobilets6.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.mobilets6.Session
import io.github.korppujauho.mobilets6.proto.ServerModel

/** What the server screen can ask for. */
interface ServerActions {
    fun join(channel: ServerModel.Channel, password: String = "")
    fun disconnect()
    fun toggleMic()
    fun toggleSound()
    fun openSettings()
    fun openServerInfo()
    fun openPerson(client: ServerModel.Client)
}

@Composable
fun ServerScreen(state: Session.UiState, actions: ServerActions) {
    var askPassword by remember { mutableStateOf<ServerModel.Channel?>(null) }
    val items = remember(state.tree) { flattenTree(state.tree) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AppHeader { ExitButton("Disconnect") { actions.disconnect() } }
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            ControlsBar(
                micOff = state.inputMuted, soundOff = state.outputMuted,
                onMic = actions::toggleMic, onSound = actions::toggleSound, onSettings = actions::openSettings,
            )
            Panel(Modifier.fillMaxWidth().weight(1f), header = {
                ServerName(state.serverName.ifEmpty { "Connecting to ${state.address}…" }, actions::openServerInfo)
            }) {
                if (state.phase == Session.Phase.CONNECTING) {
                    Loading("Connecting…")
                } else {
                    state.error?.let { ErrorLine(it) }
                    ChannelTree(
                        items,
                        TreeContext(state.ownId, state.ownChannel, state.talking) { it.id !in state.cannotTalk },
                        onChannel = { ch ->
                            if (ch.id == state.ownChannel || ch.spacer != null) return@ChannelTree
                            if (ch.hasPassword) askPassword = ch else actions.join(ch)
                        },
                        onPerson = actions::openPerson,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    askPassword?.let { ch ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPassword = null },
            containerColor = Ts.panel,
            title = { Text(ch.displayName, color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
            text = { TsField(pw, { pw = it }, "Channel password", password = true) },
            confirmButton = { TextButton(onClick = { actions.join(ch, pw); askPassword = null }) { Text("Join", color = Ts.accent) } },
            dismissButton = { TextButton(onClick = { askPassword = null }) { Text("Cancel", color = Ts.muted) } },
        )
    }
}

@Composable
fun ErrorLine(text: String) {
    Text(text, color = androidx.compose.ui.graphics.Color(0xFFF87171), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
