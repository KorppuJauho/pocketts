package io.github.korppujauho.pocketts.ui

import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.korppujauho.pocketts.VoiceSettings
import io.github.korppujauho.pocketts.VoiceSettings.Activation
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What the settings window needs besides the settings themselves. */
interface SettingsActions {
    fun save(voice: VoiceSettings, nickname: String)
    /** The microphone's level (RMS 0 - 1) and whether it counts as "on". */
    fun micLevel(): Pair<Float, Boolean>
    fun micTest(on: Boolean)
    fun playTestSound()
    fun setAvatar(uri: android.net.Uri) {}
    fun removeAvatar() {}
    /** Saves the identity into the file the person chose (TeamSpeak's .ini format); null when it worked, else why not. */
    fun exportIdentity(uri: android.net.Uri): String? = null
    /** Takes an identity exported from a TeamSpeak app; null when it worked, else why not. */
    fun importIdentity(text: String): String? = null
    /** The same from a file a TeamSpeak app saved (its .ini export). */
    fun importIdentityFile(uri: android.net.Uri): String? = null
    /** Why the saved identity can't be used, when it can't. */
    fun identityProblem(): String? = null
    /** Lets go of the saved identity: a new one is made at the next connect. */
    fun forgetIdentity() {}
    /** Forgets every server's chat history, and the lines on screen. */
    fun clearChatHistory() {}
    /** Forgets every remembered channel password. */
    fun forgetChannelPasswords() {}
}

/** Who we are on the server, for the profile picture in Settings. */
data class SelfInfo(val person: io.github.korppujauho.pocketts.proto.ServerModel.Client?, val server: String, val avatarStatus: String?)

/** WebTS's Settings: General, Playback and Capture, saved with "Save & Apply". */
@Composable
fun SettingsDialog(
    voice: VoiceSettings,
    nickname: String,
    actions: SettingsActions,
    onDismiss: () -> Unit,
    startTab: Int = 0,
    self: SelfInfo? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Nearly the whole screen: more settings in view, less scrolling
        SettingsContent(
            voice, nickname, actions, onDismiss, startTab,
            Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing).padding(8.dp), self,
        )
    }
}

@Composable
fun SettingsContent(
    voice: VoiceSettings,
    nickname: String,
    actions: SettingsActions,
    onDismiss: () -> Unit,
    startTab: Int = 0,
    modifier: Modifier = Modifier,
    self: SelfInfo? = null,
) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var v by remember { mutableStateOf(voice) }
    var nick by remember { mutableStateOf(nickname) }

    // While the settings are open the microphone is measured, nothing is sent
    DisposableEffect(Unit) {
        actions.micTest(true)
        onDispose { actions.micTest(false) }
    }

    run {
        Column(
            modifier.clip(RoundedCornerShape(12.dp)).background(Ts.panel),
        ) {
            Row(Modifier.fillMaxWidth().background(Ts.card).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Ts.bright, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Close", tint = Ts.muted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onDismiss).padding(4.dp),
                )
            }
            // Scrolls sideways if a narrow screen can't fit them all
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf("General", "Playback", "Capture", "Advanced").forEachIndexed { i, name ->
                    val active = i == tab
                    Text(
                        name, color = if (active) Ts.bright else Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.clickable { tab = i }
                            .drawBehind { if (active) drawLine(Ts.accent, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ts.card))
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    0 -> {
                        if (self != null) ProfilePicture(self, actions)
                        General(v, nick, { nick = it }) { v = it }
                        ChatHistory(actions)
                    }
                    1 -> Playback(v, actions) { v = it }
                    2 -> Capture(v, actions) { v = it }
                    else -> {
                        Advanced(v) { v = it }
                        ChannelPasswordsGroup(actions)
                        Identity(actions)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Ts.card).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                PrimaryButton("Save & Apply") { actions.save(v, nick.trim()); onDismiss() }
            }
        }
    }
}

@Composable
private fun Group(label: String? = null, badge: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (badge != null) ValueBadge(badge)
        }
        content()
    }
}

@Composable
private fun Desc(text: String) = Text(text, color = Ts.muted, fontSize = 12.5.sp, lineHeight = 16.sp)

