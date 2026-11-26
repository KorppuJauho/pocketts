package io.github.korppujauho.pocketts.ui

import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.pocketts.R

/**
 * An identity as the screens show it: its [name] (only the person sees it), the [nickname] it connects with, whether
 * it's the default one, its security level (null when it can't be read, then [problem] says why), and how many
 * favourites connect with it.
 */
data class IdentityInfo(
    val key: String,
    val name: String,
    val nickname: String,
    val isDefault: Boolean,
    val level: Int? = null,
    val problem: String? = null,
    val favourites: Int = 0,
) {
    val label: String get() = name.ifBlank { nickname }
}

/**
 * The identities in a box that folds as the favourites' folders do: its bar says how many there are and which is the
 * default, and a tap on it opens or shuts it. Open, one row each (the default starred; a tap opens it), and New
 * identity at the end. [about] is the ⓘ at the bar's end.
 */
@Composable
internal fun IdentityList(
    identities: List<IdentityInfo>, open: Boolean, onToggle: () -> Unit, onOpen: (IdentityInfo) -> Unit, onNew: () -> Unit,
    about: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ts.panel)
            .border(1.dp, Ts.hairline, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Ts.card)
                .clickable(onClickLabel = stringResource(if (open) R.string.folder_fold else R.string.folder_unfold), onClick = onToggle)
                .semantics { heading() }
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.identities_title), color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            ValueBadge(identities.size.toString())
            // Folded, the bar still says which one connects
            val default = identities.firstOrNull { it.isDefault }
            if (!open && default != null) {
                Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.identity_default), tint = Ts.amber, modifier = Modifier.size(14.dp))
                Text(default.label, color = Ts.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            about()
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Ts.muted)
            Spacer(Modifier.size(6.dp))
        }
        if (!open) return@Column
        identities.forEach { e ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ts.line))
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(e) }.padding(start = 14.dp, end = 10.dp).heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (e.isDefault) {
                    Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.identity_default), tint = Ts.amber, modifier = Modifier.size(16.dp))
                } else {
                    Spacer(Modifier.size(16.dp))
                }
                // One line: the name, then the nickname and level after it (both shortened when long)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        e.label, color = Ts.bright, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val level = e.level?.let { stringResource(R.string.identity_level, it) }
                    val line = listOfNotNull(e.nickname.ifBlank { null }.takeIf { e.name.isNotBlank() }, level).joinToString(" · ")
                    Text(line, color = Ts.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Ts.muted, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ts.line))
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onNew).padding(start = 14.dp, end = 10.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = Ts.accent, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.identity_new), color = Ts.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/**
 * One identity: its name and nickname (saved with Save), and at once: Make default, Export (what the file means
 * first), raising its security level, and Delete (what's lost first). The last identity can't be deleted, only wiped:
 * a new one is made in its place at the next connect.
 */
