package io.github.korppujauho.pocketts.ui

import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.github.korppujauho.pocketts.TsFile
import io.github.korppujauho.pocketts.proto.ServerModel

/**
 * A person's picture as the TeamSpeak app shows it: their avatar on the server, else their myTeamSpeak picture, over
 * a grey default figure (which stays for people without one, or while it loads).
 */
@Composable
fun PersonAvatar(person: ServerModel.Client?, server: String, size: Int = 24, talking: Boolean = false) {
    val ring = if (talking) Modifier.border(2.dp, Ts.green, CircleShape) else Modifier
    Box(
        Modifier.size(size.dp).then(ring).clip(CircleShape).background(Color(0xFF3F4147)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF8E9297), modifier = Modifier.size((size * 0.95).dp))
        val model: Any? = person?.let {
            TsFile.avatar(server, it.uid, it.avatarName, it.avatar) ?: it.myTeamSpeakAvatar.ifEmpty { null }
        }
        if (model != null) {
            AsyncImage(model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** TeamSpeak's built-in group icons (under 1000), drawn as WebTS does: shields by colour. */
private val BUILTIN = mapOf(100L to Color(0xFF22C55E), 200L to Color(0xFF3B82F6), 300L to Color(0xFFEF4444), 500L to Color(0xFF94A3B8), 600L to Color(0xFFEAB308))

/** An icon from the server (a group's, a channel's, a person's, the server's). */
@Composable
fun TsIcon(id: Long, server: String, size: Dp = 16.dp) {
    if (id <= 0) return
    if (id < 1000) {
        BUILTIN[id]?.let { Icon(Icons.Filled.Shield, contentDescription = null, tint = it, modifier = Modifier.size(size)) }
        return
    }
    val file = TsFile.icon(server, id) ?: return
    AsyncImage(file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
}

/** A picture on the whole screen: pinch to zoom, tap to close. */
@Composable
fun PictureViewer(model: Any, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xF2000000))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                },
            )
            Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(28.dp))
        }
    }
}
