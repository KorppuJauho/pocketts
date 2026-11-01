package io.github.korppujauho.pocketts.ui

import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.material.icons.filled.PictureInPicture
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.res.stringResource
import io.github.korppujauho.pocketts.R

/** WebTS's WATCH button (only for streams in your own channel, as in the desktop app). */
@Composable
fun WatchButton(onClick: () -> Unit) {
    Text(
        stringResource(R.string.stream_watch), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(WatchGreen).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * A video track drawn into an ordinary view (a TextureView): resized like the rest of the screen. A SurfaceView, drawn
 * in a layer of its own, kept its old size when the view changed size, so the picture came out cut and stretched.
 */
private class TextureVideoView(
    context: android.content.Context, eglContext: EglBase.Context, private val onShape: (Int, Int) -> Unit,
) : android.view.TextureView(context), android.view.TextureView.SurfaceTextureListener, org.webrtc.VideoSink {
    private val renderer = org.webrtc.EglRenderer("preview").apply { init(eglContext, EglBase.CONFIG_PLAIN, org.webrtc.GlRectDrawer()) }
    private var lastWidth = 0
    private var lastHeight = 0

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, width: Int, height: Int) {
        renderer.setLayoutAspectRatio(width / height.toFloat())
        renderer.createEglSurface(st)
    }

    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, width: Int, height: Int) {
        renderer.setLayoutAspectRatio(width / height.toFloat())
    }

    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
        val done = java.util.concurrent.CountDownLatch(1)
        renderer.releaseEglSurface { done.countDown() }
        done.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return true
    }

    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}

    override fun onFrame(frame: org.webrtc.VideoFrame) {
        val w = frame.rotatedWidth
        val h = frame.rotatedHeight
        if (w != lastWidth || h != lastHeight) {
            lastWidth = w
            lastHeight = h
            io.github.korppujauho.pocketts.AppLog.log("Preview ${w}x$h")
            post { onShape(w, h) }
        }
        renderer.onFrame(frame)
    }

    fun release() = renderer.release()
}

/** Our own share's picture: the whole of it, in its own shape, turned by [turn] more for what's shown here only. */
@Composable
private fun PreviewVideo(
    track: VideoTrack, egl: EglBase, modifier: Modifier, turn: Int,
    shownTurn: java.util.concurrent.atomic.AtomicInteger? = null, // set to the whole turn each picture is drawn with
    onSize: (Int, Int) -> Unit,
) {
    var view by remember { mutableStateOf<TextureVideoView?>(null) }
    var shape by remember { mutableStateOf<Float?>(null) }
    val turnNow = androidx.compose.runtime.rememberUpdatedState(turn)
    val report = androidx.compose.runtime.rememberUpdatedState(onSize)
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                TextureVideoView(ctx, egl.eglBaseContext) { w, h ->
                    shape = w.toFloat() / h
                    report.value(w, h)
                }.also { view = it }
            },
            modifier = shape?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize(),
            onRelease = { it.release() },
        )
    }
    DisposableEffect(track, view) {
        val v = view
        // The same pictures with the extra turn as their label (the renderer turns them)
        val sink = v?.let {
            org.webrtc.VideoSink { f ->
                val t = turnNow.value
                shownTurn?.set((f.rotation + t) % 360)
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
                // The picture is fitted while drawing: a hardware-scaled surface, cut to the view's shape when it's made,
                // stayed that shape when the view changed
                setEnableHardwareScaler(false)
                setMirror(mirror)
                renderer = this
            }
        },
        update = { it.setMirror(mirror) },
        modifier = shape.let { a -> if (fill || a == null) Modifier.fillMaxSize() else Modifier.aspectRatio(a) },
        onRelease = { it.release() },
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
    // (each renderer is let go when its view goes: onRelease)
}

private const val MIN_WATCH_HEIGHT = 96f // dp
private const val PULL_TO_SWITCH = 56f // dp dragged on past the top that makes the stream small inside the app

/**
 * Watching someone's stream: a box like the large preview of your own share, with its sound (on, off and how loud),
 * a small window over other apps, full screen, and close. Full screen turns the app the way the picture is: a wide
 * picture goes sideways, a tall one stays upright.
 *
 * The handle under the picture sets its height (remembered): at first at most 40 % of the screen, so the channels and
 * the chat stay in view, and never taller than the picture needs. Pulled on past the top it shrinks to a small
 * picture over the app that can be moved ([MiniPlayer]).
 *
 * [talk]: the talk buttons (push-to-talk, whisper) for the full screen, which covers the app's own.
 */
