package io.github.korppujauho.pocketts.ui

import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.korppujauho.pocketts.VoiceSettings
import io.github.korppujauho.pocketts.VoiceSettings.Activation
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import io.github.korppujauho.pocketts.R

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
    /** The identity's security level (null when it can't be read). */
    fun securityLevel(): Int? = null
    /** Forgets every server's chat history, and the lines on screen. */
    fun clearChatHistory() {}
    /** Forgets every remembered channel password. */
    fun forgetChannelPasswords() {}
    /** The app's language as a tag ("" for the phone's), and switching it (the screen is made again). */
    fun language(): String = ""
    fun setLanguage(tag: String) {}
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
    val savedLanguage = remember { actions.language() }
    var language by rememberSaveable { mutableStateOf(savedLanguage) }

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
                Text(stringResource(R.string.settings), color = Ts.bright, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Ts.muted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onDismiss).padding(4.dp),
                )
            }
            // Scrolls sideways if a narrow screen can't fit them all
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(R.string.settings_general, R.string.settings_playback, R.string.settings_capture, R.string.settings_ui, R.string.advanced).map { stringResource(it) }.forEachIndexed { i, name ->
                    val active = i == tab
                    Text(
                        name, color = if (active) Ts.bright else Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.selectable(selected = active, role = androidx.compose.ui.semantics.Role.Tab) { tab = i }
                            .drawBehind { if (active) drawLine(Ts.accent, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
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
                    3 -> {
                        Ui(v) { v = it }
                        Language(language) { language = it }
                    }
                    else -> {
                        Advanced(v) { v = it }
                        ChannelPasswordsGroup(actions)
                        Identity(actions)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Ts.card).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                PrimaryButton(stringResource(R.string.settings_save)) {
                    actions.save(v, nick.trim())
                    onDismiss()
                    // Last: the screen is made again in the new language
                    if (language != savedLanguage) actions.setLanguage(language)
                }
            }
        }
    }
}

/** A group of settings under its [label]; what it's about ([info]) is behind an ⓘ at the label's end. */
@Composable
private fun Group(label: String? = null, badge: String? = null, info: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var about by rememberSaveable(label) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (badge != null) ValueBadge(badge)
            if (info != null) {
                Spacer(Modifier.weight(1f))
                InfoButton(label, about) { about = !about }
            }
        }
        if (info != null && about) Desc(info)
        content()
    }
}

/**
 * A setting's ⓘ: its explanation is put away until this is tapped, so a tab reads as a list of settings. Tapped again,
 * it's put away again.
 */
@Composable
private fun InfoButton(setting: String, open: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (open) R.string.a11y_on else R.string.a11y_off)
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggle)
            .semantics { stateDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Outlined.Info, contentDescription = stringResource(R.string.a11y_about, setting),
            tint = if (open) Ts.accent else Ts.muted, modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun Desc(text: String) = Text(text, color = Ts.muted, fontSize = 12.5.sp, lineHeight = 16.sp)

@Composable
internal fun Check(text: String, checked: Boolean, enabled: Boolean = true, info: String? = null, onChange: (Boolean) -> Unit) {
    var about by rememberSaveable(text) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                Checkbox(
                    checked, onChange, enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = Ts.accent, uncheckedColor = Ts.muted, disabledCheckedColor = Ts.accent.copy(alpha = 0.5f)),
                )
            }
            Text(text, color = if (enabled) Ts.text else Ts.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
        }
        // What the setting is about, behind its ⓘ
        if (info != null) InfoButton(text, about) { about = !about }
    }
    if (info != null && about) Desc(info)
}

@Composable
internal fun Radio(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
            RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = Ts.accent, unselectedColor = Ts.muted))
        }
        Text(text, color = Ts.text, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
internal fun TsSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
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
    Group(stringResource(R.string.avatar_title), info = stringResource(R.string.avatar_desc)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PersonAvatar(self.person, self.server, 88)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.avatar_change)) {
                    pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (self.person?.avatar?.isNotEmpty() == true) SecondaryButton(stringResource(R.string.remove)) { actions.removeAvatar() }
            }
        }
        self.avatarStatus?.let { Desc(it) }
    }
}

