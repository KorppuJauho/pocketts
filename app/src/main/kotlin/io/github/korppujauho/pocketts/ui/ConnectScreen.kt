package io.github.korppujauho.pocketts.ui

import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.pocketts.Favorite

/** What the connect screen can ask for. */
interface ConnectActions {
    fun connect(address: String, nickname: String, password: String, remember: Boolean, favorite: Boolean)
    fun connectFavorite(f: Favorite)
    fun saveFavorite(old: Favorite?, new: Favorite)
    fun removeFavorite(f: Favorite)
    fun toggleAuto(f: Favorite)
}

/** WebTS's connect window: the favourites (one tap connects), then an address to type. */
@Composable
fun ConnectScreen(
    nickname: String,
    address: String,
    password: String,
    favorites: List<Favorite>,
    error: String?,
    actions: ConnectActions,
    voice: io.github.korppujauho.pocketts.VoiceSettings = io.github.korppujauho.pocketts.VoiceSettings(),
    settingsActions: SettingsActions? = null,
    onDismissError: () -> Unit = {},
) {
    var settings by remember { mutableStateOf(false) }
    var nick by remember { mutableStateOf(nickname) }
    var addr by remember { mutableStateOf(address) }
    var pw by remember { mutableStateOf(password) }
    var remember by remember { mutableStateOf(password.isNotEmpty()) }
    var favorite by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Favorite?>(null) }
    val known = favorites.any { io.github.korppujauho.pocketts.Favorites.same(it.address, addr) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        AppHeader { if (settingsActions != null) SettingsGear { settings = true } }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            if (favorites.isNotEmpty()) {
                Panel(Modifier.fillMaxWidth(), header = {
                    Text("Favourites", color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (f in favorites) FavoriteRow(f, actions, onEdit = { editing = f })
                    }
                }
            }
            Panel(Modifier.fillMaxWidth(), header = {
                Text("Connect to a server", color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TsField(nick, { nick = it }, "Nickname")
                    TsField(addr, { addr = it }, "Server address", placeholder = "ts.example.com", keyboard = KeyboardType.Uri)
                    TsField(pw, { pw = it }, "Server password", password = true, imeAction = ImeAction.Go)
                    CheckRow("Remember the password", remember) { remember = it }
                    // Always there; ticked (and fixed) for a server that already is a favourite
                    CheckRow(if (known) "In favourites" else "Save to favourites", favorite || known, enabled = !known) { favorite = it }
                    error?.let { WarningBanner(it, onDismissError, inset = 0.dp) }
                    PrimaryButton(
                        "Connect", enabled = nick.isNotBlank() && addr.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                    ) { actions.connect(addr.trim(), nick.trim(), pw, remember, favorite && !known) }
                }
            }
        }
    }

    if (settings && settingsActions != null) {
        SettingsDialog(voice, nick, settingsActions, onDismiss = { settings = false })
    }
    editing?.let { f ->
        FavoriteDialog(f, onSave = { actions.saveFavorite(f, it); editing = null }, onDelete = { actions.removeFavorite(f); editing = null }) {
            editing = null
        }
    }
}

@Composable
private fun FavoriteRow(f: Favorite, actions: ConnectActions, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Ts.card)
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
                .clickable { actions.connectFavorite(f) }.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(f.label, color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FavButton(androidx.compose.material.icons.Icons.Rounded.Edit, "Edit", onEdit)
        FavButton(androidx.compose.material.icons.Icons.Rounded.Close, "Remove") { actions.removeFavorite(f) }
    }
}

@Composable
private fun FavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Ts.card)
            .border(1.dp, Ts.buttonBorder, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = label, tint = Ts.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked, onChange, enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Ts.accent, uncheckedColor = Ts.muted, disabledCheckedColor = Ts.accent.copy(alpha = 0.5f)),
        )
        Text(text, color = if (enabled) Ts.text else Ts.muted, fontSize = 14.sp)
    }
}

/** WebTS's "Edit favourite": its name, address, the nickname used there, its password, connect automatically. */
@Composable
fun FavoriteDialog(f: Favorite, onSave: (Favorite) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(f.name) }
    var address by remember { mutableStateOf(f.address) }
    var nick by remember { mutableStateOf(f.nickname) }
    var pw by remember { mutableStateOf(f.password) }
    var auto by remember { mutableStateOf(f.auto) }
    var filePort by remember { mutableStateOf(f.filePort?.toString().orEmpty()) }
    var advanced by remember { mutableStateOf(f.filePort != null) }
    TsDialog(
        onDismiss = onDismiss, title = if (onDelete != null) "Edit favourite" else "Add to favourites",
        icon = androidx.compose.material.icons.Icons.Rounded.Star, tint = Ts.amber, subtitle = f.address.takeIf { onDelete != null },
        // Delete on the left of the same row as Cancel and Save
        buttons = {
            if (onDelete != null) PillButton("Delete", PillKind.DANGER_TEXT, onClick = onDelete)
            Spacer(Modifier.weight(1f))
            PillButton("Cancel", onClick = onDismiss)
            PillButton("Save", PillKind.PRIMARY, enabled = address.isNotBlank()) {
                onSave(Favorite(address.trim(), name.trim(), nick.trim(), pw, auto, filePort.toIntOrNull()?.takeIf { it in 1..65535 }))
            }
        },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TsField(name, { name = it }, "Name in your lists", placeholder = address)
                TsField(address, { address = it }, "Server address", keyboard = KeyboardType.Uri)
                TsField(nick, { nick = it }, "Your nickname on this server")
                TsField(pw, { pw = it }, "Server password", password = true)
                CheckRow("Connect automatically when the app opens", auto) { auto = it }
                // Advanced: folded away unless used
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { advanced = !advanced }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Advanced", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Icon(
                        if (advanced) androidx.compose.material.icons.Icons.Rounded.ExpandLess else androidx.compose.material.icons.Icons.Rounded.ExpandMore,
                        contentDescription = null, tint = Ts.muted,
                    )
                }
                if (advanced) {
                    TsField(
                        filePort, { v -> filePort = v.filter { it.isDigit() }.take(5) }, "File transfer port",
                        placeholder = "The server's own (usually 30033)", keyboard = KeyboardType.Number,
                    )
                    Text("Only if the server's pictures and avatars don't load: its file transfer is on another port.", color = Ts.muted, fontSize = 12.sp)
                }
            }
    }
}