@Composable
fun WatchCard(
    state: StreamEngine.State, title: String, egl: EglBase, onClose: () -> Unit,
    onMute: (Boolean) -> Unit = {}, onVolume: (Float) -> Unit = {}, onSmallWindow: (() -> Unit)? = null,
    talk: (@Composable (Modifier) -> Unit)? = null,
) {
    var aspect by remember { mutableStateOf(16f / 9f) }
    var full by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(false) }
    val video = state.watchVideo
    val prefs = androidx.compose.ui.platform.LocalContext.current.let { remember { it.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) } }
    var chosen by remember { mutableFloatStateOf(prefs.getFloat("watchHeight", 0f)) } // dp; 0: not chosen yet
    var pull by remember { mutableFloatStateOf(0f) } // dragged on past the top, dp (below 0)
    var mini by remember { mutableStateOf(false) }
    if (mini) {
        MiniPlayer(state, video, egl, aspect, onOpen = { mini = false }, onClose = onClose)
        if (full && video != null) FullScreenPlayer(state, video, title, egl, aspect, onMute, onVolume, talk) { full = false }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ts.accent.copy(alpha = 0.12f))
            .border(1.dp, Ts.accent.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val screen = LocalConfiguration.current.screenHeightDp.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val natural = maxWidth.value / aspect
            val highest = maxOf(MIN_WATCH_HEIGHT, minOf(natural, screen * 0.75f))
            val height = (if (chosen > 0) chosen else minOf(natural, screen * 0.4f)).coerceIn(MIN_WATCH_HEIGHT, highest)
            val currentHeight by androidx.compose.runtime.rememberUpdatedState(height)
            Column {
                Box(
                    Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)
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
                // The handle: drag for the height; on past the top, small inside the app (its icon shows when letting go
                // would do it)
                var raw by remember { mutableFloatStateOf(0f) }
                val toSmall = pull < -PULL_TO_SWITCH
                val handleLabel = stringResource(R.string.stream_resize)
                Box(
                    Modifier.fillMaxWidth().height(22.dp)
                        .semantics { contentDescription = handleLabel }
                        .pointerInput(highest) {
                            detectVerticalDragGestures(
                                onDragStart = { raw = currentHeight },
                                onDragEnd = {
                                    if (pull < -PULL_TO_SWITCH) mini = true
                                    pull = 0f
                                    prefs.edit().putFloat("watchHeight", chosen).apply()
                                },
                                onDragCancel = { pull = 0f },
                            ) { change, dy ->
                                change.consume()
                                raw += dy / density
                                chosen = raw.coerceIn(MIN_WATCH_HEIGHT, highest)
                                pull = minOf(0f, raw - chosen)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        toSmall -> Icon(Icons.Filled.PictureInPicture, stringResource(R.string.stream_mini), tint = Ts.accent, modifier = Modifier.size(20.dp))
                        else -> Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(if (pull != 0f) Ts.accent else Ts.muted))
                    }
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
                    if (state.watchMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.sound),
                    if (state.watchMuted) Ts.red else Ts.text,
                ) { sound = !sound }
            }
            if (onSmallWindow != null) RoundButton(Icons.Filled.PictureInPictureAlt, stringResource(R.string.stream_small_window), Ts.text, onClick = onSmallWindow)
            RoundButton(Icons.Filled.Fullscreen, stringResource(R.string.stream_full_screen), Ts.text) { full = true }
            RoundButton(Icons.Filled.Close, stringResource(R.string.stream_stop_watching), Ts.text, onClick = onClose)
        }
        if (sound && state.watchAudio != null) SoundRow(state, onMute, onVolume)
    }
    if (full && video != null) FullScreenPlayer(state, video, title, egl, aspect, onMute, onVolume, talk) { full = false }
}

/**
 * A watched stream made small inside the app (its box pulled up past the top): the picture over everything, at first
 * in the top corner, moved by dragging it. A tap brings the box back; the ✕ stops watching.
 */
