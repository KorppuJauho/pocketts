package io.github.korppujauho.mobilets6.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.mobilets6.Session
import io.github.korppujauho.mobilets6.proto.Chat
import io.github.korppujauho.mobilets6.proto.ServerModel

/** What the server screen can ask for. */
interface ServerActions {
    fun join(channel: ServerModel.Channel, password: String = "")
    fun disconnect()
    fun toggleMic()
    fun toggleSound()
    fun openSettings()
    fun send(key: Chat.Key, text: String)
    fun openPrivate(person: ServerModel.Client)
    fun closePrivate(uid: String)
    fun poke(person: ServerModel.Client, text: String)
    fun dismissPoke(id: Long)
    fun setVolume(person: ServerModel.Client, volume: Float)
    fun setPtt(held: Boolean)
    fun sendPicture(uri: android.net.Uri)
    fun setAway(away: Boolean, message: String)
    /** Another server from the favourites (this one is left). */
    fun switchTo(f: io.github.korppujauho.mobilets6.Favorite)
    fun addFavorite(f: io.github.korppujauho.mobilets6.Favorite)
    /** The conversation on screen (null when the chat isn't showing). */
    fun viewing(key: Chat.Key?)
}

/** The two views on a phone: WebTS shows them side by side on a wide screen. */
enum class View { CHANNELS, CHAT }

