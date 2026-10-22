package io.github.korppujauho.pocketts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.pocketts.Session
import io.github.korppujauho.pocketts.proto.ServerModel

/** Moving someone: the server's channels as a tree, tap one. Spacers only when they may be joined (Settings). */
@Composable
fun MoveDialog(
    person: ServerModel.Client,
    tree: List<ServerModel.Node>,
    spacers: Boolean,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember(tree, spacers) {
        buildList {
            fun add(nodes: List<ServerModel.Node>, depth: Int) {
                for (n in nodes) {
                    if (spacers || n.channel.spacer == null) add(n.channel to depth)
                    add(n.children, depth + 1)
                }
            }
            add(tree, 0)
        }
    }
    TsDialog(
        onDismiss = onDismiss, title = "Move ${person.nickname}", icon = Icons.Rounded.SwapVert,
        subtitle = "Choose the channel",
        buttons = { PillButton("Cancel", onClick = onDismiss) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            for ((channel, depth) in rows) {
                val here = channel.id == person.channel
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !here) { onMove(channel.id) }
                        .padding(start = (8 + depth * 16).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        channel.displayName.ifBlank { channel.name }, color = if (here) Ts.muted else Ts.text, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (here) Text("Here now", color = Ts.muted, fontSize = 12.sp)
                    if (channel.hasPassword) Icon(Icons.Rounded.Lock, contentDescription = "Has a password", tint = Ts.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Kicking someone out of their channel or off the server, with a reason they see. */
@Composable
fun KickDialog(person: ServerModel.Client, onKick: (fromServer: Boolean, reason: String) -> Unit, onDismiss: () -> Unit) {
    var fromServer by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    TsDialog(
        onDismiss = onDismiss, title = "Kick ${person.nickname}", icon = Icons.AutoMirrored.Rounded.Logout, tint = Ts.red,
        subtitle = "They see the reason",
        buttons = {
            PillButton("Cancel", onClick = onDismiss)
            PillButton("Kick", PillKind.DANGER) { onKick(fromServer, reason) }
        },
    ) {
        Choice("From the channel (into the default channel)", !fromServer) { fromServer = false }
        Choice("From the server", fromServer) { fromServer = true }
        TsField(reason, { reason = it.take(Session.REASON_MAX) }, "Reason (optional)")
    }
}

/** Banning someone for a while or for good: their identity and address, and the server removes them at once. */
@Composable
fun BanDialog(person: ServerModel.Client, onBan: (seconds: Long, reason: String) -> Unit, onDismiss: () -> Unit) {
    var seconds by remember { mutableLongStateOf(3600L) }
    var reason by remember { mutableStateOf("") }
    TsDialog(
        onDismiss = onDismiss, title = "Ban ${person.nickname}", icon = Icons.Rounded.Block, tint = Ts.red,
        subtitle = "Their identity and IP address are banned, and they're removed from the server",
        buttons = {
            PillButton("Cancel", onClick = onDismiss)
            PillButton("Ban", PillKind.DANGER) { onBan(seconds, reason) }
        },
    ) {
        for ((label, s) in BAN_TIMES) Choice(label, seconds == s) { seconds = s }
        TsField(reason, { reason = it.take(Session.REASON_MAX) }, "Reason (optional)")
    }
}

/** How long a ban lasts: 0 is for good. */
private val BAN_TIMES = listOf("10 minutes" to 600L, "1 hour" to 3600L, "1 day" to 86_400L, "1 week" to 604_800L, "Permanently" to 0L)

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = Ts.accent, unselectedColor = Ts.muted))
        }
        Text(text, color = Ts.text, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** The person menu's admin row (Settings › UI › Show admin functions). */
@Composable
fun AdminTiles(onMove: () -> Unit, onKick: () -> Unit, onBan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Admin", color = Ts.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.Rounded.SwapVert, "Move", Modifier.weight(1f), onClick = onMove)
            ActionTile(Icons.AutoMirrored.Rounded.Logout, "Kick", Modifier.weight(1f), tint = Ts.red, onClick = onKick)
            ActionTile(Icons.Rounded.Block, "Ban", Modifier.weight(1f), tint = Ts.red, onClick = onBan)
        }
    }
}