@Composable
private fun MiniPlayer(state: StreamEngine.State, video: VideoTrack?, egl: EglBase, aspect: Float, onOpen: () -> Unit, onClose: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val config = LocalConfiguration.current
    val width = config.screenWidthDp * if (aspect >= 1f) 0.5f else 0.32f
    val height = width / aspect
    val screenW = config.screenWidthDp * density
    val screenH = config.screenHeightDp * density
    fun inside(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(
        x.coerceIn(0f, maxOf(0f, screenW - width * density)), y.coerceIn(0f, maxOf(0f, screenH - height * density)),
    )
    var at by remember { mutableStateOf(inside(screenW - (width + 12) * density, 96 * density)) }
    val label = stringResource(R.string.stream_mini)
    androidx.compose.ui.window.Popup(
        popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize,
            ) = androidx.compose.ui.unit.IntOffset(
                at.x.toInt().coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
                at.y.toInt().coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height)),
            )
        },
        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
    ) {
        Box(
            Modifier.size(width.dp, height.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)
                .border(2.dp, Ts.accent, RoundedCornerShape(10.dp))
                .semantics { contentDescription = label }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        at = inside(at.x + drag.x, at.y + drag.y)
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }) },
        ) {
            if (video != null) VideoView(video, egl, Modifier.fillMaxSize())
            if (state.watchStatus.isNotEmpty()) {
                Text(state.watchStatus, color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.Center).padding(6.dp))
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).clip(CircleShape).background(Color(0x99000000)).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, stringResource(R.string.stream_stop_watching), tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** The stream's sound: on or off, and how loud ([small]: the full screen's, see-through and smaller). */
@Composable
private fun SoundRow(state: StreamEngine.State, onMute: (Boolean) -> Unit, onVolume: (Float) -> Unit, small: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 10.dp)) {
        RoundButton(
            if (state.watchMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(if (state.watchMuted) R.string.stream_sound_on else R.string.stream_sound_off), if (state.watchMuted) Ts.red else Ts.text,
            size = if (small) 36.dp else 40.dp, background = if (small) FullScreenButton else Ts.button,
        ) { onMute(!state.watchMuted) }
        ThinSlider(
            if (state.watchMuted) 0f else state.watchVolume, { onVolume(it); if (state.watchMuted) onMute(false) },
            Modifier.weight(1f),
        )
    }
}

private val FullScreenButton = Color(0x59000000)

/** A slim slider (0 - 1): a thin track and a small round handle that stays within it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ThinSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Slider(
        value = value, onValueChange = onChange, modifier = modifier,
        thumb = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White)) },
        track = { s ->
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF))) {
                Box(Modifier.fillMaxWidth(s.value.coerceIn(0f, 1f)).height(4.dp).background(Ts.accent))
            }
        },
    )
}

/**
 * The stream on the whole screen, the app turned the way the picture is; tap for the buttons: the sound (on, off and how
 * loud), leaving the full screen, and the talk buttons ([talk]) in the bottom corner, so push-to-talk works while watching.
 */
@Composable
private fun FullScreenPlayer(
    state: StreamEngine.State, video: VideoTrack, title: String, egl: EglBase, aspect: Float,
    onMute: (Boolean) -> Unit, onVolume: (Float) -> Unit, talk: (@Composable (Modifier) -> Unit)?, onExit: () -> Unit,
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
            onClickLabel = stringResource(if (controls) R.string.a11y_hide_controls else R.string.a11y_show_controls),
        ) { controls = !controls }) {
            VideoView(video, egl, Modifier.fillMaxSize())
            if (controls) {
                // Kept light over the picture: a fade at the top, small see-through buttons
                Row(
                    Modifier.fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent)))
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    if (state.watchAudio != null) Box(Modifier.width(150.dp)) { SoundRow(state, onMute, onVolume, small = true) }
                    RoundButton(Icons.Filled.FullscreenExit, stringResource(R.string.stream_leave_full_screen), Ts.text, size = 36.dp, background = FullScreenButton, onClick = onExit)
                }
                talk?.invoke(Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
        }
    }
}

