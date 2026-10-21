package io.github.korppujauho.pocketts.ui

import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Visibility
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
import io.github.korppujauho.pocketts.StreamEngine
import io.github.korppujauho.pocketts.proto.Streams
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
    turn: Int = 0, // more turn (clockwise) for what's shown here only
) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val turnNow = androidx.compose.runtime.rememberUpdatedState(turn)
    val report = androidx.compose.runtime.rememberUpdatedState(onSize)
    var shape by remember { mutableStateOf<Float?>(null) } // the picture's width / height, as shown
    Box(modifier, contentAlignment = Alignment.Center) {
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(egl.eglBaseContext, object : RendererCommon.RendererEvents {
                    override fun onFirstFrameRendered() {}
                    override fun onFrameResolutionChanged(w: Int, h: Int, rotation: Int) {
                        io.github.korppujauho.pocketts.AppLog.log("Video ${w}x$h turned $rotation")
                        val (sw, sh) = if (rotation % 180 == 0) w to h else h to w
                        if (sw > 0 && sh > 0) shape = sw.toFloat() / sh
                        report.value?.invoke(sw, sh)
                    }
                })
                setScalingType(if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL else RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                tag = fill
                setEnableHardwareScaler(true)
                setMirror(mirror)
                renderer = this
            }
        },
        update = {
            it.setMirror(mirror)
            // Small (filled) and big (whole picture) differ: the surface is sized for the way it's drawn, so a
            // change must re-size it, or the picture stays stretched to the old shape
            if (it.tag != fill) {
                it.tag = fill
                it.setScalingType(if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL else RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                it.requestLayout()
            }
        },
        modifier = shape.let { a -> if (fill || a == null) Modifier.fillMaxSize() else Modifier.aspectRatio(a) },
    )
    }
    DisposableEffect(track, renderer) {
        val r = renderer
        // The same pictures with the extra turn as their label (the renderer turns them)
        val sink = r?.let {
            org.webrtc.VideoSink { f ->
                val t = turnNow.value
                if (t == 0) it.onFrame(f) else it.onFrame(org.webrtc.VideoFrame(f.buffer, (f.rotation + t) % 360, f.timestampNs))
            }
        }
        if (sink != null) track.addSink(sink)
        onDispose {
            if (sink != null) {
                try { track.removeSink(sink) } catch (_: Exception) {}
            }
        }
    }
    DisposableEffect(Unit) { onDispose { renderer?.release() } }
}

/**
 * Watching someone's stream: a box like the large preview of your own share, the picture at most half the screen,
 * with its sound (on, off and how loud), a small window over other apps, full screen, and close. Full screen turns
 * the app the way the picture is: a wide picture goes sideways, a tall one stays upright.
 */
@Composable
fun WatchCard(
    state: StreamEngine.State, title: String, egl: EglBase, onClose: () -> Unit,
    onMute: (Boolean) -> Unit = {}, onVolume: (Float) -> Unit = {}, onSmallWindow: (() -> Unit)? = null,
) {
    var aspect by remember { mutableStateOf(16f / 9f) }
    var full by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(false) }
    val video = state.watchVideo
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ts.accent.copy(alpha = 0.12f))
            .border(1.dp, Ts.accent.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(minOf(maxWidth / aspect, maxHeight)).clip(RoundedCornerShape(8.dp)).background(Color.Black)
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp)),
            ) {
                if (video != null && !full) {
                    VideoView(video, egl, Modifier.fillMaxSize(), onSize = { w, h -> aspect = w.toFloat() / h; StreamSize.set(w, h) })
                }
                if (state.watchStatus.isNotEmpty()) {
                    Text(
                        state.watchStatus, color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveDot()
            Text(
                title, color = Ts.sender, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (state.watchAudio != null) {
                RoundButton(
                    if (state.watchMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Sound",
                    if (state.watchMuted) Ts.red else Ts.text,
                ) { sound = !sound }
            }
            if (onSmallWindow != null) RoundButton(Icons.Filled.PictureInPictureAlt, "Small window", Ts.text, onSmallWindow)
            RoundButton(Icons.Filled.Fullscreen, "Full screen", Ts.text) { full = true }
            RoundButton(Icons.Filled.Close, "Stop watching", Ts.text, onClose)
        }
        if (sound && state.watchAudio != null) SoundRow(state, onMute, onVolume)
    }
    if (full && video != null) FullScreenPlayer(state, video, title, egl, aspect, onMute) { full = false }
}

/** The stream's sound: on or off, and how loud. */
@Composable
private fun SoundRow(state: StreamEngine.State, onMute: (Boolean) -> Unit, onVolume: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundButton(
            if (state.watchMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            if (state.watchMuted) "Sound on" else "Sound off", if (state.watchMuted) Ts.red else Ts.text,
        ) { onMute(!state.watchMuted) }
        androidx.compose.material3.Slider(
            value = if (state.watchMuted) 0f else state.watchVolume, onValueChange = { onVolume(it); if (state.watchMuted) onMute(false) },
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Ts.accent, activeTrackColor = Ts.accent),
        )
    }
}

/** The stream on the whole screen, the app turned the way the picture is; tap for the buttons. */
@Composable
private fun FullScreenPlayer(
    state: StreamEngine.State, video: VideoTrack, title: String, egl: EglBase, aspect: Float,
    onMute: (Boolean) -> Unit, onExit: () -> Unit,
) {
    val activity = androidx.activity.compose.LocalActivity.current
    DisposableEffect(Unit) {
        val before = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = when {
            aspect > 1.05f -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            aspect < 0.95f -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> before
        }
        onDispose { activity?.requestedOrientation = before }
    }
    var controls by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // No status or navigation bar (a swipe brings them back for a moment)
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {}
        }
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
        ) { controls = !controls }) {
            VideoView(video, egl, Modifier.fillMaxSize())
            if (controls) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0x66000000)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    if (state.watchAudio != null) {
                        RoundButton(
                            if (state.watchMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            if (state.watchMuted) "Sound on" else "Sound off", if (state.watchMuted) Ts.red else Ts.text,
                        ) { onMute(!state.watchMuted) }
                    }
                    RoundButton(Icons.Filled.FullscreenExit, "Leave full screen", Ts.text, onExit)
                }
            }
        }
    }
}