@Composable
internal fun IdentityDialog(e: IdentityInfo, all: List<IdentityInfo>, actions: SettingsActions, onClose: () -> Unit) {
    var name by remember { mutableStateOf(e.name) }
    var nick by remember { mutableStateOf(e.nickname) }
    var status by remember { mutableStateOf<String?>(null) }
    var isDefault by remember { mutableStateOf(e.isDefault) }
    var level by remember { mutableStateOf(e.level) }
    var warning by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var raising by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.identity_saved)
    val saveFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) status = actions.exportIdentity(e.key, uri) ?: saved }
    val last = all.size <= 1
    TsDialog(
        onDismiss = onClose, title = e.label, icon = Icons.Rounded.Badge,
        subtitle = stringResource(R.string.identity_default).takeIf { isDefault },
        buttons = {
            PillButton(stringResource(if (last) R.string.identity_wipe else R.string.delete), PillKind.DANGER_TEXT) { deleting = true }
            Spacer(Modifier.weight(1f))
            PillButton(stringResource(R.string.cancel), onClick = onClose)
            PillButton(stringResource(R.string.save), PillKind.PRIMARY) {
                actions.editIdentity(e.key, name, nick)
                onClose()
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TsField(name, { name = it.take(40) }, stringResource(R.string.identity_name))
            TsField(nick, { nick = it.take(30) }, stringResource(R.string.nickname))
            e.problem?.let { Desc(it) }
            // What can be done with it, one row each, as the menus' rows
            val rows = buildList<@Composable (Boolean) -> Unit> {
                if (!isDefault) add { first ->
                    ActionRow(Icons.Rounded.Star, stringResource(R.string.identity_make_default), first, tint = Ts.amber) {
                        actions.makeDefaultIdentity(e.key)
                        isDefault = true
                    }
                }
                if (e.problem == null) add { first -> ActionRow(Icons.Rounded.FileUpload, stringResource(R.string.identity_export), first, more = true) { warning = true } }
                // The security level: some servers need a higher one than a new identity has
                level?.let { current ->
                    add { first ->
                        ActionRow(
                            Icons.Rounded.Shield, stringResource(R.string.level_title), first, more = true,
                            detail = stringResource(R.string.level_raise_button), trailing = { ValueBadge("$current") },
                        ) { raising = true }
                    }
                }
            }
            if (rows.isNotEmpty()) ActionCard { rows.forEachIndexed { i, row -> row(i == 0) } }
            status?.let { Desc(it) }
            if (level != null) Desc(stringResource(R.string.level_desc))
        }
    }
    if (raising) {
        RaiseLevelDialog(level ?: 8, identity = e.key, onClose = {
            raising = false
            level = actions.identities().firstOrNull { it.key == e.key }?.level
        })
    }
    // Export: what the file means first, then a file of the person's choosing (not shared to an app)
    if (warning) {
        TsDialog(
            onDismiss = { warning = false }, title = stringResource(R.string.identity_export_title), icon = Icons.Rounded.Warning,
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
    // Delete (or wipe the last one): what's lost first, with a way to keep a copy
    if (deleting) {
        // Its favourites go to the default one, or, when this is the default one, to the first left
        val next = (if (e.isDefault || isDefault) all.firstOrNull { it.key != e.key } else all.firstOrNull { it.isDefault })?.label
        TsDialog(
            onDismiss = { deleting = false }, icon = Icons.Rounded.Warning, tint = Ts.red, subtitle = stringResource(R.string.cant_be_undone),
            title = if (last) stringResource(R.string.identity_wipe_title) else stringResource(R.string.identity_delete_title, e.label),
            buttons = {
                PillButton(stringResource(R.string.cancel)) { deleting = false }
                PillButton(stringResource(if (last) R.string.identity_wipe else R.string.delete), PillKind.DANGER) {
                    actions.deleteIdentity(e.key)
                    deleting = false
                    onClose()
                }
            },
        ) {
            Desc(stringResource(if (last) R.string.identity_wipe_what else R.string.identity_delete_what))
            if (!last && e.favourites > 0 && next != null) {
                Desc(pluralStringResource(R.plurals.identity_delete_favourites, e.favourites, e.favourites, next))
            }
            Desc(stringResource(R.string.identity_wipe_back))
        }
    }
}

/**
 * A new identity: its name and nickname, then made new or imported from a TeamSpeak app (pasted, or its .ini file).
 * It's someone new to every server, which is said first.
 */
@Composable
internal fun NewIdentityDialog(actions: SettingsActions, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(0) } // 0: made new, 1: imported
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            error = actions.newIdentityFile(name, nick, uri)
            if (error == null) onClose()
        }
    }
    TsDialog(
        onDismiss = onClose, title = stringResource(R.string.identity_new), icon = Icons.Rounded.PersonAdd,
        buttons = {
            PillButton(stringResource(R.string.cancel), onClick = onClose)
            PillButton(
                stringResource(if (importing == 1) R.string.identity_import else R.string.identity_create), PillKind.PRIMARY,
                enabled = name.isNotBlank() && (importing == 0 || text.isNotBlank()),
            ) {
                error = actions.newIdentity(name, nick, text.takeIf { importing == 1 })
                if (error == null) onClose()
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Desc(stringResource(R.string.identity_new_desc))
            TsField(name, { name = it.take(40) }, stringResource(R.string.identity_name))
            TsField(nick, { nick = it.take(30) }, stringResource(R.string.nickname))
            Segmented(listOf(stringResource(R.string.identity_make_new), stringResource(R.string.identity_import)), importing) { importing = it }
            if (importing == 1) {
                Desc(stringResource(R.string.identity_import_desc))
                // Hidden like a password while it's on screen
                TsField(text, { text = it }, stringResource(R.string.identity_field), password = true)
                PillButton(stringResource(R.string.identity_choose_file), enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    pickFile.launch(arrayOf("*/*"))
                }
            }
            error?.let { Desc(it) }
        }
    }
}

/**
 * Which identity to connect with, in the fields' look; the whole of it opens the list. With [withDefault] the first
 * choice is "Default" (key ""), which follows whichever identity is the default one.
 */
@Composable
internal fun IdentityChoice(identities: List<IdentityInfo>, key: String, withDefault: Boolean = false, onChoose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val default = identities.firstOrNull { it.isDefault }
    val defaultLabel = stringResource(R.string.identity_choice_default, default?.label.orEmpty())
    val shown = if (withDefault && key.isEmpty()) defaultLabel else (identities.firstOrNull { it.key == key } ?: default)?.label.orEmpty()
    Box {
        TsField(
            shown, {}, stringResource(R.string.identity_choice), readOnly = true,
            trailing = { Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Ts.muted) },
        )
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).clickable(role = Role.DropdownList) { open = true })
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Ts.card) {
            if (withDefault) DropdownMenuItem(text = { Text(defaultLabel, color = Ts.text) }, onClick = { open = false; onChoose("") })
            for (e in identities) {
                DropdownMenuItem(
                    text = { Text(e.label, color = Ts.text) },
                    trailingIcon = if (!withDefault && e.isDefault) {
                        { Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.identity_default), tint = Ts.amber, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = { open = false; onChoose(e.key) },
                )
            }
        }
    }
}
