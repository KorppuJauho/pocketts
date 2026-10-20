package io.github.korppujauho.mobilets6.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.korppujauho.mobilets6.StreamEngine
import io.github.korppujauho.mobilets6.proto.Streams
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** WebTS's WATCH button (only for streams in your own channel, as in the desktop app). */
@Composable
fun WatchButton(onClick: () -> Unit) {
    Text(
        "WATCH", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Ts.green).clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A video track on screen. */
@Composable
fun VideoView(track: VideoTrack, egl: EglBase, modifier: Modifier = Modifier, mirror: Boolean = false, fill: Boolean = false) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(egl.eglBaseContext, null)
                setScalingType(if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL else RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                renderer = this
            }
        },
        update = { it.setMirror(mirror) },
        modifier = modifier,
    )
    DisposableEffect(track, renderer) {
        val r = renderer
        if (r != null) track.addSink(r)
        onDispose {
            if (r != null) {
                try { track.removeSink(r) } catch (_: Exception) {}
            }
        }
    }
    DisposableEffect(Unit) { onDispose { renderer?.release() } }
}

/** WebTS's stream player: the picture on the whole screen, the state while it connects, sound, close. */
@Composable
fun StreamPlayer(state: StreamEngine.State, title: String, egl: EglBase, onClose: () -> Unit) {
    var muted by remember { mutableStateOf(false) }
    DisposableEffect(state.watchAudio, muted) {
        state.watchAudio?.setEnabled(!muted)
        onDispose {}
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            state.watchVideo?.let { VideoView(it, egl, Modifier.fillMaxSize()) }
            if (state.watchStatus.isNotEmpty()) {
                Text(
                    state.watchStatus, color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (state.watchAudio != null) {
                    Text(if (muted) "🔇" else "🔊", fontSize = 20.sp, modifier = Modifier.clickable { muted = !muted }.padding(6.dp))
                }
                Text("×", color = Color.White, fontSize = 28.sp, modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 8.dp))
            }
        }
    }
}

/** WebTS's "Share" window: what (camera or screen), which camera, how good. */
@Composable
fun ShareDialog(onCamera: (front: Boolean, hd: Boolean) -> Unit, onScreen: () -> Unit, onDismiss: () -> Unit) {
    var screen by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(true) }
    var hd by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ts.panel,
        title = { Text(if (screen) "Share your screen" else "Share your camera", color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Segments(listOf("Camera", "Screen"), if (screen) 1 else 0) { screen = it == 1 }
                if (!screen) {
                    Text("Camera", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Segments(listOf("Front", "Back"), if (front) 0 else 1) { front = it == 0 }
                    Text("Quality", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Segments(listOf("Data saver (480p)", "HD (720p)"), if (hd) 1 else 0) { hd = it == 1 }
                } else {
                    Text("Android asks once more before it starts. Everything on your screen is shown, notifications too.", color = Ts.muted, fontSize = 13.sp)
                }
                Text("Everyone in your channel can watch.", color = Ts.muted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (screen) onScreen() else onCamera(front, hd) }) { Text("Start", color = Ts.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ts.muted) } },
    )
}

@Composable
private fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ts.card).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            Text(
                o, color = if (i == selected) Ts.bright else Ts.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (i == selected) Color(0x1FFFFFFF) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
            )
        }
    }
}

/** WebTS's share bar: our own picture small, what's shared and to how many, flip and stop. */
@Composable
fun ShareBar(state: StreamEngine.State, egl: EglBase, onFlip: () -> Unit, onStop: () -> Unit) {
    val kind = state.sharing ?: return
    var big by remember { mutableStateOf(false) }
    val mirror = kind == Streams.Kind.CAMERA && state.shareFront
    // Tapping the small picture shows it large: what the others see
    if (big) state.localVideo?.let { track ->
        Dialog(onDismissRequest = { big = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { big = false }) {
                VideoView(track, egl, Modifier.fillMaxSize(), mirror = mirror)
                Row(
                    Modifier.fillMaxWidth().background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (kind == Streams.Kind.CAMERA) "Your camera" else "Your screen",
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f),
                    )
                    if (kind == Streams.Kind.CAMERA) Text("Flip", color = Color.White, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onFlip).padding(8.dp))
                    Text("×", color = Color.White, fontSize = 28.sp, modifier = Modifier.clickable { big = false }.padding(horizontal = 8.dp))
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ts.card)
            .border(1.dp, Ts.green.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.localVideo?.let {
            Box(Modifier.size(width = 72.dp, height = 54.dp).clip(RoundedCornerShape(6.dp))) {
                VideoView(it, egl, Modifier.fillMaxSize(), mirror = mirror, fill = true)
                // Over the picture (a video surface doesn't take taps itself)
                Box(Modifier.fillMaxSize().clickable { big = true })
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (kind == Streams.Kind.CAMERA) "Sharing your camera" else "Sharing your screen",
                color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            )
            Text(
                when {
                    !state.shareLive -> "Starting…"
                    state.viewers == 0 -> "Nobody watching yet"
                    state.viewers == 1 -> "1 watching"
                    else -> "${state.viewers} watching"
                },
                color = Ts.muted, fontSize = 12.sp,
            )
        }
        if (kind == Streams.Kind.CAMERA) SecondaryButton("Flip", onClick = onFlip)
        Text(
            "Stop", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ts.redButton).clickable(onClick = onStop)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
