package io.github.korppujauho.mobilets6.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
fun VideoView(
    track: VideoTrack, egl: EglBase, modifier: Modifier = Modifier, mirror: Boolean = false, fill: Boolean = false,
    onSize: ((width: Int, height: Int) -> Unit)? = null,
) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(egl.eglBaseContext, onSize?.let { report ->
                    object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {}
                        override fun onFrameResolutionChanged(w: Int, h: Int, rotation: Int) {
                            if (rotation % 180 == 0) report(w, h) else report(h, w)
                        }
                    }
                })
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
fun StreamPlayer(
    state: StreamEngine.State, title: String, egl: EglBase, onClose: () -> Unit,
    onMute: (Boolean) -> Unit = {}, onSmallWindow: (() -> Unit)? = null,
) {
    val muted = state.watchMuted
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            state.watchVideo?.let { VideoView(it, egl, Modifier.fillMaxSize(), onSize = { w, h -> StreamSize.set(w, h) }) }
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
                    Text(if (muted) "🔇" else "🔊", fontSize = 20.sp, modifier = Modifier.clickable { onMute(!muted) }.padding(6.dp))
                }
                if (onSmallWindow != null) {
                    Icon(
                        Icons.Filled.PictureInPictureAlt, contentDescription = "Small window", tint = Color.White,
                        modifier = Modifier.clickable(onClick = onSmallWindow).padding(6.dp).size(24.dp),
                    )
                }
                Text("×", color = Color.White, fontSize = 28.sp, modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 8.dp))
            }
        }
    }
}

/** WebTS's "Share" window: what (camera or screen), which camera, how good. */
@Composable
fun ShareDialog(
    onCamera: (front: Boolean, hd: Boolean) -> Unit, onScreen: () -> Unit, onDismiss: () -> Unit,
    onBoth: (hd: Boolean) -> Unit = {}, canBoth: Boolean = false,
) {
    var screen by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(0) } // front, back, both
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
                    Segments(if (canBoth) listOf("Front", "Back", "Both") else listOf("Front", "Back"), camera) { camera = it }
                    if (camera == 2) Text("The back camera big, the front one small in the corner. Swap switches them.", color = Ts.muted, fontSize = 13.sp)
                    Text("Quality", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Segments(listOf("Data saver (480p)", "HD (720p)"), if (hd) 1 else 0) { hd = it == 1 }
                } else {
                    Text("Android asks once more before it starts. Everything on your screen is shown, notifications too.", color = Ts.muted, fontSize = 13.sp)
                }
                Text("Everyone in your channel can watch.", color = Ts.muted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    screen -> onScreen()
                    camera == 2 -> onBoth(hd)
                    else -> onCamera(camera == 0, hd)
                }
            }) { Text("Start", color = Ts.accent, fontWeight = FontWeight.SemiBold) }
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

/**
 * WebTS's share bar: a green box with your own picture small (tap: bigger, inside the box, at most half the screen;
 * tap again: small), a live dot with what's shared and how many watch, and round buttons to switch the camera, turn
 * or lock the picture, and Stop.
 */
@Composable
fun ShareBar(state: StreamEngine.State, egl: EglBase, onFlip: () -> Unit, onStop: () -> Unit, onRotateLock: (Boolean) -> Unit = {}) {
    val kind = state.sharing ?: return
    var big by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(9f / 16f) } // the picture's width / height
    val camera = kind == Streams.Kind.CAMERA
    val mirror = camera && state.shareFront && !state.shareBoth
    val track = state.localVideo

    @Composable
    fun Preview(modifier: Modifier) {
        if (track == null) return
        Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))) {
            VideoView(track, egl, Modifier.fillMaxSize(), mirror = mirror, fill = !big, onSize = { w, h -> aspect = w.toFloat() / h })
            // Over the picture (a video surface doesn't take taps itself)
            Box(Modifier.fillMaxSize().clickable { big = !big })
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ShareGreen.copy(alpha = 0.15f))
            .border(1.dp, ShareGreen, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (big) {
            val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                Preview(Modifier.fillMaxWidth().height(minOf(maxWidth / aspect, maxHeight)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!big) Preview(Modifier.size(width = 72.dp, height = minOf(72.dp / aspect, 128.dp)))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot()
                val what = when {
                    !camera -> "Screen"
                    state.shareBoth -> "Both cameras"
                    else -> "Camera"
                }
                Text(
                    if (!state.shareLive) "$what · starting…" else "$what · ${state.viewers} watching",
                    color = Color(0xFF4ADE80), fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                )
            }
            if (camera) {
                RoundButton(
                    Icons.Filled.FlipCameraAndroid, if (state.shareBoth) "Swap the cameras" else "Switch camera", Ts.text, onFlip,
                )
                RoundButton(
                    if (state.rotateLocked) Icons.Filled.ScreenLockRotation else Icons.Filled.ScreenRotation,
                    if (state.rotateLocked) "Turn locked (tap to follow the phone)" else "Turn follows the phone (tap to lock)",
                    if (state.rotateLocked) Color(0xFFFBBF24) else Ts.text,
                ) { onRotateLock(!state.rotateLocked) }
            }
            Text(
                "Stop", color = Color(0xFFF87171), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Ts.redButton.copy(alpha = 0.15f))
                    .border(1.dp, Ts.redButton, RoundedCornerShape(20.dp)).clickable(onClick = onStop)
                    .wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 18.dp),
            )
        }
    }
}

private val ShareGreen = Color(0xFF23A559)

/** WebTS's round share button. */
@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Ts.button).border(1.dp, Color(0x1AFFFFFF), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** The red "live" dot, with a ring that grows and fades as in WebTS. */
@Composable
private fun LiveDot() {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
    val t by pulse.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween<Float>(1600)),
        label = "ring",
    )
    Box(Modifier.size(9.dp).drawBehind {
        val ring = (t / 0.7f).coerceAtMost(1f)
        drawCircle(Color(0xFFEF4444).copy(alpha = 0.6f * (1f - ring)), radius = size.minDimension / 2 + 7.dp.toPx() * ring)
        drawCircle(Color(0xFFEF4444))
    })
}

/** The watched stream's picture size, for the small window's shape. */
object StreamSize {
    @Volatile var width = 16
        private set
    @Volatile var height = 9
        private set
    /** Called when it changes (from the picture's own thread). */
    @Volatile var onChange: (() -> Unit)? = null

    fun set(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || (w == width && h == height)) return
        width = w
        height = h
        onChange?.invoke()
    }
}

/** The app as a small window over other apps: only the stream's picture. */
@Composable
fun PipPlayer(state: StreamEngine.State, egl: EglBase) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        state.watchVideo?.let { VideoView(it, egl, Modifier.fillMaxSize(), onSize = { w, h -> StreamSize.set(w, h) }) }
        val status = if (state.watching == 0) "Stream ended." else state.watchStatus
        if (status.isNotEmpty()) {
            Text(status, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp))
        }
    }
}