@Composable
private fun Check(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
            Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = Ts.accent, uncheckedColor = Ts.muted))
        }
        Text(text, color = Ts.text, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Radio(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
            RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = Ts.accent, unselectedColor = Ts.muted))
        }
        Text(text, color = Ts.text, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun TsSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Slider(
        value, onChange, valueRange = range, // no tick marks; values are rounded by the caller
        modifier = Modifier.height(32.dp),
        colors = SliderDefaults.colors(thumbColor = Ts.accent, activeTrackColor = Ts.accent, inactiveTrackColor = Ts.card),
    )
}

@Composable
private fun RangeEnds(left: String, right: String) {
    Row(Modifier.fillMaxWidth().offset(y = (-6).dp)) {
        Text(left, color = Ts.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(right, color = Ts.muted, fontSize = 12.sp)
    }
}

/** WebTS's profile picture: the picture as others see it, Change… and Remove. */
@Composable
private fun ProfilePicture(self: SelfInfo, actions: SettingsActions) {
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) actions.setAvatar(uri) }
    Group("Profile picture") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PersonAvatar(self.person, self.server, 88)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Change") {
                    pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (self.person?.avatar?.isNotEmpty() == true) SecondaryButton("Remove") { actions.removeAvatar() }
            }
        }
        self.avatarStatus?.let { Desc(it) }
        Desc("Others see it on this server. It's made to fit 320 × 320.")
    }
}

@Composable
private fun Identity(actions: SettingsActions) {
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf(actions.identityProblem()) }
    val done = { error: String? ->
        status = error ?: "Imported. It's used from the next connect."
        if (error == null) problem = null
        importing = false
    }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) done(actions.importIdentityFile(uri)) }
    var warning by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    val saveFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) status = actions.exportIdentity(uri) ?: "Saved. Keep the file somewhere only you can reach." }
    Group("TeamSpeak identity") {
        Desc("Your identity is who you are to servers (your groups and permissions). Keep a copy, or use the one from another TeamSpeak app.")
        problem?.let { Desc(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Wipe") { wiping = true }
            if (problem == null) SecondaryButton("Export") { warning = true }
            SecondaryButton("Import") { importing = true }
        }
        status?.let { Desc(it) }
    }
    // Export: what the file means first, then a file of the person's choosing (not shared to an app)
    if (warning) {
        TsDialog(
            onDismiss = { warning = false }, title = "Export your identity", icon = androidx.compose.material.icons.Icons.Rounded.Warning,
            tint = Ts.amber, subtitle = "Keep it private",
            buttons = {
                PillButton("Cancel") { warning = false }
                PillButton("Save to a file", PillKind.PRIMARY) {
                    warning = false
                    saveFile.launch("pocketts-identity.ini")
                }
            },
        ) {
            Desc(
                "The file is your identity itself: anyone who has it is you on every server, with your groups and " +
                    "permissions, such as a server admin's. Nobody can tell the difference."
            )
            Desc(
                "Save it somewhere only you can reach, like this phone or a private drive. Don't send it in a chat or " +
                    "an email, and don't post it. A TeamSpeak app can open it, and so can PocketTS on another phone."
            )
        }
    }
    // Wipe: what's lost first, with a way to keep a copy
    if (wiping) {
        TsDialog(
            onDismiss = { wiping = false }, title = "Wipe your identity?", icon = androidx.compose.material.icons.Icons.Rounded.Warning,
            tint = Ts.red, subtitle = "This can't be undone",
            buttons = {
                PillButton("Cancel") { wiping = false }
                PillButton("Wipe", PillKind.DANGER) {
                    actions.forgetIdentity()
                    problem = null
                    wiping = false
                    status = "Wiped. A new identity is made at the next connect."
                }
            },
        ) {
            Desc(
                "This phone's identity is deleted, and a new one is made the next time you connect. To every server " +
                    "you're then someone new: your groups, permissions and anything given to your old identity (such " +
                    "as admin rights) don't come along."
            )
            Desc("The only way back is an exported copy. If you might want it again, cancel and Export it first.")
        }
    }
    if (importing) {
        var text by remember { mutableStateOf("") }
        TsDialog(
            onDismiss = { importing = false }, title = "Import an identity", icon = androidx.compose.material.icons.Icons.Rounded.Key,
            subtitle = "From a TeamSpeak app",
            buttons = {
                PillButton("Cancel") { importing = false }
                PillButton("Import", PillKind.PRIMARY, enabled = text.isNotBlank()) { done(actions.importIdentity(text)) }
            },
        ) {
            Desc("Paste the identity a TeamSpeak app exported, or choose the file it saved (.ini). It replaces this phone's own from the next connect.")
            // Hidden like a password while it's on screen
            TsField(text, { text = it }, "Identity", password = true)
            PillButton("Choose a file…", modifier = Modifier.fillMaxWidth()) { pickFile.launch(arrayOf("*/*")) }
        }
    }
}

