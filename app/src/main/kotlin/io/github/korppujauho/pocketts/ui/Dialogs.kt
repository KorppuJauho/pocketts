package io.github.korppujauho.pocketts.ui

import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.pocketts.Session
import io.github.korppujauho.pocketts.proto.Chat
import io.github.korppujauho.pocketts.proto.ServerModel
import java.util.Date
import kotlin.math.roundToInt

/** WebTS's person menu: who it is, how loud they are for you, poke, chat; for admins also move, kick and ban. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSheet(
    person: ServerModel.Client,
    server: String,
    isSelf: Boolean,
    volume: Float,
    onVolume: (Float) -> Unit,
    onPoke: () -> Unit,
    onChat: () -> Unit,
    onDismiss: () -> Unit,
    away: Boolean = false,
    awayMessage: String = "",
    onAway: (Boolean, String) -> Unit = { _, _ -> },
    admin: Boolean = false,
    onMove: () -> Unit = {},
    onKick: () -> Unit = {},
    onBan: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Pop.bottom,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetHandle() },
        // Open all the way (with the admin row it's taller than half the screen)
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // Above the phone's navigation buttons, and scrolling on a short screen
        Column(
            Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Who: the picture with a ring, the name, the description and whether they're away
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.border(2.dp, if (isSelf) Ts.youBorder else Ts.accent.copy(alpha = 0.6f), CircleShape).padding(4.dp)) {
                    PersonAvatar(person, server, 68)
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(person.nickname, color = Ts.bright, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 2)
                    if (person.description.isNotEmpty()) Text(person.description, color = Ts.muted, fontSize = 14.sp, maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isSelf) Chip("You", Ts.you)
                        if (person.away) Chip("Away" + if (person.awayMessage.isNotEmpty()) ": ${person.awayMessage}" else "", Ts.afk)
                    }
                }
            }
            if (isSelf) {
                // Away, as the TeamSpeak app sets it: others see AFK and the message
                var msg by remember { mutableStateOf(awayMessage) }
                PopCard {
                    Text("Away", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    TsField(msg, { msg = it.take(80) }, "Away message (optional)")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (away) {
                        PillButton("I'm back", modifier = Modifier.weight(1f)) { onAway(false, ""); onDismiss() }
                        PillButton("Update", PillKind.PRIMARY, modifier = Modifier.weight(1f)) { onAway(true, msg.trim()); onDismiss() }
                    } else {
                        PillButton("Set away", PillKind.PRIMARY, modifier = Modifier.weight(1f)) { onAway(true, msg.trim()); onDismiss() }
                    }
                }
                return@Column
            }
            var v by remember { mutableFloatStateOf(volume) }
            PopCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume for you", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    ValueBadge("${(v * 100).roundToInt()}%")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = null, tint = Ts.muted, modifier = Modifier.size(20.dp))
                    Slider(
                        value = v, onValueChange = { v = (it * 20).roundToInt() / 20f }, onValueChangeFinished = { onVolume(v) },
                        valueRange = 0f..2f, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        colors = SliderDefaults.colors(thumbColor = Ts.accent, activeTrackColor = Ts.accent, inactiveTrackColor = Ts.card),
                    )
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = Ts.muted, modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(androidx.compose.material.icons.Icons.Rounded.NotificationsActive, "Poke", Modifier.weight(1f), onClick = onPoke)
                ActionTile(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Chat, "Chat", Modifier.weight(1f), onClick = onChat)
            }
            if (admin) AdminTiles(onMove, onKick, onBan)
        }
    }
}

/** A small rounded label in its colour. */
@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun ValueBadge(text: String) {
    Text(
        text, color = Ts.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ts.accent.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Writing a poke. */
@Composable
fun PokeDialog(person: ServerModel.Client, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var msg by remember { mutableStateOf("") }
    TsDialog(
        onDismiss = onDismiss, title = "Poke ${person.nickname}", icon = androidx.compose.material.icons.Icons.Rounded.NotificationsActive,
        subtitle = "They get a popup with your message",
        buttons = {
            PillButton("Cancel", onClick = onDismiss)
            PillButton("Poke", PillKind.PRIMARY) { onSend(msg) }
        },
    ) {
        TsField(msg, { msg = it.take(100) }, "Message (optional)")
    }
}

/** Pokes received: a card each until dismissed, the newest last. */
@Composable
fun PokeCards(pokes: List<Chat.Poke>, onDismiss: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (pokes.isEmpty()) return
    val context = LocalContext.current
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in pokes) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ts.card)
                    .border(1.dp, Ts.accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${p.fromName} poked you", color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (p.text.isNotEmpty()) Text(formatted(p.text), color = Ts.text, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeFormat.format(Date(p.time)), color = Ts.faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    SecondaryButton("Dismiss") { onDismiss(p.id) }
                }
            }
        }
    }
}