@Composable
private fun Identity(actions: SettingsActions) {
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf(actions.identityProblem()) }
    val imported = stringResource(R.string.identity_imported)
    val saved = stringResource(R.string.identity_saved)
    val wiped = stringResource(R.string.identity_wiped)
    val done = { error: String? ->
        status = error ?: imported
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
    ) { uri -> if (uri != null) status = actions.exportIdentity(uri) ?: saved }
    Group(stringResource(R.string.identity_title), info = stringResource(R.string.identity_desc)) {
        problem?.let { Desc(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.identity_wipe)) { wiping = true }
            if (problem == null) SecondaryButton(stringResource(R.string.identity_export)) { warning = true }
            SecondaryButton(stringResource(R.string.identity_import)) { importing = true }
        }
        status?.let { Desc(it) }
    }
    // The security level: some servers need a higher one than a new identity has
    var level by remember { mutableStateOf(actions.securityLevel()) }
    var raising by remember { mutableStateOf(false) }
    level?.let { current ->
        Group(stringResource(R.string.level_title), badge = "$current", info = stringResource(R.string.level_desc)) {
            SecondaryButton(stringResource(R.string.level_raise_button)) { raising = true }
        }
        if (raising) RaiseLevelDialog(current, onClose = { raising = false; level = actions.securityLevel() })
    }
    // Export: what the file means first, then a file of the person's choosing (not shared to an app)
    if (warning) {
        TsDialog(
            onDismiss = { warning = false }, title = stringResource(R.string.identity_export_title), icon = androidx.compose.material.icons.Icons.Rounded.Warning,
            tint = Ts.amber, subtitle = stringResource(R.string.identity_export_subtitle),
            buttons = {
                PillButton(stringResource(R.string.cancel)) { warning = false }
                PillButton(stringResource(R.string.identity_save_file), PillKind.PRIMARY) {
                    warning = false
                    saveFile.launch("pocketts-identity.ini")
                }
            },
        ) {
            Desc(stringResource(R.string.identity_export_what))
            Desc(stringResource(R.string.identity_export_where))
        }
    }
    // Wipe: what's lost first, with a way to keep a copy
    if (wiping) {
        TsDialog(
            onDismiss = { wiping = false }, title = stringResource(R.string.identity_wipe_title), icon = androidx.compose.material.icons.Icons.Rounded.Warning,
            tint = Ts.red, subtitle = stringResource(R.string.cant_be_undone),
            buttons = {
                PillButton(stringResource(R.string.cancel)) { wiping = false }
                PillButton(stringResource(R.string.identity_wipe), PillKind.DANGER) {
                    actions.forgetIdentity()
                    problem = null
                    wiping = false
                    status = wiped
                }
            },
        ) {
            Desc(stringResource(R.string.identity_wipe_what))
            Desc(stringResource(R.string.identity_wipe_back))
        }
    }
    if (importing) {
        var text by remember { mutableStateOf("") }
        TsDialog(
            onDismiss = { importing = false }, title = stringResource(R.string.identity_import_title), icon = androidx.compose.material.icons.Icons.Rounded.Key,
            subtitle = stringResource(R.string.identity_import_subtitle),
            buttons = {
                PillButton(stringResource(R.string.cancel)) { importing = false }
                PillButton(stringResource(R.string.identity_import), PillKind.PRIMARY, enabled = text.isNotBlank()) { done(actions.importIdentity(text)) }
            },
        ) {
            Desc(stringResource(R.string.identity_import_desc))
            // Hidden like a password while it's on screen
            TsField(text, { text = it }, stringResource(R.string.identity_field), password = true)
            PillButton(stringResource(R.string.identity_choose_file), modifier = Modifier.fillMaxWidth()) { pickFile.launch(arrayOf("*/*")) }
        }
    }
}

@Composable
private fun ChannelPasswordsGroup(actions: SettingsActions) {
    var forgotten by remember { mutableStateOf(false) }
    Group(stringResource(R.string.passwords_title), info = stringResource(R.string.passwords_desc)) {
        SecondaryButton(stringResource(R.string.passwords_forget)) {
            actions.forgetChannelPasswords()
            forgotten = true
        }
        if (forgotten) Desc(stringResource(R.string.passwords_forgotten))
    }
}

