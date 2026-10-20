package io.github.korppujauho.mobilets6.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.MicExternalOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.mobilets6.proto.ServerModel

/** One channel box in the list: the channel, its people, and how deep it is. */
data class ChannelItem(val node: ServerModel.Node, val depth: Int)

fun flattenTree(nodes: List<ServerModel.Node>, depth: Int = 0, out: MutableList<ChannelItem> = mutableListOf()): List<ChannelItem> {
    for (n in nodes) {
        out.add(ChannelItem(n, depth))
        flattenTree(n.children, depth + 1, out)
    }
    return out
}

/** What the tree needs to know about the people in it, beyond the server's data. */
data class TreeContext(
    val ownId: Int,
    val ownChannel: Int,
    val talking: Set<Int>,
    val canTalk: (ServerModel.Client) -> Boolean,
)

/** The channel tree as WebTS draws it: a box per channel with its people inside, sub-channels indented. */
@Composable
fun ChannelTree(
    items: List<ChannelItem>,
    ctx: TreeContext,
    onChannel: (ServerModel.Channel) -> Unit,
    onPerson: (ServerModel.Client) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.node.channel.id }) { item ->
            ChannelBox(item, ctx, onChannel, onPerson)
        }
    }
}

@Composable
private fun ChannelBox(
    item: ChannelItem,
    ctx: TreeContext,
    onChannel: (ServerModel.Channel) -> Unit,
    onPerson: (ServerModel.Client) -> Unit,
) {
    val ch = item.node.channel
    val own = ch.id == ctx.ownChannel
    val shape = RoundedCornerShape(8.dp)
    var box = Modifier.fillMaxWidth()
    if (item.depth > 0) {
        box = box.padding(start = (item.depth * 15).dp)
    }
    box = if (own) {
        box.shadow(6.dp, shape, ambientColor = Ts.accent.copy(alpha = 0.3f), spotColor = Ts.accent.copy(alpha = 0.3f))
            .border(1.dp, Ts.accent.copy(alpha = 0.5f), shape)
    } else {
        box.border(1.dp, Color(0x05FFFFFF), shape)
    }
    val sub = item.depth > 0
    Column(
        box.clip(shape).background(Ts.card).drawBehind {
            if (sub) drawRect(Color(0x0DFFFFFF), size = Size(2.dp.toPx(), size.height)) // a sub-channel's edge
        },
    ) {
        ChannelTitle(ch) { onChannel(ch) }
        for (c in item.node.clients) PersonRow(c, ctx) { onPerson(c) }
    }
}

@Composable
private fun ChannelTitle(ch: ServerModel.Channel, onClick: () -> Unit) {
    val spacer = ch.spacer
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spacer != null) {
            SpacerTitle(spacer)
            return@Row
        }
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Ts.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            ch.displayName, color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (ch.hasPassword) Icon(Icons.Filled.Lock, contentDescription = "Password", tint = Ts.muted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SpacerTitle(s: ServerModel.Spacer) {
    val line = s.line
    if (line != null) {
        Canvas(Modifier.weight(1f).height(14.dp)) {
            val y = size.height / 2
            val effect = when (line) {
                ServerModel.Spacer.Line.DASHED -> PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                ServerModel.Spacer.Line.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f, 5f))
                ServerModel.Spacer.Line.SOLID -> null
            }
            drawLine(
                Color(0x73_94A3B8), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                pathEffect = effect, cap = StrokeCap.Butt,
            )
        }
        return
    }
    val text = if (s.repeat) s.text.repeat((160 / maxOf(1, s.text.length)) + 1) else s.text
    Text(
        text,
        color = if (s.repeat) Ts.spacerRepeat else Ts.spacer,
        fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
        overflow = if (s.repeat) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = when (s.align) {
            ServerModel.Spacer.Align.LEFT -> TextAlign.Start
            ServerModel.Spacer.Align.CENTER -> TextAlign.Center
            ServerModel.Spacer.Align.RIGHT -> TextAlign.End
        },
        softWrap = false,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun PersonRow(c: ServerModel.Client, ctx: TreeContext, onClick: () -> Unit) {
    val talking = c.id in ctx.talking
    val self = c.id == ctx.ownId
    val bg = if (talking) Ts.talking.copy(alpha = 0.1f) else Ts.row
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).background(bg).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(34.dp).background(if (talking) Ts.talking else Color.Transparent))
        Spacer(Modifier.width(25.dp))
        Avatar(talking)
        Spacer(Modifier.width(8.dp))
        Text(
            c.nickname,
            color = if (talking) Ts.talking else Ts.username,
            fontWeight = FontWeight.Medium, fontSize = 13.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
        Row(
            Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                c.outputMuted || !c.outputHardware ->
                    Icon(Icons.Filled.HeadsetOff, "Sound off", tint = Ts.red, modifier = Modifier.size(14.dp))
                c.inputMuted || !c.inputHardware ->
                    Icon(Icons.Filled.MicOff, "Microphone off", tint = Ts.red, modifier = Modifier.size(14.dp))
                !ctx.canTalk(c) ->
                    Icon(Icons.Outlined.MicExternalOff, "Can't talk in this channel", tint = Ts.amber, modifier = Modifier.size(15.dp))
            }
            if (c.away) Tag("AFK", Ts.afk, Ts.afkBg, Ts.afkBorder)
            if (self) Tag("YOU", Ts.you, Ts.afkBg.copy(alpha = 0f), Ts.youBorder)
        }
    }
}

@Composable
fun Avatar(talking: Boolean, size: Int = 24) {
    val ring = if (talking) Modifier.border(2.dp, Ts.green, CircleShape) else Modifier
    Box(
        Modifier.size(size.dp).then(ring).clip(CircleShape).background(Color(0xFF3F4147)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF8E9297), modifier = Modifier.size((size * 0.95).dp))
    }
}

@Composable
fun Tag(text: String, color: Color, bg: Color, border: Color) {
    Text(
        text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
