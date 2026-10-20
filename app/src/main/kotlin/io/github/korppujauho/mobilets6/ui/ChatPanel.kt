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
import io.github.korppujauho.mobilets6.TsFile
import io.github.korppujauho.mobilets6.proto.Pictures
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
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
    onPicture: (Uri) -> Unit = {},
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
            is Chat.Key.Private -> "No messages yet."
        }, state)
        val target = when (key) {
            is Chat.Key.Channel -> state.ownChannelName.ifEmpty { "the channel" }
            is Chat.Key.Private -> state.privateTabs.firstOrNull { it.uid == key.uid }?.name ?: "them"
        }
        MessageBox(
            "Message to $target…", enabled = state.phase == Session.Phase.CONNECTED,
            pictures = key is Chat.Key.Channel, sendingPicture = state.sendingPicture, onPicture = onPicture,
        ) { onSend(key, it) }
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
private fun ColumnScope.Messages(entries: List<Chat.Entry>, emptyText: String, state: Session.UiState) {
    var viewing by remember { mutableStateOf<Any?>(null) }
    viewing?.let { PictureViewer(it) { viewing = null } }
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
            if (e.event != null) EventLine(time, e.event!!) else MessageLine(time, e, state) { viewing = it }
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
private fun MessageLine(time: String, e: Chat.Entry, state: Session.UiState, onPicture: (Any) -> Unit) {
    val (pictures, rest) = remember(e.text) { Pictures.channelPictures(e.text) }
    val text = remember(rest) { formatted(rest) }
    val sender = state.clientByUid(e.senderUid) ?: state.client(e.senderId)
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0x05FFFFFF))
            .drawBehind { drawLine(Ts.accent, Offset(1f, 0f), Offset(1f, size.height), strokeWidth = 2.dp.toPx()) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Ts.faint, fontSize = 12.sp)) { append(time) }
                append("  ")
                appendInlineContent("avatar", "•")
                append(" ")
                withStyle(SpanStyle(color = Ts.sender, fontWeight = FontWeight.Bold)) { append(e.senderName.ifEmpty { "System" } + ":") }
                append("  ")
                append(text)
            },
            inlineContent = mapOf(
                "avatar" to InlineTextContent(Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)) {
                    PersonAvatar(sender, state.serverKey, 20)
                },
            ),
            color = Ts.text, fontSize = 15.sp,
        )
        // Pictures from the channel's files, as the TeamSpeak app shows them
        for (p in pictures) {
            val file = TsFile.picture(state.serverKey, p)
            AsyncImage(
                file, contentDescription = p.name, contentScale = ContentScale.Fit,
                modifier = Modifier.heightIn(max = 260.dp).clip(RoundedCornerShape(6.dp)).clickable { onPicture(file) },
            )
        }
        // A preview of the first link: a direct picture, else a small "Open" card
        val link = remember(rest) { MessageText.parse(rest).firstNotNullOfOrNull { it.link } }
        if (link != null) {
            if (Regex("""\.(jpe?g|gif|png|webp)(\?.*)?$""", RegexOption.IGNORE_CASE).containsMatchIn(link)) {
                AsyncImage(
                    link, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.heightIn(max = 260.dp).clip(RoundedCornerShape(6.dp)).clickable { onPicture(link) },
                )
            } else {
                val host = remember(link) { runCatching { java.net.URI(link).host }.getOrNull() }
                if (host != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(Ts.card).clickable { uri.openUri(link) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = Ts.muted, modifier = Modifier.size(16.dp))
                        Text("Open: $host", color = Ts.text, fontSize = 13.sp)
                    }
                }
            }
        }
    }
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
private fun MessageBox(
    placeholder: String,
    enabled: Boolean,
    pictures: Boolean,
    sendingPicture: Boolean,
    onPicture: (Uri) -> Unit,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) pending = uri }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pending = photoUri }
    pending?.let { uri ->
        Row(
            Modifier.fillMaxWidth().background(Ts.panel).padding(horizontal = 12.dp).padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)))
            Text("Picture ready to send", color = Ts.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PrimaryButton("Send") { onPicture(uri); pending = null }
            Text("×", color = Ts.muted, fontSize = 22.sp, modifier = Modifier.clickable { pending = null }.padding(horizontal = 4.dp))
        }
    }
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
        if (pictures) Box {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Ts.input)
                    .clickable(enabled = enabled && !sendingPicture) { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                if (sendingPicture) CircularProgressIndicator(color = Ts.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Icon(Icons.Filled.Image, contentDescription = "Send a picture", tint = Ts.text)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Ts.card) {
                DropdownMenuItem(text = { Text("Choose a picture", color = Ts.text) }, onClick = {
                    menu = false
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                DropdownMenuItem(text = { Text("Take a photo", color = Ts.text) }, onClick = {
                    menu = false
                    val dir = java.io.File(context.cacheDir, "photos").apply { mkdirs() }
                    val file = java.io.File(dir, "photo-${System.currentTimeMillis()}.jpg")
                    photoUri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    camera.launch(photoUri!!)
                })
            }
        }
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