@Composable
private fun ChatHistory(actions: SettingsActions) {
    var asking by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    val rules = io.github.korppujauho.pocketts.proto.ChatHistory
    Group(
        stringResource(R.string.history_title),
        info = stringResource(
            R.string.history_desc,
            androidx.compose.ui.res.pluralStringResource(R.plurals.history_days, rules.DAYS, rules.DAYS),
            androidx.compose.ui.res.pluralStringResource(R.plurals.history_lines, rules.LINES, rules.LINES),
        ),
    ) {
        SecondaryButton(stringResource(R.string.history_clear)) { asking = true }
        if (cleared) Desc(stringResource(R.string.history_cleared))
    }
    if (asking) {
        TsDialog(
            onDismiss = { asking = false }, title = stringResource(R.string.history_clear_title), icon = androidx.compose.material.icons.Icons.Rounded.DeleteOutline, tint = Ts.red,
            buttons = {
                PillButton(stringResource(R.string.cancel)) { asking = false }
                PillButton(stringResource(R.string.history_clear_button), PillKind.DANGER) {
                    actions.clearChatHistory()
                    cleared = true
                    asking = false
                }
            },
        ) {
            Desc(stringResource(R.string.history_clear_desc))
        }
    }
}

@Composable
private fun General(v: VoiceSettings, nick: String, onNick: (String) -> Unit, onChange: (VoiceSettings) -> Unit) {
    Group(stringResource(R.string.nickname)) {
        TsField(nick, onNick, stringResource(R.string.nickname))
    }
    Group {
        Check(stringResource(R.string.general_floating_ptt), v.floatingPtt, info = stringResource(R.string.general_floating_ptt_desc)) { onChange(v.copy(floatingPtt = it)) }
    }
    Group {
        Check(stringResource(R.string.general_overlay_ptt), v.overlayPtt, info = stringResource(R.string.general_overlay_ptt_desc)) { onChange(v.copy(overlayPtt = it)) }
    }
    Group {
        Check(stringResource(R.string.general_volume_ptt), v.volumeKeysPtt, info = stringResource(R.string.general_volume_ptt_desc)) { onChange(v.copy(volumeKeysPtt = it)) }
    }
    Group {
        Check(stringResource(R.string.general_headset_ptt), v.headsetPtt, info = stringResource(R.string.general_headset_ptt_desc)) { onChange(v.copy(headsetPtt = it)) }
    }
    Group {
        Check(stringResource(R.string.general_call_mute), v.muteOnCalls, info = stringResource(R.string.general_call_mute_desc)) { onChange(v.copy(muteOnCalls = it)) }
    }
    if (io.github.korppujauho.pocketts.UpdateCheck.enabled) Group {
        Check(stringResource(R.string.general_updates), v.checkUpdates, info = stringResource(R.string.general_updates_desc)) { onChange(v.copy(checkUpdates = it)) }
    }
    Group {
        Check(stringResource(R.string.general_screen_on), v.keepScreenOn, info = stringResource(R.string.general_screen_on_desc)) { onChange(v.copy(keepScreenOn = it)) }
    }
    Group {
        Check(stringResource(R.string.general_spacers), v.joinSpacers, info = stringResource(R.string.general_spacers_desc)) { onChange(v.copy(joinSpacers = it)) }
    }
}

/** How the app looks and what it offers. */
@Composable
private fun Ui(v: VoiceSettings, onChange: (VoiceSettings) -> Unit) {
    Group {
        Check(stringResource(R.string.ui_admin), v.adminTools, info = stringResource(R.string.ui_admin_desc)) { onChange(v.copy(adminTools = it)) }
    }
    Group {
        Check(stringResource(R.string.ui_controls_bottom), v.controlsAtBottom, info = stringResource(R.string.ui_controls_bottom_desc)) { onChange(v.copy(controlsAtBottom = it)) }
    }
}

/**
 * The app's language: the phone's, or one of the translations by its own name. One row showing the chosen one; a tap
 * opens the list (it scrolls when there are many).
 */