@Composable
private fun ChannelPasswordsGroup(actions: SettingsActions) {
    var forgotten by remember { mutableStateOf(false) }
    Group("Channel passwords") {
        Desc("Passwords you chose to remember join their channels with one tap. They're kept on this phone, encrypted.")
        SecondaryButton("Forget saved passwords") {
            actions.forgetChannelPasswords()
            forgotten = true
        }
        if (forgotten) Desc("Forgotten.")
    }
}

@Composable
private fun ChatHistory(actions: SettingsActions) {
    var asking by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    val rules = io.github.korppujauho.pocketts.proto.ChatHistory
    Group("Chat history") {
        Desc(
            "Channel chats, private chats and who joined or left are kept on this phone, encrypted, and shown again " +
                "when you connect to the same server: the last ${rules.DAYS} days, at most ${rules.LINES} lines per " +
                "conversation. Pokes aren't kept."
        )
        SecondaryButton("Clear chat history") { asking = true }
        if (cleared) Desc("Cleared.")
    }
    if (asking) {
        TsDialog(
            onDismiss = { asking = false }, title = "Clear chat history?", icon = androidx.compose.material.icons.Icons.Rounded.DeleteOutline, tint = Ts.red,
            buttons = {
                PillButton("Cancel") { asking = false }
                PillButton("Clear", PillKind.DANGER) {
                    actions.clearChatHistory()
                    cleared = true
                    asking = false
                }
            },
        ) {
            Desc("Every server's saved chat is removed from this phone, and the chat on screen now.")
        }
    }
}

@Composable
private fun General(v: VoiceSettings, nick: String, onNick: (String) -> Unit, onChange: (VoiceSettings) -> Unit) {
    Group("Nickname") {
        TsField(nick, onNick, "Nickname")
    }
    Group {
        Check("Floating push-to-talk button", v.floatingPtt) { onChange(v.copy(floatingPtt = it)) }
        Desc("In push-to-talk mode, a round button stays at the bottom of the screen, so you can talk while you scroll the chat.")
    }
    Group {
        Check("Talk button over other apps", v.overlayPtt) { onChange(v.copy(overlayPtt = it)) }
        Desc("In push-to-talk mode, the round button stays on screen when you go to other apps. Hold it to talk, drag it to move it. Android asks once to let PocketTS display over other apps.")
    }
    Group {
        Check("Volume buttons are push-to-talk", v.volumeKeysPtt) { onChange(v.copy(volumeKeysPtt = it)) }
        Desc("In push-to-talk mode, holding a volume button talks while the app is on screen (instead of changing the volume).")
    }
    Group {
        Check("Headset button is push-to-talk", v.headsetPtt) { onChange(v.copy(headsetPtt = it)) }
        Desc("In push-to-talk mode, press the button on your headphones to start talking and press it again to stop. Works with the screen off too. While it's on, the button doesn't play or pause music.")
    }
    Group {
        Check("Allow joining spacer channels", v.joinSpacers) { onChange(v.copy(joinSpacers = it)) }
        Desc("Tapping a spacer (a divider line or title in the channel list) joins it like any channel. Some servers use one as their AFK channel. Off, spacers can't be joined by a stray tap.")
    }
}

@Composable
private fun Advanced(v: VoiceSettings, onChange: (VoiceSettings) -> Unit) {
    Group("Video connection") {
        Desc("Streams go straight between you and the other end. Behind some networks they need a relay (a TURN server): enter one here if you have it.")
        TsField(v.turnUrl, { onChange(v.copy(turnUrl = it)) }, "Relay (turn:host:port)", placeholder = "turn:relay.example.com:3478")
        TsField(v.turnUser, { onChange(v.copy(turnUser = it)) }, "Relay username")
        TsField(v.turnPassword, { onChange(v.copy(turnPassword = it)) }, "Relay password", password = true)
        Check("Always through the relay", v.relayOnly) { onChange(v.copy(relayOnly = it)) }
    }
    Group {
        Desc("A server whose file transfer is on another port (its pictures and avatars don't load) can have its own port in the favourite's Advanced part.")
    }
}