@Composable
fun ServerScreen(
    state: Session.UiState,
    actions: ServerActions,
    settingsActions: SettingsActions? = null,
    nickname: String = "",
    startView: View = View.CHANNELS,
    startChat: Chat.Key = Chat.Key.Channel(0),
    startSettings: Int? = null,
    favorites: List<io.github.korppujauho.mobilets6.Favorite> = emptyList(),
) {
    var serversMenu by remember { mutableStateOf(false) }
    var addingFavorite by remember { mutableStateOf<io.github.korppujauho.mobilets6.Favorite?>(null) }
    var settings by remember { mutableStateOf(startSettings) }
    var view by rememberSaveable { mutableStateOf(startView) }
    var chatKey by remember { mutableStateOf(startChat) }
    var askPassword by remember { mutableStateOf<ServerModel.Channel?>(null) }
    var person by remember { mutableStateOf<ServerModel.Client?>(null) }
    var pokeTarget by remember { mutableStateOf<ServerModel.Client?>(null) }
    var serverInfo by remember { mutableStateOf(false) }
    val items = remember(state.tree) { flattenTree(state.tree) }

    // A private chat whose tab was closed: back to the channel
    if (chatKey is Chat.Key.Private && state.privateTabs.none { it.uid == (chatKey as Chat.Key.Private).uid }) chatKey = Chat.Key.Channel(0)
    val shownKey = if (chatKey is Chat.Key.Channel) Chat.Key.Channel(state.ownChannel) else chatKey
    LaunchedEffect(view, shownKey) { actions.viewing(if (view == View.CHAT) shownKey else null) }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader {
                // WebTS's menu: the favourites (one tap switches), and adding the server you're on
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.IconButton(onClick = { serversMenu = true }) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Filled.Star, contentDescription = "Servers", tint = Ts.text,
                        )
                    }
                    androidx.compose.material3.DropdownMenu(serversMenu, onDismissRequest = { serversMenu = false }, containerColor = Ts.card) {
                        for (f in favorites) {
                            val here = io.github.korppujauho.mobilets6.Favorites.same(f.address, state.address)
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text((if (here) "● " else "★ ") + f.label, color = Ts.text) },
                                enabled = !here,
                                onClick = { serversMenu = false; actions.switchTo(f) },
                            )
                        }
                        if (favorites.none { io.github.korppujauho.mobilets6.Favorites.same(it.address, state.address) }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("☆ Add to favourites", color = Ts.text) },
                                onClick = {
                                    serversMenu = false
                                    addingFavorite = io.github.korppujauho.mobilets6.Favorite(state.address, state.serverName, nickname)
                                },
                            )
                        }                    }
                }
                ExitButton("Disconnect") { actions.disconnect() }
            }
            Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val ptt = state.voice.activation == io.github.korppujauho.mobilets6.VoiceSettings.Activation.PTT
                ControlsBar(
                    micOff = state.inputMuted || !state.micAllowed, soundOff = state.outputMuted,
                    onMic = actions::toggleMic, onSound = actions::toggleSound, onSettings = { settings = 0 },
                    ptt = if (ptt) ({ PttButton(state.pttHeld, actions::setPtt) }) else null,
                )
                ViewSwitch(view, unread = state.unread.isNotEmpty()) { view = it }
                when (view) {
                    View.CHANNELS -> Panel(Modifier.fillMaxWidth().weight(1f), header = {
                        TsIcon(state.serverIcon, state.serverKey, 18.dp)
                        if (state.serverIcon > 0) androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                        ServerName(state.serverName.ifEmpty { "Connecting to ${state.address}…" }) { serverInfo = true }
                    }) {
                        if (state.phase == Session.Phase.CONNECTING) {
                            Loading(if (state.reconnecting > 0) "Connection lost. Reconnecting… (try ${state.reconnecting})" else "Connecting…")
                        } else {
                            state.error?.let { ErrorLine(it) }
                            ChannelTree(
                                items,
                                TreeContext(state.ownId, state.ownChannel, state.talking, state.serverKey, state.badges) { it.id !in state.cannotTalk },
                                onChannel = { ch ->
                                    if (ch.id == state.ownChannel || ch.spacer != null) return@ChannelTree
                                    if (ch.hasPassword) askPassword = ch else actions.join(ch)
                                },
                                onPerson = { person = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    View.CHAT -> ChatPanel(
                        state, chatKey,
                        onView = { chatKey = it },
                        onCloseTab = actions::closePrivate,
                        onSend = actions::send,
                        onPicture = actions::sendPicture,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
        PokeCards(state.pokes, actions::dismissPoke, Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
        if (state.voice.activation == io.github.korppujauho.mobilets6.VoiceSettings.Activation.PTT && state.voice.floatingPtt &&
            state.phase == Session.Phase.CONNECTED
        ) {
            FloatingPtt(
                state.pttHeld, actions::setPtt,
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (view == View.CHAT) 96.dp else 24.dp),
            )
        }
    }
    settings?.let { tab ->
        if (settingsActions != null) SettingsDialog(
            state.voice, nickname, settingsActions, onDismiss = { settings = null }, startTab = tab,
            self = SelfInfo(state.client(state.ownId), state.serverKey, state.avatarStatus),
        )
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
    person?.let { p ->
        PersonSheet(
            p, state.serverKey, isSelf = p.id == state.ownId, volume = state.volumes[p.uid] ?: 1f,
            onVolume = { actions.setVolume(p, it) },
            onPoke = { person = null; pokeTarget = p },
            onChat = {
                person = null
                actions.openPrivate(p)
                chatKey = Chat.Key.Private(p.uid)
                view = View.CHAT
            },
            onDismiss = { person = null },
            away = state.away, awayMessage = state.awayMessage,
            onAway = { a, m -> actions.setAway(a, m) },
        )
    }
    addingFavorite?.let { f ->
        FavoriteDialog(f, onSave = { actions.addFavorite(it); addingFavorite = null }, onDelete = null) { addingFavorite = null }
    }
    pokeTarget?.let { p ->
        PokeDialog(p, onSend = { actions.poke(p, it); pokeTarget = null }, onDismiss = { pokeTarget = null })
    }
    if (serverInfo) ServerInfoDialog(state) { serverInfo = false }
}

/** "Channels" and "Chat" on a phone. */
@Composable
private fun ViewSwitch(view: View, unread: Boolean, onView: (View) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ts.card)
            .border(1.dp, Ts.hairline, RoundedCornerShape(10.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((v, label) in listOf(View.CHANNELS to "Channels", View.CHAT to "Chat")) {
            val active = v == view
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (active) Color(0x12FFFFFF) else Color.Transparent)
                    .clickable { onView(v) }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = if (active) Ts.bright else Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (v == View.CHAT && unread && !active) {
                    Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(Ts.red))
                }
            }
        }
    }
}

@Composable
fun ErrorLine(text: String) {
    Text(text, color = Color(0xFFF87171), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