/** WebTS's "Share" window: what (camera or screen), which camera, how good. */
@Composable
fun ShareDialog(
    onCamera: (front: Boolean, quality: Int) -> Unit, onScreen: () -> Unit, onDismiss: () -> Unit,
    onBoth: (quality: Int) -> Unit = {}, canBoth: Boolean = false,
) {
    var screen by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(0) } // front, back, both
    var quality by remember { mutableStateOf(720) }
    val qualities = listOf(480, 720, 1080)
    TsDialog(
        onDismiss = onDismiss, title = if (screen) "Share your screen" else "Share your camera",
        icon = if (screen) androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ScreenShare else androidx.compose.material.icons.Icons.Rounded.Videocam, tint = ShareGreen,
        subtitle = "Everyone in your channel can watch",
        buttons = {
            PillButton("Cancel", onClick = onDismiss)
            PillButton("Start", PillKind.PRIMARY) {
                when {
                    screen -> onScreen()
                    camera == 2 -> onBoth(quality)
                    else -> onCamera(camera == 0, quality)
                }
            }
        },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Segments(listOf("Camera", "Screen"), if (screen) 1 else 0) { screen = it == 1 }
                if (!screen) {
                    Text("Camera", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Segments(if (canBoth) listOf("Front", "Back", "Both") else listOf("Front", "Back"), camera) { camera = it }
                    if (camera == 2) Text("The back camera big, the front one small in the corner. Swap switches them.", color = Ts.muted, fontSize = 13.sp)
                    Text("Quality", color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Segments(listOf("480p", "720p", "1080p"), qualities.indexOf(quality)) { quality = qualities[it] }
                    Text(
                        when (quality) {
                            480 -> "Data saver: the least data, for mobile networks."
                            720 -> "HD: sharp, and light enough for most connections."
                            else -> "Full HD: the sharpest, needs a good connection on both ends."
                        },
                        color = Ts.muted, fontSize = 13.sp,
                    )
                } else {
                    Text("Android asks once more before it starts. Everything on your screen is shown, notifications too.", color = Ts.muted, fontSize = 13.sp)
                }
            }
    }
}

@Composable
private fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pop.field).border(1.dp, Color(0x0AFFFFFF), RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            Text(
                o, color = if (i == selected) Color.White else Ts.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == selected) Ts.accent.copy(alpha = 0.9f) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
            )
        }
    }
}

/**
 * WebTS's share bar: a green box with your own picture small (tap: bigger, inside the box, at most half the screen;
 * tap again: small), a live dot with what's shared and how many watch, and round buttons to switch the camera, turn
 * or lock the picture, and Stop. [screenTurn] is how far the phone is turned from the screen's upright (clockwise):
 * the preview is turned back by it, so it's the right way up to you while the stream is upright for viewers.
 */
@Composable
fun ShareBar(
    state: StreamEngine.State, egl: EglBase, onFlip: () -> Unit, onStop: () -> Unit, onRotateLock: (Boolean) -> Unit = {},
    screenTurn: Int = 0,
) {
    val kind = state.sharing ?: return
    var big by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(9f / 16f) } // the picture's width / height, as shown
    val camera = kind == Streams.Kind.CAMERA
    // The preview is what the viewers see, the front camera too (not mirrored like a selfie camera)
    val sent = if (state.rotateLocked) state.lockedTurn else screenTurn
    val previewTurn = (360 - sent) % 360
    val track = state.localVideo

    @Composable
    fun Preview(modifier: Modifier) {
        if (track == null) return
        Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))) {
            VideoView(track, egl, Modifier.fillMaxSize(), fill = !big, onSize = { w, h -> aspect = w.toFloat() / h }, turn = previewTurn)
            // Over the picture (a video surface doesn't take taps itself)
            Box(Modifier.fillMaxSize().clickable { big = !big })
        }
    }

    @Composable
    fun Status(modifier: Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveDot()
            val what = when {
                !camera -> "Screen"
                state.shareBoth -> "Both cameras"
                else -> "Camera"
            }
            Text(what, color = ShareText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            if (!state.shareLive) {
                Text("starting…", color = ShareText, fontSize = 14.sp, maxLines = 1)
            } else {
                Icon(Icons.Filled.Visibility, contentDescription = "Watching", tint = ShareText, modifier = Modifier.size(16.dp))
                Text("${state.viewers}", color = ShareText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            }
        }
    }

    @Composable
    fun Buttons() {
        if (camera) {
            RoundButton(Icons.Filled.FlipCameraAndroid, if (state.shareBoth) "Swap the cameras" else "Switch camera", Ts.text, onFlip)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Status(Modifier.weight(1f))
                Buttons()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Preview(Modifier.size(width = 72.dp, height = minOf(72.dp / aspect, 128.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Status(Modifier.padding(end = 4.dp)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Buttons()
                    }
                }
            }
        }
    }
}

private val ShareText = Color(0xFF4ADE80)
private val ShareGreen = Color(0xFF23A559)

/** WebTS's round share button. */
@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Ts.button).border(1.dp, Color(0x1AFFFFFF), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp).upright())
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