/** The server's info, like the desktop app: welcome message, users, channels, age, website, version. */
@Composable
fun ServerInfoDialog(state: Session.UiState, onDismiss: () -> Unit) {
    val v = state.server
    val uri = LocalUriHandler.current
    TsDialog(
        onDismiss = onDismiss, title = state.serverName.ifEmpty { "Server info" }, icon = androidx.compose.material.icons.Icons.Rounded.Dns,
        subtitle = state.address,
        buttons = { PillButton("Close", PillKind.PRIMARY, onClick = onDismiss) },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The server's banner: a web address, or a picture in the server's own files (as channel banners)
                val bannerUrl = v["virtualserver_hostbanner_gfx_url"].orEmpty().trim()
                val banner: Any? = when {
                    bannerUrl.startsWith("https://", ignoreCase = true) -> bannerUrl
                    bannerUrl.startsWith("ts3image://", ignoreCase = true) ->
                        io.github.korppujauho.pocketts.proto.Pictures.channelPicture(bannerUrl)?.let { io.github.korppujauho.pocketts.TsFile.picture(state.serverKey, it) }
                    else -> null
                }
                if (banner != null) {
                    val link = v["virtualserver_hostbanner_url"].orEmpty().takeIf { it.startsWith("https://", ignoreCase = true) }
                    coil3.compose.AsyncImage(
                        banner, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(10.dp))
                            .then(if (link != null) Modifier.clickable { uri.openUri(link) } else Modifier),
                    )
                }
                val welcome = v["virtualserver_welcomemessage"].orEmpty().trim()
                if (welcome.isNotEmpty()) InfoBox { Text(formatted(welcome), color = Ts.text, fontSize = 14.sp) }
                val max = v["virtualserver_maxclients"]?.toIntOrNull() ?: 0
                val stats = buildList {
                    add("Users" to if (max > 0) "${state.clientCount} / $max" else "${state.clientCount}")
                    add("Channels" to "${state.channelCount}")
                    v["virtualserver_uptime"]?.toLongOrNull()?.let {
                        val since = v["received_at"]?.toLongOrNull()?.let { at -> System.currentTimeMillis() - at } ?: 0
                        add("Uptime" to span(it * 1000 + since))
                    }
                    v["virtualserver_created"]?.toLongOrNull()?.takeIf { it > 0 }?.let {
                        add("Created" to "${span(System.currentTimeMillis() - it * 1000)} ago")
                    }
                }
                for (row in stats.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((k, value) in row) {
                            InfoBox(Modifier.weight(1f)) {
                                Text(k, color = Ts.muted, fontSize = 12.sp)
                                Text(value, color = Ts.text, fontSize = 15.sp)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                val site = v["virtualserver_hostbutton_url"].orEmpty()
                if (site.startsWith("http://") || site.startsWith("https://")) {
                    InfoBox(Modifier.clickable { uri.openUri(site) }) {
                        Text(v["virtualserver_hostbutton_tooltip"].orEmpty().ifEmpty { site }, color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(site, color = Ts.muted, fontSize = 12.sp)
                    }
                }
                val version = listOf(
                    v["virtualserver_version"].orEmpty().replace(Regex("""\s*\[Build:[^\]]*]""", RegexOption.IGNORE_CASE), ""),
                    v["virtualserver_platform"].orEmpty(),
                ).filter { it.isNotEmpty() }.joinToString(" • ")
                if (version.isNotEmpty()) Text(version, color = Ts.muted, fontSize = 12.sp)
            }
    }
}

@Composable
private fun InfoBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pop.field).padding(horizontal = 14.dp, vertical = 10.dp)) {
        content()
    }
}

/** "5 minutes", "3 hours", "12 days", "4 months", "2 years". */
fun span(ms: Long): String {
    val min = maxOf(0, ms / 60000)
    if (min < 60) return "$min minute${if (min == 1L) "" else "s"}"
    val hours = min / 60
    if (hours < 24) return "$hours hour${if (hours == 1L) "" else "s"}"
    val days = hours / 24
    if (days < 60) return "$days day${if (days == 1L) "" else "s"}"
    val months = (days / 30.44).toLong()
    if (months < 24) return "$months months"
    return "${(days / 365.25).toLong()} years"
}
