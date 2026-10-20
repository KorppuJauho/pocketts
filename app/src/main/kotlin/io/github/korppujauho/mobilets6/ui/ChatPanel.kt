package io.github.korppujauho.mobilets6.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.mobilets6.Session
import io.github.korppujauho.mobilets6.proto.Chat
import io.github.korppujauho.mobilets6.proto.MessageText
import java.util.Date

/** The chat as WebTS shows it: tabs (the channel, the server, private chats), the lines, and the message box. */
@Composable
fun ChatPanel(
    state: Session.UiState,
    view: Chat.Key,
    onView: (Chat.Key) -> Unit,
    onCloseTab: (String) -> Unit,
    onSend: (Chat.Key, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier, header = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val channelKey = Chat.Key.Channel(state.ownChannel)
            ChatTab("#", state.ownChannelName.ifEmpty { "Channel" }, view is Chat.Key.Channel, channelKey in state.unread) { onView(channelKey) }
            ChatTab("", "Server", view == Chat.Key.Server, Chat.Key.Server in state.unread) { onView(Chat.Key.Server) }
            for (t in state.privateTabs) {
                val key = Chat.Key.Private(t.uid)
                ChatTab("@", t.name, view == key, key in state.unread, onClose = { onCloseTab(t.uid) }) { onView(key) }
            }
        }
    }) {
        val key = if (view is Chat.Key.Channel) Chat.Key.Channel(state.ownChannel) else view
        val entries = state.chat[key].orEmpty()
        Messages(entries, emptyText = when (key) {
            is Chat.Key.Channel -> "No messages in this channel yet."
            is Chat.Key.Server -> "No messages to the whole server yet."
            is Chat.Key.Private -> "No messages yet."
        })
        val target = when (key) {
            is Chat.Key.Channel -> state.ownChannelName.ifEmpty { "the channel" }
            is Chat.Key.Server -> "everyone on the server"
            is Chat.Key.Private -> state.privateTabs.firstOrNull { it.uid == key.uid }?.name ?: "them"
        }
        MessageBox("Message to $target…", enabled = state.phase == Session.Phase.CONNECTED) { onSend(key, it) }
    }
}

@Composable
private fun ChatTab(prefix: String, name: String, active: Boolean, unread: Boolean, onClose: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (active) Color(0x12FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (prefix.isNotEmpty()) Text(prefix, color = Ts.muted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(name, color = if (active) Ts.bright else Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
        if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(Ts.red))
        if (onClose != null) Text("×", color = Ts.muted, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 2.dp))
    }
}

@Composable
private fun ColumnScope.Messages(entries: List<Chat.Entry>, emptyText: String) {
    val list = rememberLazyListState()
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) list.animateScrollToItem(entries.size - 1) }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
            Text(emptyText, color = Ts.muted, fontSize = 14.sp)
        }
        return
    }
    val context = LocalContext.current
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        state = list,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.id }) { e ->
            val time = timeFormat.format(Date(e.time))
            if (e.event != null) EventLine(time, e.event!!) else MessageLine(time, e)
        }
    }
}

@Composable
private fun EventLine(time: String, parts: List<Chat.Part>) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Ts.faint, fontSize = 12.sp)) { append(time) }
            append("  ")
            for (p in parts) {
                if (p.bold) withStyle(SpanStyle(color = Ts.text, fontWeight = FontWeight.SemiBold)) { append(p.text) }
                else append(p.text)
            }
        },
        color = Ts.event, fontSize = 13.5.sp,
        modifier = Modifier.padding(start = 12.dp, end = 10.dp),
    )
}

@Composable
private fun MessageLine(time: String, e: Chat.Entry) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Ts.faint, fontSize = 12.sp)) { append(time) }
            append("  ")
            withStyle(SpanStyle(color = Ts.sender, fontWeight = FontWeight.Bold)) { append(e.senderName.ifEmpty { "System" } + ":") }
            append("  ")
            append(formatted(e.text))
        },
        color = Ts.text, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0x05FFFFFF))
            .drawBehind { drawLine(Ts.accent, Offset(1f, 0f), Offset(1f, size.height), strokeWidth = 2.dp.toPx()) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A message's styles and links (tapping a link opens it in the browser). */
fun formatted(text: String): AnnotatedString = buildAnnotatedString {
    for (s in MessageText.parse(text)) {
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = when {
                s.underline && s.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                s.underline -> TextDecoration.Underline
                s.strike -> TextDecoration.LineThrough
                else -> null
            },
        )
        val link = s.link
        if (link != null) {
            withLink(LinkAnnotation.Url(link, TextLinkStyles(SpanStyle(color = Ts.talking, textDecoration = TextDecoration.Underline)))) {
                withStyle(style) { append(s.text) }
            }
        } else {
            withStyle(style) { append(s.text) }
        }
    }
}

@Composable
private fun MessageBox(placeholder: String, enabled: Boolean, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun send() {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(
        Modifier.fillMaxWidth().background(Ts.panel).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Ts.input).padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            if (text.isEmpty()) Text(placeholder, color = Color(0xFF8E9297), fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value = text, onValueChange = { text = it }, enabled = enabled,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Ts.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(if (text.isNotBlank()) Ts.accent else Ts.input)
                .clickable(enabled = enabled && text.isNotBlank()) { send() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (text.isNotBlank()) Color.White else Ts.muted)
        }
    }
}
