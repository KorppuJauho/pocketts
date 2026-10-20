package io.github.korppujauho.mobilets6.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** WebTS's header: the logo and name on the left, an action on the right. */
@Composable
fun AppHeader(right: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Ts.header).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Dns, contentDescription = null, tint = Ts.brand, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text("MobileTS6", color = Ts.bright, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        right()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ts.hairline))
}

/** WebTS's red "exit" style button. */
@Composable
fun ExitButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Ts.red, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ts.red.copy(alpha = 0.15f))
            .border(1.dp, Ts.red.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** A round control button; red when [off]. */
@Composable
fun ControlButton(icon: ImageVector, label: String, off: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (off) Ts.redButton else Ts.button
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(bg)
            .border(1.dp, if (off) Ts.redButton else Ts.buttonBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (off) Color.White else Ts.text.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

/** Calls [onHold] with true while pressed, false when let go (push-to-talk). */
fun Modifier.holdToTalk(onHold: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onPress = {
        onHold(true)
        try {
            tryAwaitRelease()
        } finally {
            onHold(false)
        }
    })
}

/** WebTS's "Hold to talk" button (in the controls bar in push-to-talk mode). */
@Composable
fun PttButton(held: Boolean, onHold: (Boolean) -> Unit) {
    val color = if (held) Ts.talking else Ts.text
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (held) Ts.talking.copy(alpha = 0.2f) else Ts.button)
            .border(1.dp, if (held) Ts.talking else Ts.buttonBorder, CircleShape).holdToTalk(onHold),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.RecordVoiceOver, contentDescription = "Hold to talk", tint = color)
    }
}

/** The floating push-to-talk button: round, at the bottom right, bigger while held. */
@Composable
fun FloatingPtt(held: Boolean, onHold: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val size = if (held) 66.dp else 60.dp
    Box(
        modifier.size(size).shadow(10.dp, CircleShape).clip(CircleShape)
            .background(if (held) Color(0xEB0E7490) else Color(0xC71E1F22))
            .border(1.dp, if (held) Ts.talking else Color(0x24FFFFFF), CircleShape).holdToTalk(onHold),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Hold to talk", tint = if (held) Color(0xFFE0F2FE) else Ts.text, modifier = Modifier.size(28.dp))
    }
}

/** The bar with the microphone, sound and settings buttons (and "Hold to talk" in push-to-talk mode). */
@Composable
fun ControlsBar(
    micOff: Boolean, soundOff: Boolean, onMic: () -> Unit, onSound: () -> Unit, onSettings: () -> Unit,
    ptt: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ts.card)
            .border(1.dp, Ts.hairline, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        ControlButton(if (micOff) Icons.Filled.MicOff else Icons.Filled.Mic, "Microphone", off = micOff, onClick = onMic)
        ControlButton(
            if (soundOff) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            "Sound", off = soundOff, onClick = onSound,
        )
        ptt?.invoke()
        ControlButton(Icons.Filled.Settings, "Settings", onClick = onSettings)
    }
}

/** A WebTS box: a header strip and the contents below it. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Ts.panel)
            .border(1.dp, Ts.hairline, RoundedCornerShape(12.dp)),
    ) {
        Row(Modifier.fillMaxWidth().background(Ts.card).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            header()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF111214)))
        content()
    }
}

@Composable
fun Loading(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Ts.accent)
        Spacer(Modifier.height(16.dp))
        Text(text, color = Ts.muted, fontSize = 14.sp)
    }
}

@Composable
fun TsField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Ts.faint) } },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Ts.card, unfocusedContainerColor = Ts.card,
            focusedBorderColor = Ts.accent, unfocusedBorderColor = Color(0x14FFFFFF),
            focusedTextColor = Ts.bright, unfocusedTextColor = Ts.bright,
            focusedLabelColor = Ts.accent, unfocusedLabelColor = Ts.muted, cursorColor = Ts.accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** WebTS's blue main button. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (enabled) Ts.accent else Ts.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White.copy(alpha = if (enabled) 1f else 0.6f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(Ts.card)
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** The server name in the panel header: tapping it opens the server info. */
@Composable
fun ServerName(name: String, onClick: () -> Unit) {
    Text(
        name, color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
