package io.github.korppujauho.pocketts.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.korppujauho.pocketts.R
import io.github.korppujauho.pocketts.Session
import io.github.korppujauho.pocketts.proto.FileTransfer
import io.github.korppujauho.pocketts.proto.ServerModel

/** Android's Save window for a file: its suggested name and type in, where the person chose out (null: cancelled). */
private class SaveAs : androidx.activity.result.contract.ActivityResultContract<Pair<String, String>, android.net.Uri?>() {
    override fun createIntent(context: android.content.Context, input: Pair<String, String>) =
        android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(android.content.Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: android.content.Intent?) = intent?.data?.takeIf { resultCode == android.app.Activity.RESULT_OK }
}

private fun mimeOf(name: String) =
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

private fun iconOf(e: FileTransfer.Entry): ImageVector = when {
    e.folder -> Icons.Rounded.Folder
    else -> when (mimeOf(e.name).substringBefore('/')) {
        "image" -> Icons.Rounded.Image
        "video" -> Icons.Rounded.Movie
        "audio" -> Icons.Rounded.AudioFile
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

private fun inside(path: String, name: String) = if (path == "/") "/$name" else "$path/$name"

/**
 * A channel's files, as the TeamSpeak app's file browser: folders first, then files, with their sizes and when they
 * were changed. Tapping a folder opens it (back goes up), tapping a file saves it where the person chooses. What may
 * be seen is the server's to decide; a password channel needs its remembered password.
 */
@Composable
fun FileBrowser(
    channel: ServerModel.Channel,
    download: Session.FileDownload?,
    downloadResult: String?,
    list: (path: String, done: (Result<List<FileTransfer.Entry>>) -> Unit) -> Unit,
    onDownload: (path: String, target: android.net.Uri) -> Unit,
    onCancelDownload: () -> Unit,
    onResultSeen: () -> Unit,
    onDismiss: () -> Unit,
    startPath: String = "/",
) {
    var path by rememberSaveable { mutableStateOf(startPath) }
    var reload by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<FileTransfer.Entry>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<String?>(null) } // the file whose place is being chosen
    val save = rememberLauncherForActivityResult(SaveAs()) { target ->
        val p = saving
        saving = null
        if (target != null && p != null) onDownload(p, target)
    }
    LaunchedEffect(path, reload) {
        val asked = path
        entries = null
        problem = null
        list(asked) { result ->
            // An answer for a folder no longer open is dropped
            if (path != asked) return@list
            result.onSuccess { found -> entries = found.sortedWith(compareBy({ !it.folder }, { it.name.lowercase() })) }
                .onFailure { problem = it.message.orEmpty() }
        }
    }
    LaunchedEffect(downloadResult) {
        if (downloadResult != null) {
            kotlinx.coroutines.delay(5000)
            onResultSeen()
        }
    }
    fun up() {
        path = path.substringBeforeLast('/').ifEmpty { "/" }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Back goes up a folder, and closes at the top (inside the window: it has its own back)
        BackHandler { if (path != "/") up() else onDismiss() }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)
                .clip(RoundedCornerShape(12.dp)).background(Ts.panel),
        ) {
            Row(Modifier.fillMaxWidth().background(Ts.card).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.files_title), color = Ts.bright, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(channel.displayName.ifEmpty { channel.name }, color = Ts.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    Icons.Rounded.Refresh, contentDescription = stringResource(R.string.files_refresh), tint = Ts.muted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { reload++ }.padding(6.dp),
                )
                Icon(
                    Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Ts.muted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onDismiss).padding(6.dp),
                )
            }
            // Where we are; the arrow goes up a folder
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (path != "/") {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.files_up), tint = Ts.text,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { up() }.padding(8.dp),
                    )
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Text(path, color = Ts.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val shown = entries
                when {
                    problem != null -> Message(problem.orEmpty())
                    shown == null -> CircularProgressIndicator(color = Ts.accent, modifier = Modifier.align(Alignment.Center))
                    shown.isEmpty() -> Message(stringResource(R.string.files_empty))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { (if (it.folder) "d:" else "f:") + it.name }) { e ->
                            FileRow(e) {
                                if (e.folder) {
                                    path = inside(path, e.name)
                                } else if (download == null) {
                                    saving = inside(path, e.name)
                                    save.launch(e.name to mimeOf(e.name))
                                }
                            }
                        }
                    }
                }
            }
            // A download under way, or how the last one went
            if (download != null) {
                Column(Modifier.fillMaxWidth().background(Ts.card).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.files_downloading, download.name), color = Ts.text, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        PillButton(stringResource(R.string.cancel), onClick = onCancelDownload)
                    }
                    if (download.size > 0) {
                        LinearProgressIndicator(
                            progress = { download.done.toFloat() / download.size }, color = Ts.accent, trackColor = Ts.panel,
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    } else {
                        LinearProgressIndicator(color = Ts.accent, trackColor = Ts.panel, modifier = Modifier.fillMaxWidth().height(4.dp))
                    }
                }
            } else if (downloadResult != null) {
                Text(
                    downloadResult, color = Ts.text, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().background(Ts.card).clickable(onClick = onResultSeen).padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Message(text: String) {
    Text(text, color = Ts.muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
}

@Composable
private fun FileRow(e: FileTransfer.Entry, onClick: () -> Unit) {
    val context = LocalContext.current
    val time = remember(e.time) {
        if (e.time > 0) java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(e.time)) else ""
    }
    val details = if (e.folder) time else listOf(android.text.format.Formatter.formatShortFileSize(context, e.size), time).filter { it.isNotEmpty() }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(iconOf(e), contentDescription = if (e.folder) stringResource(R.string.files_folder) else null, tint = if (e.folder) Ts.accent else Ts.muted, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, color = Ts.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) Text(details, color = Ts.muted, fontSize = 12.sp)
        }
        if (!e.folder) Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.files_download), tint = Ts.muted, modifier = Modifier.size(22.dp))
    }
}