/** WebTS's "Share" window: what (camera or screen), which camera, how good. */
@Composable
fun ShareDialog(
    onCamera: (front: Boolean, quality: Int, sound: Boolean) -> Unit, onScreen: (sound: Boolean) -> Unit, onDismiss: () -> Unit,
    onBoth: (quality: Int, sound: Boolean) -> Unit = { _, _ -> }, canBoth: Boolean = false, sound: Boolean = false,
    screenSound: Boolean = true,
    screenSoundBlocked: Boolean = false, // watching a stream: the phone's sound can't go with the screen now
) {
    var withSound by remember { mutableStateOf(sound) }
    var withScreenSound by remember { mutableStateOf(screenSound) }
    var screen by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(0) } // front, back, both
    var quality by remember { mutableStateOf(720) }
    val qualities = listOf(480, 720, 1080)
    TsDialog(
        onDismiss = onDismiss, title = stringResource(if (screen) R.string.share_screen_title else R.string.share_camera_title),
        icon = if (screen) androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ScreenShare else androidx.compose.material.icons.Icons.Rounded.Videocam, tint = ShareGreen,
        subtitle = stringResource(R.string.share_subtitle),
        buttons = {
            PillButton(stringResource(R.string.cancel), onClick = onDismiss)
            PillButton(stringResource(R.string.start), PillKind.PRIMARY) {
                when {
                    screen -> onScreen(withScreenSound && !screenSoundBlocked)
                    camera == 2 -> onBoth(quality, withSound)
                    else -> onCamera(camera == 0, quality, withSound)
                }
            }
        },
    ) {
            @Composable
            fun SoundChoice(label: String, on: Boolean, desc: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled) { onChange(!on) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                        androidx.compose.material3.Checkbox(
                            on, onChange, enabled = enabled,
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = Ts.accent, uncheckedColor = Ts.muted,
                                disabledCheckedColor = Ts.muted.copy(alpha = 0.4f), disabledUncheckedColor = Ts.muted.copy(alpha = 0.4f),
                            ),
                        )
                    }
                    Text(label, color = if (enabled) Ts.text else Ts.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
                }
                Text(desc, color = Ts.muted, fontSize = 13.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.share_what), color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Segments(listOf(stringResource(R.string.share_camera), stringResource(R.string.share_screen)), if (screen) 1 else 0) { screen = it == 1 }
                if (!screen) {
                    Text(stringResource(R.string.share_camera), color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Segments(listOf(stringResource(R.string.camera_front), stringResource(R.string.camera_back)) + if (canBoth) listOf(stringResource(R.string.camera_both)) else emptyList(), camera) { camera = it }
                    if (camera == 2) Text(stringResource(R.string.share_both_desc), color = Ts.muted, fontSize = 13.sp)
                    Text(stringResource(R.string.share_quality), color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Segments(listOf("480p", "720p", "1080p"), qualities.indexOf(quality)) { quality = qualities[it] }
                    Text(
                        when (quality) {
                            480 -> stringResource(R.string.share_quality_480)
                            720 -> stringResource(R.string.share_quality_720)
                            else -> stringResource(R.string.share_quality_1080)
                        },
                        color = Ts.muted, fontSize = 13.sp,
                    )
                    SoundChoice(
                        stringResource(R.string.share_sound), withSound,
                        stringResource(if (withSound) R.string.share_sound_on_desc else R.string.share_sound_off_desc),
                    ) { withSound = it }
                } else {
                    Text(stringResource(R.string.share_screen_desc), color = Ts.muted, fontSize = 13.sp)
                    SoundChoice(
                        stringResource(R.string.share_screen_sound), withScreenSound && !screenSoundBlocked,
                        stringResource(
                            when {
                                screenSoundBlocked -> R.string.share_screen_sound_watching
                                withScreenSound -> R.string.share_screen_sound_on_desc
                                else -> R.string.share_screen_sound_off_desc
                            },
                        ),
                        enabled = !screenSoundBlocked,
                    ) { withScreenSound = it }
                }
            }
    }
}

@Composable
internal fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
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
 * or lock the picture, light it (the ring light with the front camera, the flashlight with the back one), and Stop.
 * [screenTurn] is how far the phone is turned from the screen's upright (clockwise): the preview is turned back by it,
 * so it's the right way up to you while the stream is upright for viewers.
 */