@Composable
private fun Language(tag: String, onChange: (String) -> Unit) {
    val choices = listOf("" to stringResource(R.string.ui_language_phone)) + io.github.korppujauho.pocketts.AppLanguage.choices
    val chosen = choices.firstOrNull { it.first == tag }?.second ?: choices.first().second
    var open by remember { mutableStateOf(false) }
    Group(stringResource(R.string.ui_language)) {
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ts.card)
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.DropdownList) { open = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(chosen, color = Ts.bright, fontSize = 15.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.ExpandMore, contentDescription = null, tint = Ts.muted,
                )
            }
            androidx.compose.material3.DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Ts.card) {
                for ((t, name) in choices) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name, color = if (t == tag) Ts.bright else Ts.text, fontWeight = if (t == tag) FontWeight.SemiBold else null) },
                        trailingIcon = if (t == tag) {
                            { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Check, contentDescription = null, tint = Ts.accent) }
                        } else null,
                        onClick = {
                            open = false
                            onChange(t)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Advanced(v: VoiceSettings, onChange: (VoiceSettings) -> Unit) {
    Group(stringResource(R.string.relay_title), info = stringResource(R.string.relay_desc)) {
        TsField(v.turnUrl, { onChange(v.copy(turnUrl = it)) }, stringResource(R.string.relay_address), placeholder = "turn:relay.example.com:3478")
        TsField(v.turnUser, { onChange(v.copy(turnUser = it)) }, stringResource(R.string.relay_user))
        TsField(v.turnPassword, { onChange(v.copy(turnPassword = it)) }, stringResource(R.string.relay_password), password = true)
        Check(stringResource(R.string.relay_only), v.relayOnly) { onChange(v.copy(relayOnly = it)) }
    }
    Group {
        Desc(stringResource(R.string.advanced_file_port))
    }
    // For bug reports: kept on the phone, without names or addresses (see AppLog); the app never sends it
    Group(stringResource(R.string.debug_log_title)) {
        Check(stringResource(R.string.debug_log), v.debugLog, info = stringResource(R.string.debug_log_desc)) { onChange(v.copy(debugLog = it)) }
        if (v.debugLog) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.debug_log_share), modifier = Modifier.weight(1f)) {
                    val uri = io.github.korppujauho.pocketts.AppLog.shareDebugLog(context)
                    if (uri == null) {
                        android.widget.Toast.makeText(context, R.string.debug_log_empty, android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_STREAM, uri).putExtra(android.content.Intent.EXTRA_SUBJECT, "PocketTS debug log")
                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(android.content.Intent.createChooser(send, null))
                    }
                }
                PillButton(stringResource(R.string.debug_log_delete), PillKind.DANGER_TEXT, modifier = Modifier.weight(1f)) {
                    io.github.korppujauho.pocketts.AppLog.clearDebugLog(context)
                    android.widget.Toast.makeText(context, R.string.debug_log_deleted, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            // Test builds only: a crash on purpose, to see it in the debug log (the host name in it must come out)
            if (io.github.korppujauho.pocketts.BuildConfig.DEBUG) {
                PillButton("Crash the app (test build)", PillKind.DANGER_TEXT, modifier = Modifier.fillMaxWidth()) {
                    throw IllegalStateException("A crash on purpose, from voice.example.org")
                }
            }
        }
    }
}