@Composable
private fun Playback(v: VoiceSettings, actions: SettingsActions, onChange: (VoiceSettings) -> Unit) {
    Group("Voice volume", "${if (v.voiceVolumeDb > 0) "+" else ""}${v.voiceVolumeDb} dB") {
        TsSlider(v.voiceVolumeDb.toFloat(), -20f..12f, 31) { onChange(v.copy(voiceVolumeDb = it.roundToInt())) }
        RangeEnds("Quiet", "Loud")
    }
    Group {
        SecondaryButton("Play test sound") { actions.playTestSound() }
    }
    Group {
        Check("Sounds for events", v.uiSounds) { onChange(v.copy(uiSounds = it)) }
        Desc("Short sounds when someone comes into or leaves your channel, for messages, pokes and streams, and for the microphone and sound buttons.")
    }
    if (v.uiSounds) {
        Group("Sound volume", "${(v.uiSoundVolume * 100).roundToInt()}%") {
            TsSlider(v.uiSoundVolume, 0f..1f, 19) { onChange(v.copy(uiSoundVolume = (it * 20).roundToInt() / 20f)) }
            SecondaryButton("Play a sound") { io.github.korppujauho.pocketts.UiSounds.play(io.github.korppujauho.pocketts.UiSounds.Cue.MESSAGE_IN, v) }
        }
    }
    Desc(
        if (v.echoCancellation) "With echo cancellation on, voices follow the call volume: press a volume button while connected."
        else "Voices follow the media volume."
    )
}

@Composable
private fun Capture(v: VoiceSettings, actions: SettingsActions, onChange: (VoiceSettings) -> Unit) {
    Group("Microphone volume", "${(v.micVolume * 100).roundToInt()}%") {
        TsSlider(v.micVolume, 0f..3f, 29) { onChange(v.copy(micVolume = (it * 10).roundToInt() / 10f)) }
    }
    Group("Activation") {
        Radio("Voice activation", v.activation == Activation.VAD) { onChange(v.copy(activation = Activation.VAD)) }
        Radio("Push-to-talk", v.activation == Activation.PTT) { onChange(v.copy(activation = Activation.PTT)) }
        Radio("Continuous transmission", v.activation == Activation.CONTINUOUS) { onChange(v.copy(activation = Activation.CONTINUOUS)) }
    }
    if (v.activation == Activation.PTT) Desc("Hold the \"Hold to talk\" button while you speak.")
    Group("Level") {
        LevelMeter(actions, if (v.activation == Activation.VAD) v.vadThreshold else null)
        Desc("The bar shows your microphone while these settings are open; nothing is sent meanwhile.")
    }
    if (v.activation == Activation.VAD) {
        Group("Voice activation level", "%.1f%%".format(v.vadThreshold * 100)) {
            TsSlider(v.vadThreshold, 0f..0.1f, 99) { onChange(v.copy(vadThreshold = (it * 1000).roundToInt() / 1000f)) }
            Desc("Slide right to block background noise. If your voice cuts out, slide left.")
        }
        Group("Delay before deactivation", "%.1f s".format(v.vadTailMs / 1000f)) {
            TsSlider(v.vadTailMs / 1000f, 0.1f..2f, 18) { onChange(v.copy(vadTailMs = (it * 10).roundToInt() * 100)) }
        }
    }
    Group("Signal processing") {
        Check("Remove background noise", v.noiseSuppression) { onChange(v.copy(noiseSuppression = it)) }
        Check("Echo cancellation", v.echoCancellation) { onChange(v.copy(echoCancellation = it)) }
        Check("Automatic gain control", v.autoGainControl) { onChange(v.copy(autoGainControl = it)) }
        Desc("Echo cancellation plays voices like a phone call (loudspeaker, or your headset). Turn it off for the best sound with headphones.")
    }
}

/** WebTS's level meter: green while "on", with the voice activation threshold as a red line (0 - 10 % RMS). */
@Composable
private fun LevelMeter(actions: SettingsActions, threshold: Float?) {
    var level by remember { mutableFloatStateOf(0f) }
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val (l, a) = actions.micLevel()
            level = l
            on = a
            delay(60)
        }
    }
    var width by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF111214)).onSizeChanged { width = it.width },
    ) {
        val fraction = (level / 0.1f).coerceIn(0f, 1f)
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(if (on) Ts.green else Color(0xFF64748B)))
        if (threshold != null && width > 0) {
            val x = with(density) { (width * (threshold / 0.1f).coerceIn(0f, 1f)).toDp() }
            Box(Modifier.offset(x = x).width(2.dp).fillMaxSize().background(Ts.red))
        }
    }
}