@Composable
fun ShareBar(
    state: StreamEngine.State, egl: EglBase, onFlip: () -> Unit, onStop: () -> Unit, onRotateLock: (Boolean) -> Unit = {},
    screenTurn: Int = 0,
    light: Boolean? = null, // the ring light, when it can be had (the front camera): on or off
    onLight: (Boolean) -> Unit = {},
    torch: Boolean? = null, // the back camera's flashlight, when it has one: on or off
    onTorch: (Boolean) -> Unit = {},
    onZoom: ((Float) -> Unit)? = null, // pinching the big picture of one camera
    onFocus: ((x: Float, y: Float) -> Unit)? = null, // tapping it: a spot, 0 - 1 across the camera's own picture
    onLens: (() -> Unit)? = null, // the next back lens, on phones that offer them apart
) {
    val kind = state.sharing ?: return
    var big by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(9f / 16f) } // the picture's width / height, as shown
    val camera = kind == Streams.Kind.CAMERA
    // The preview is what the viewers see, the front camera too (not mirrored like a selfie camera), turned back by
    // how the phone is held: upright for you as it is for them. With the turn locked, turning the phone turns the
    // preview too, as the viewers' picture turns.
    val previewTurn = (360 - screenTurn) % 360
    val track = state.localVideo

    // The big picture of one camera: pinch to zoom, tap to focus, double tap back to 1×; a button makes it small again
    // (tapping the small one makes it big)
    val touch = camera && !state.shareBoth && onZoom != null && onFocus != null
    val shownTurn = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val shape = androidx.compose.runtime.rememberUpdatedState(aspect)
    // The zoom while pinching, ahead of the camera's answer (taken from it again when the camera changes)
    val pinch = remember { floatArrayOf(state.zoom) }
    androidx.compose.runtime.LaunchedEffect(state.shareFront, state.zoomMin, state.zoomMax) { pinch[0] = state.zoom }
    val range = androidx.compose.runtime.rememberUpdatedState(state.zoomMin..state.zoomMax)
    var ring by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    androidx.compose.runtime.LaunchedEffect(ring) {
        if (ring != null) {
            kotlinx.coroutines.delay(1000)
            ring = null
        }
    }
    // The zoom shown for a moment when it changes
    var zoomShown by remember { mutableStateOf(false) }
    val lastZoom = remember { floatArrayOf(state.zoom) }
    androidx.compose.runtime.LaunchedEffect(state.zoom) {
        if (state.zoom == lastZoom[0]) return@LaunchedEffect
        lastZoom[0] = state.zoom
        zoomShown = true
        kotlinx.coroutines.delay(1200)
        zoomShown = false
    }

    @Composable
    fun Preview(modifier: Modifier) {
        if (track == null) return
        val controls = big && touch
        Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))) {
            // Small and big alike, the whole picture in its own shape (the small box already has that shape)
            PreviewVideo(track, egl, Modifier.fillMaxSize(), previewTurn, shownTurn) { w, h -> aspect = w.toFloat() / h }
            // Over the picture (a video surface doesn't take taps itself)
            if (!controls) {
                Box(Modifier.fillMaxSize().clickable(onClickLabel = stringResource(if (big) R.string.a11y_preview_smaller else R.string.a11y_preview_bigger)) { big = !big })
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { p ->
                                    // Only on the picture (the big box can be wider), turned back to the camera's own
                                    val spot = io.github.korppujauho.pocketts.CameraView.fitted(p.x, p.y, size.width.toFloat(), size.height.toFloat(), shape.value)
                                    if (spot != null) {
                                        ring = p
                                        val (x, y) = io.github.korppujauho.pocketts.CameraView.unturn(spot.first, spot.second, shownTurn.get())
                                        onFocus?.invoke(x, y)
                                    }
                                },
                                onDoubleTap = {
                                    pinch[0] = 1f
                                    onZoom?.invoke(1f)
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, z, _ ->
                                if (z != 1f) {
                                    pinch[0] = (pinch[0] * z).coerceIn(range.value.start, range.value.endInclusive)
                                    onZoom?.invoke(pinch[0])
                                }
                            }
                        },
                )
                ring?.let { p ->
                    val side = 52.dp
                    val half = with(androidx.compose.ui.platform.LocalDensity.current) { (side / 2).toPx() }
                    Box(
                        Modifier.offset { androidx.compose.ui.unit.IntOffset((p.x - half).toInt(), (p.y - half).toInt()) }
                            .size(side).border(2.dp, Color.White, CircleShape),
                    )
                }
                if (zoomShown) {
                    Text(
                        "%.1f×".format(state.zoom), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clip(CircleShape).background(Color(0x99000000))
                        .clickable(role = Role.Button) { big = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.FullscreenExit, contentDescription = stringResource(R.string.share_preview_small), tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
    @Composable
    fun Status(modifier: Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveDot()
            val what = when {
                !camera -> stringResource(R.string.share_screen)
                state.shareBoth -> stringResource(R.string.share_both_cameras)
                else -> stringResource(R.string.share_camera)
            }
            Text(what, color = ShareText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            if (state.shareSound) {
                Icon(
                    if (state.soundPaused) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = stringResource(if (state.soundPaused) R.string.share_sound_quiet else R.string.share_sound_live),
                    tint = ShareText, modifier = Modifier.size(16.dp),
                )
            }
            if (!state.shareLive) {
                Text(stringResource(R.string.share_starting), color = ShareText, fontSize = 14.sp, maxLines = 1)
            } else if (state.sharePaused) {
                Icon(Icons.Filled.Pause, contentDescription = null, tint = ShareText, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.share_paused_call), color = ShareText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Icon(Icons.Filled.Visibility, contentDescription = stringResource(R.string.share_watching), tint = ShareText, modifier = Modifier.size(16.dp))
                Text("${state.viewers}", color = ShareText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            }
        }
    }

    @Composable
    fun Buttons() {
        if (camera) {
            RoundButton(Icons.Filled.FlipCameraAndroid, stringResource(if (state.shareBoth) R.string.share_swap_cameras else R.string.share_switch_camera), Ts.text, onClick = onFlip)
            val lens = state.lens
            if (lens != null && onLens != null) LensButton(lens, stringResource(R.string.share_lens_switch, lens), onLens)
            RoundButton(
                if (state.rotateLocked) Icons.Filled.ScreenLockRotation else Icons.Filled.ScreenRotation,
                stringResource(if (state.rotateLocked) R.string.share_turn_locked else R.string.share_turn_follows),
                if (state.rotateLocked) Color(0xFFFBBF24) else Ts.text,
            ) { onRotateLock(!state.rotateLocked) }
            if (light != null) {
                RoundButton(
                    Icons.Filled.LightMode, stringResource(if (light) R.string.share_light_on else R.string.share_light_off),
                    if (light) Color(0xFFFBBF24) else Ts.text,
                ) { onLight(!light) }
            }
            if (torch != null) {
                RoundButton(
                    if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    stringResource(if (torch) R.string.share_torch_on else R.string.share_torch_off),
                    if (torch) Color(0xFFFBBF24) else Ts.text,
                ) { onTorch(!torch) }
            }
        }
        Text(
            stringResource(R.string.stop), color = Color(0xFFF87171), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, softWrap = false,
            modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Ts.redButton.copy(alpha = 0.15f))
                .border(1.dp, Ts.redButton, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onStop)
                .wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 18.dp),
        )
    }

    // The buttons on the right, going onto a second line rather than squeezing when there are many (flip, lens, turn,
    // light, Stop) on a narrow screen
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    fun ButtonRow() {
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically,
        ) { Buttons() }
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
            Status(Modifier.fillMaxWidth())
            ButtonRow()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Preview(Modifier.size(width = 72.dp, height = minOf(72.dp / aspect, 128.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Status(Modifier.padding(end = 4.dp)) }
                    ButtonRow()
                }
            }
        }
    }
}

private val WatchGreen = Color(0xFF15803D) // dark enough for WATCH's white letters
private val ShareText = Color(0xFF4ADE80)
private val ShareGreen = Color(0xFF23A559)

/** WebTS's round share button. */
@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color,
    size: androidx.compose.ui.unit.Dp = 40.dp, background: Color = Ts.button, onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).border(1.dp, Color(0x1AFFFFFF), CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size / 2).upright())
    }
}

/** The back lens as its zoom ("0.6×"), in a round button like the others: a tap moves to the next lens. */
@Composable
private fun LensButton(text: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Ts.button).border(1.dp, Color(0x1AFFFFFF), CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, modifier = Modifier.upright())
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
        val status = if (state.watching == 0) stringResource(R.string.stream_ended) else state.watchStatus
        if (status.isNotEmpty()) {
            Text(status, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp))
        }
    }
}