/** A two-way choice in one row, as the server screen's Channels/Chat switch. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ts.card)
            .border(1.dp, Ts.hairline, RoundedCornerShape(10.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (active) Color(0x12FFFFFF) else Color.Transparent)
                    .selectable(selected = active, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) Ts.bright else Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Playback(v: VoiceSettings, actions: SettingsActions, onChange: (VoiceSettings) -> Unit) {
    // Where voices come out: the choice the rest follows (the earpiece brings call audio, and echo cancellation, along)
    Group(stringResource(R.string.playback_output), info = stringResource(R.string.playback_output_earpiece_desc)) {
        Segmented(
            listOf(stringResource(R.string.playback_output_speaker), stringResource(R.string.playback_output_earpiece)),
            if (v.earpiece) 1 else 0,
        ) { onChange(v.copy(earpiece = it == 1)) }
        Check(stringResource(R.string.playback_headset), v.useHeadset, info = stringResource(R.string.playback_headset_desc)) { onChange(v.copy(useHeadset = it)) }
    }
    Group(
        stringResource(R.string.playback_voice_volume), "${if (v.voiceVolumeDb > 0) "+" else ""}${v.voiceVolumeDb} dB",
        info = stringResource(if (v.callAudio) R.string.playback_call_volume else R.string.playback_media_volume),
    ) {
        TsSlider(v.voiceVolumeDb.toFloat(), -20f..12f, 31) { onChange(v.copy(voiceVolumeDb = it.roundToInt())) }
        RangeEnds(stringResource(R.string.quiet), stringResource(R.string.loud))
    }
    Group {
        SecondaryButton(stringResource(R.string.playback_test_sound)) { actions.playTestSound() }
    }
    Group {
        Check(stringResource(R.string.playback_event_sounds), v.uiSounds, info = stringResource(R.string.playback_event_sounds_desc)) { onChange(v.copy(uiSounds = it)) }
    }
    if (v.uiSounds) {
        Group(stringResource(R.string.playback_sound_volume), "${(v.uiSoundVolume * 100).roundToInt()}%") {
            TsSlider(v.uiSoundVolume, 0f..1f, 19) { onChange(v.copy(uiSoundVolume = (it * 20).roundToInt() / 20f)) }
            SecondaryButton(stringResource(R.string.playback_play_sound)) { io.github.korppujauho.pocketts.UiSounds.play(io.github.korppujauho.pocketts.UiSounds.Cue.MESSAGE_IN, v) }
        }
    }
}

@Composable
private fun Capture(v: VoiceSettings, actions: SettingsActions, onChange: (VoiceSettings) -> Unit) {
    Group(stringResource(R.string.capture_mic_volume), "${(v.micVolume * 100).roundToInt()}%") {
        TsSlider(v.micVolume, 0f..3f, 29) { onChange(v.copy(micVolume = (it * 10).roundToInt() / 10f)) }
    }
    Group(stringResource(R.string.capture_activation), info = stringResource(R.string.capture_ptt_desc).takeIf { v.activation == Activation.PTT }) {
        Radio(stringResource(R.string.capture_vad), v.activation == Activation.VAD) { onChange(v.copy(activation = Activation.VAD)) }
        Radio(stringResource(R.string.capture_ptt), v.activation == Activation.PTT) { onChange(v.copy(activation = Activation.PTT)) }
        Radio(stringResource(R.string.capture_continuous), v.activation == Activation.CONTINUOUS) { onChange(v.copy(activation = Activation.CONTINUOUS)) }
    }
    Group(stringResource(R.string.capture_level), info = stringResource(R.string.capture_level_desc)) {
        LevelMeter(actions, if (v.activation == Activation.VAD) v.vadThreshold else null)
    }
    if (v.activation == Activation.VAD) {
        Group(stringResource(R.string.capture_vad_level), "%.1f%%".format(v.vadThreshold * 100), info = stringResource(R.string.capture_vad_level_desc)) {
            TsSlider(v.vadThreshold, 0f..0.1f, 99) { onChange(v.copy(vadThreshold = (it * 1000).roundToInt() / 1000f)) }
        }
        Group(stringResource(R.string.capture_vad_delay), "%.1f s".format(v.vadTailMs / 1000f)) {
            TsSlider(v.vadTailMs / 1000f, 0.1f..2f, 18) { onChange(v.copy(vadTailMs = (it * 10).roundToInt() * 100)) }
        }
    }
    Group(stringResource(R.string.capture_processing)) {
        Check(stringResource(R.string.capture_noise), v.noiseSuppression) { onChange(v.copy(noiseSuppression = it)) }
        Check(stringResource(R.string.capture_ai_noise), v.aiNoiseRemoval, info = stringResource(R.string.capture_ai_noise_desc)) { onChange(v.copy(aiNoiseRemoval = it)) }
        // RNNoise's license (BSD: its notice goes with the app)
        var license by remember { mutableStateOf(false) }
        Text(
            stringResource(R.string.capture_ai_noise_license), color = Ts.accent, fontSize = 12.5.sp,
            modifier = Modifier.clickable { license = true }.padding(vertical = 2.dp),
        )
        if (license) LicenseDialog("licenses/rnnoise.txt", "RNNoise") { license = false }
        // With the earpiece (Playback) it's on and can't be turned off: the earpiece only plays as call audio. On a
        // phone whose loudspeaker doesn't play call audio it's off with the loudspeaker, and can't be turned on.
        // Why it's greyed out is said without a tap; what it does is behind its ⓘ
        val fixed = v.earpiece || v.speakerRefused
        Check(
            stringResource(R.string.capture_echo), v.callAudio, enabled = !fixed,
            info = stringResource(R.string.capture_echo_desc).takeIf { !fixed },
        ) { onChange(v.copy(echoCancellation = it)) }
        if (fixed) Desc(stringResource(if (v.earpiece) R.string.capture_echo_earpiece else R.string.echo_refused))
        Check(stringResource(R.string.capture_agc), v.autoGainControl) { onChange(v.copy(autoGainControl = it)) }
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

/** A library's license, from the app's own files (assets/licenses). */
@Composable
private fun LicenseDialog(asset: String, name: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val text = remember(asset) { runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }.getOrDefault("") }
    TsDialog(
        onDismiss = onDismiss, title = name,
        buttons = { PillButton(stringResource(R.string.close), PillKind.PRIMARY, onClick = onDismiss) },
    ) {
        Text(
            text, color = Ts.muted, fontSize = 11.sp, lineHeight = 14.sp,
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        )
    }
}
