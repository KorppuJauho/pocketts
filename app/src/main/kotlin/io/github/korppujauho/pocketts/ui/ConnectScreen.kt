package io.github.korppujauho.pocketts.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import kotlinx.coroutines.launch
import io.github.korppujauho.pocketts.Favorite
import io.github.korppujauho.pocketts.R

/** What the connect screen can ask for. */
interface ConnectActions {
    fun connect(address: String, nickname: String, password: String, remember: Boolean, favorite: Boolean)
    fun connectFavorite(f: Favorite)
    fun saveFavorite(old: Favorite?, new: Favorite)
    fun removeFavorite(f: Favorite)
    fun toggleAuto(f: Favorite)
    fun renameFolder(folder: String, name: String) {}
    /** A favourite dragged to place [to] in its folder; a folder moved one place up or down. */
    fun moveFavorite(f: Favorite, to: Int) {}
    fun moveFolder(folder: String, up: Boolean) {}
    /** Remembered: the folders folded shut, and whether the Connect and Community panel is folded to its bar. */
    fun setFolded(folders: Set<String>) {}
    fun setConnectHidden(hidden: Boolean) {}
}

/**
 * WebTS's connect window: the favourites in their folders (one tap connects), then an address to type or TeamSpeak's
 * community servers. That last panel folds shut to its bar ([connectHidden]), as the folders do, for those who only use
 * their favourites.
 */
@Composable
fun ConnectScreen(
    nickname: String,
    address: String,
    password: String,
    favorites: List<Favorite>,
    error: String?,
    actions: ConnectActions,
    voice: io.github.korppujauho.pocketts.VoiceSettings = io.github.korppujauho.pocketts.VoiceSettings(),
    settingsActions: SettingsActions? = null,
    onDismissError: () -> Unit = {},
    folded: Set<String> = emptySet(),
    connectHidden: Boolean = false,
) {
    var settings by remember { mutableStateOf(false) }
    var nick by remember { mutableStateOf(nickname) }
    var addr by remember { mutableStateOf(address) }
    var pw by remember { mutableStateOf(password) }
    var remember by remember { mutableStateOf(password.isNotEmpty()) }
    var favorite by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Favorite?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var shut by remember { mutableStateOf(folded) }
    var hidden by remember { mutableStateOf(connectHidden) }
    val known = favorites.any { io.github.korppujauho.pocketts.Favorites.same(it.address, addr) }
    val folders = io.github.korppujauho.pocketts.Favorites.byFolder(favorites)
    val fold = { to: Boolean ->
        hidden = to
        actions.setConnectHidden(to)
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        AppHeader { if (settingsActions != null) SettingsGear { settings = true } }
        // Above the scrolling part: in view whether the connect came from a favourite or the form
        error?.let { WarningBanner(it, onDismissError, Modifier.padding(top = 15.dp)) }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            for ((folder, inFolder) in folders) {
                val open = folder !in shut
                FolderPanel(
                    folderName(folder), open,
                    onToggle = {
                        shut = if (open) shut + folder else shut - folder
                        actions.setFolded(shut)
                    },
                    onRename = { renaming = folder },
                ) {
                    FavoriteRows(inFolder, actions, onEdit = { editing = it })
                }
            }
            // Connecting by address, and TeamSpeak's community servers: two pages of one panel, its switch on top, and
            // a swipe goes between them too
            val pages = androidx.compose.foundation.pager.rememberPagerState { 2 }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            // A newer version is said in the form: folded shut, here instead
            if (hidden) UpdateCard()
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ts.panel)
                    .border(1.dp, Ts.hairline, RoundedCornerShape(12.dp)),
            ) {
                PageSwitch(
                    pages.currentPage, open = !hidden,
                    // A page's name opens the panel on it
                    onPage = {
                        val shutNow = hidden
                        if (shutNow) fold(false)
                        scope.launch { if (shutNow) pages.scrollToPage(it) else pages.animateScrollToPage(it) }
                    },
                    onFold = { fold(!hidden) },
                )
                if (!hidden) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF111214)))
                if (!hidden) androidx.compose.foundation.pager.HorizontalPager(pages, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // The same nickname on both pages
                        TsField(nick, { nick = it }, stringResource(R.string.nickname))
                        if (page == 0) {
                            TsField(addr, { addr = it }, stringResource(R.string.server_address), placeholder = "ts.example.com", keyboard = KeyboardType.Uri)
                            TsField(pw, { pw = it }, stringResource(R.string.server_password), password = true, imeAction = ImeAction.Go)
                            CheckRow(stringResource(R.string.remember_password), remember) { remember = it }
                            // Always there; ticked (and fixed) for a server that already is a favourite
                            CheckRow(stringResource(if (known) R.string.favourites_in else R.string.favourites_save), favorite || known, enabled = !known) { favorite = it }
                            UpdateCard()
                            PrimaryButton(
                                stringResource(R.string.connect), enabled = nick.isNotBlank() && addr.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                            ) { actions.connect(addr.trim(), nick.trim(), pw, remember, favorite && !known) }
                        } else {
                            CommunityServers(nick.trim()) { actions.connect(it, nick.trim(), "", false, false) }
                        }
                    }
                }
            }
        }
    }

    if (settings && settingsActions != null) {
        // A nickname saved in the settings shows in the form at once
        val saving = remember(settingsActions) {
            object : SettingsActions by settingsActions {
                override fun save(voice: io.github.korppujauho.pocketts.VoiceSettings, nickname: String) {
                    settingsActions.save(voice, nickname)
                    if (nickname.isNotEmpty()) nick = nickname
                }
            }
        }
        SettingsDialog(voice, nick, saving, onDismiss = { settings = false })
    }
    editing?.let { f ->
        FavoriteDialog(
            f, onSave = { actions.saveFavorite(f, it); editing = null }, onDelete = { actions.removeFavorite(f); editing = null },
            folders = folders.map { it.first },
        ) { editing = null }
    }
    renaming?.let { folder ->
        val first = stringResource(R.string.favourites)
        val at = folders.indexOfFirst { it.first == folder }
        FolderDialog(
            folderName(folder),
            onUp = if (at > 0) ({ actions.moveFolder(folder, true) }) else null,
            onDown = if (at in 0 until folders.lastIndex) ({ actions.moveFolder(folder, false) }) else null,
            onSave = { typed ->
                val name = if (typed == first) "" else typed // the first folder's own name is that folder
                // Folded shut, it stays so under its new name
                if (folder in shut) {
                    shut = shut - folder + name
                    actions.setFolded(shut)
                }
                actions.renameFolder(folder, name)
                renaming = null
            },
        ) { renaming = null }
    }
}

/** A folder's name as shown: the first one is "Favourites" until it's renamed. */
@Composable
internal fun folderName(folder: String) = folder.ifEmpty { stringResource(R.string.favourites) }

/** A folder of favourites: its name, and a tap on it folds it shut or open (holding it opens its edit window). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderPanel(name: String, open: Boolean, onToggle: () -> Unit, onRename: () -> Unit, content: @Composable () -> Unit) {
    val rename = stringResource(R.string.folder_edit)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ts.panel)
            .border(1.dp, Ts.hairline, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Ts.card)
                .combinedClickable(
                    onClickLabel = stringResource(if (open) R.string.folder_fold else R.string.folder_unfold),
                    onLongClickLabel = rename, onLongClick = onRename, onClick = onToggle,
                )
                .semantics {
                    heading()
                    customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(rename) { onRename(); true })
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name, color = Ts.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Icon(
                if (open) androidx.compose.material.icons.Icons.Rounded.ExpandLess else androidx.compose.material.icons.Icons.Rounded.ExpandMore,
                contentDescription = null, tint = Ts.muted,
            )
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF111214)))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/**
 * A folder's edit window: its name, and its place among the folders ([onUp] and [onDown] move it at once; null at the
 * top and at the bottom).
 */
@Composable
private fun FolderDialog(name: String, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    TsDialog(
        onDismiss = onDismiss, title = stringResource(R.string.folder_edit),
        icon = androidx.compose.material.icons.Icons.Rounded.Edit, tint = Ts.accent,
        buttons = {
            Spacer(Modifier.weight(1f))
            PillButton(stringResource(R.string.cancel), onClick = onDismiss)
            PillButton(stringResource(R.string.save), PillKind.PRIMARY, enabled = text.isNotBlank()) { onSave(text.trim()) }
        },
    ) {
        TsField(text, { text = it.take(40) }, stringResource(R.string.folder_name))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(stringResource(R.string.a11y_move_up), enabled = onUp != null, modifier = Modifier.weight(1f)) { onUp?.invoke() }
            PillButton(stringResource(R.string.a11y_move_down), enabled = onDown != null, modifier = Modifier.weight(1f)) { onDown?.invoke() }
        }
    }
}

/**
 * The panel's top bar: its two pages, Connect and the community servers, as the server screen's Channels/Chat switch,
 * and at its end the arrow that folds the panel shut to this bar, or [open] again, as a folder's does.
 */
@Composable
private fun PageSwitch(page: Int, open: Boolean, onPage: (Int) -> Unit, onFold: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ts.card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((i, label) in listOf(stringResource(R.string.connect), stringResource(R.string.community_tab)).withIndex()) {
            val active = open && i == page // folded shut, neither page is the one shown
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (active) Color(0x12FFFFFF) else Color.Transparent)
                    .selectable(selected = active, role = androidx.compose.ui.semantics.Role.Tab) { onPage(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) Ts.bright else Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(7.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onFold),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                if (open) androidx.compose.material.icons.Icons.Rounded.ExpandLess else androidx.compose.material.icons.Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (open) R.string.connect_hide else R.string.connect_show), tint = Ts.muted,
            )
        }
    }
}

/** TeamSpeak's community servers (see [io.github.korppujauho.pocketts.CommunityServers]): one tap joins, with [nick]. */
@Composable
private fun CommunityServers(nick: String, onJoin: (String) -> Unit) {
    Text(
        stringResource(if (nick.isBlank()) R.string.community_desc_no_nick else R.string.community_desc),
        color = Ts.muted, fontSize = 12.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 2.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in io.github.korppujauho.pocketts.CommunityServers.list) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ts.card)
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
                    .clickable(enabled = nick.isNotBlank()) { onJoin(s.address) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(s.name), color = if (nick.isNotBlank()) Ts.text else Ts.muted, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(s.address, color = Ts.muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * A folder's favourites: a tap connects, and a row held is dragged up or down to another place among them (the others
 * make room; let go, it stays there).
 */
@Composable
private fun FavoriteRows(list: List<Favorite>, actions: ConnectActions, onEdit: (Favorite) -> Unit) {
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var rowHeight by remember { mutableStateOf(0) }
    var dragged by remember { mutableStateOf<Favorite?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    val slot = rowHeight + gap
    val from = list.indexOf(dragged)
    // Where it would land: its own place moved by whole rows
    val to = if (from < 0 || slot <= 0f) from else (from + Math.round(dragY / slot)).coerceIn(0, list.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEachIndexed { i, f ->
            val mine = f == dragged
            // The rows between where it was and where it would land step aside
            val aside by androidx.compose.animation.core.animateFloatAsState(
                when {
                    from < 0 || mine -> 0f
                    i in (from + 1)..to -> -slot
                    i in to until from -> slot
                    else -> 0f
                },
                label = "aside",
            )
            androidx.compose.runtime.key(f) {
                FavoriteRow(
                    f, lifted = mine,
                    modifier = Modifier.onSizeChanged { rowHeight = it.height }
                        .zIndex(if (mine) 1f else 0f).graphicsLayer { translationY = if (mine) dragY else aside },
                    onConnect = { actions.connectFavorite(f) }, onEdit = { onEdit(f) },
                    onUp = if (i > 0) ({ actions.moveFavorite(f, i - 1) }) else null,
                    onDown = if (i < list.lastIndex) ({ actions.moveFavorite(f, i + 1) }) else null,
                    onDragStart = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        dragged = f
                        dragY = 0f
                    },
                    onDrag = { dragY += it },
                    onDragEnd = { moved ->
                        if (moved && to >= 0 && to != from) actions.moveFavorite(f, to)
                        dragged = null
                        dragY = 0f
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    f: Favorite, lifted: Boolean, modifier: Modifier, onConnect: () -> Unit, onEdit: () -> Unit, onUp: (() -> Unit)?, onDown: (() -> Unit)?,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: (moved: Boolean) -> Unit,
) {
    val connect = stringResource(R.string.connect)
    val up = stringResource(R.string.a11y_move_up)
    val down = stringResource(R.string.a11y_move_down)
    // The latest ones, for the gestures that outlive a redraw
    val start by androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val drag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val end by androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    val tap by androidx.compose.runtime.rememberUpdatedState(onConnect)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Ts.card)
                .border(1.dp, if (lifted) Ts.accent else Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
                .semantics {
                    role = androidx.compose.ui.semantics.Role.Button
                    onClick(connect) { tap(); true }
                    customActions = listOfNotNull(
                        onUp?.let { androidx.compose.ui.semantics.CustomAccessibilityAction(up) { it(); true } },
                        onDown?.let { androidx.compose.ui.semantics.CustomAccessibilityAction(down) { it(); true } },
                    )
                }
                // A tap connects; held, it isn't a tap any more (the drag below takes it, and letting go connects nothing)
                .pointerInput(Unit) { detectTapGestures(onLongPress = {}, onTap = { tap() }) }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start() },
                        onDrag = { change, amount -> change.consume(); drag(amount.y) },
                        onDragEnd = { end(true) }, onDragCancel = { end(false) },
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(f.label, color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Removing is in its edit window (Delete), where it isn't tapped by mistake
        FavButton(androidx.compose.material.icons.Icons.Rounded.Edit, stringResource(R.string.edit), onEdit)
    }
}

@Composable
private fun FavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Ts.card)
            .border(1.dp, Ts.buttonBorder, RoundedCornerShape(8.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = label, tint = Ts.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked, onChange, enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Ts.accent, uncheckedColor = Ts.muted, disabledCheckedColor = Ts.accent.copy(alpha = 0.5f)),
        )
        Text(text, color = if (enabled) Ts.text else Ts.muted, fontSize = 14.sp)
    }
}

/**
 * WebTS's "Edit favourite": its name, address, the nickname used there, its password, the channel to join (with its
 * password), connect automatically, and the folder it's in: one of [folders], or a new one by its name.
 */
@Composable
fun FavoriteDialog(
    f: Favorite, onSave: (Favorite) -> Unit, onDelete: (() -> Unit)?, folders: List<String> = listOf(f.folder), onDismiss: () -> Unit,
) {
    var folder by remember { mutableStateOf(f.folder) }
    var newFolder by remember { mutableStateOf<String?>(null) } // a new folder's name being typed, when that was chosen
    val firstFolder = stringResource(R.string.favourites)
    var name by remember { mutableStateOf(f.name) }
    var address by remember { mutableStateOf(f.address) }
    var nick by remember { mutableStateOf(f.nickname) }
    var pw by remember { mutableStateOf(f.password) }
    var auto by remember { mutableStateOf(f.auto) }
    var channel by remember { mutableStateOf(f.channel) }
    var channelPw by remember { mutableStateOf(f.channelPassword) }
    var filePort by remember { mutableStateOf(f.filePort?.toString().orEmpty()) }
    var advanced by remember { mutableStateOf(f.filePort != null) }
    TsDialog(
        onDismiss = onDismiss, title = stringResource(if (onDelete != null) R.string.favourite_edit else R.string.favourite_add),
        icon = androidx.compose.material.icons.Icons.Rounded.Star, tint = Ts.amber, subtitle = f.address.takeIf { onDelete != null },
        // Delete on the left of the same row as Cancel and Save
        buttons = {
            if (onDelete != null) PillButton(stringResource(R.string.delete), PillKind.DANGER_TEXT, onClick = onDelete)
            Spacer(Modifier.weight(1f))
            PillButton(stringResource(R.string.cancel), onClick = onDismiss)
            PillButton(stringResource(R.string.save), PillKind.PRIMARY, enabled = address.isNotBlank()) {
                // "Games / Lobby" isn't found: no spaces around the path's slashes
                val ch = channel.split('/').joinToString("/") { it.trim() }
                onSave(
                    Favorite(
                        address.trim(), name.trim(), nick.trim(), pw, auto, filePort.toIntOrNull()?.takeIf { it in 1..65535 },
                        ch, if (ch.isEmpty()) "" else channelPw,
                        if (ch == f.channel) f.channelId else 0, // another channel typed: its id is learned again
                        // A new folder left without a name: where it was; the first folder's own name is that folder
                        newFolder?.trim()?.ifEmpty { null }?.let { if (it == firstFolder) "" else it } ?: folder,
                    )
                )
            }
        },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TsField(name, { name = it }, stringResource(R.string.favourite_name), placeholder = address)
                TsField(address, { address = it }, stringResource(R.string.server_address), keyboard = KeyboardType.Uri)
                TsField(nick, { nick = it }, stringResource(R.string.favourite_nickname))
                TsField(pw, { pw = it }, stringResource(R.string.server_password), password = true)
                TsField(channel, { channel = it }, stringResource(R.string.favourite_channel), placeholder = stringResource(R.string.favourite_channel_hint))
                if (channel.isNotBlank()) {
                    Text(stringResource(R.string.favourite_channel_desc), color = Ts.muted, fontSize = 12.sp)
                    TsField(channelPw, { channelPw = it }, stringResource(R.string.channel_password), password = true)
                }
                FolderChoice(folders, folder, newFolder, onFolder = { folder = it; newFolder = null }, onNew = { newFolder = it })
                CheckRow(stringResource(R.string.favourite_auto), auto) { auto = it }
                // Advanced: folded away unless used
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { advanced = !advanced }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.advanced), color = Ts.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Icon(
                        if (advanced) androidx.compose.material.icons.Icons.Rounded.ExpandLess else androidx.compose.material.icons.Icons.Rounded.ExpandMore,
                        contentDescription = null, tint = Ts.muted,
                    )
                }
                if (advanced) {
                    TsField(
                        filePort, { v -> filePort = v.filter { it.isDigit() }.take(5) }, stringResource(R.string.favourite_file_port),
                        placeholder = stringResource(R.string.favourite_file_port_hint), keyboard = KeyboardType.Number,
                    )
                    Text(stringResource(R.string.favourite_file_port_desc), color = Ts.muted, fontSize = 12.sp)
                }
            }
    }
}

/** The favourite's folder: a row showing it, which opens the list of folders and "New folder…" (then its name is typed). */
@Composable
private fun FolderChoice(folders: List<String>, folder: String, newFolder: String?, onFolder: (String) -> Unit, onNew: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.favourite_folder)
    Box {
        // In the other fields' look; the whole of it opens the list
        TsField(
            if (newFolder != null) stringResource(R.string.favourite_folder_new) else folderName(folder), {}, label, readOnly = true,
            trailing = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.ExpandMore, contentDescription = null, tint = Ts.muted) },
        )
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(12.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.DropdownList) { open = true },
        )
        androidx.compose.material3.DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Ts.card) {
            for (name in folders) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(folderName(name), color = Ts.text) },
                    onClick = { open = false; onFolder(name) },
                )
            }
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.favourite_folder_new), color = Ts.accent) },
                onClick = { open = false; onNew("") },
            )
        }
    }
    if (newFolder != null) TsField(newFolder, { onNew(it.take(40)) }, stringResource(R.string.folder_name))
}

/** A newer PocketTS on GitHub: Download opens its release page, Not now hides it until a later one. */
@Composable
private fun UpdateCard() {
    val update by io.github.korppujauho.pocketts.UpdateCheck.available.collectAsState()
    val r = update ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    PopCard {
        Text(stringResource(R.string.update_title, r.version), color = Ts.bright, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(stringResource(R.string.update_text, io.github.korppujauho.pocketts.BuildConfig.VERSION_NAME), color = Ts.muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            PillButton(stringResource(R.string.not_now)) { io.github.korppujauho.pocketts.UpdateCheck.dismiss(context) }
            PillButton(stringResource(R.string.download), PillKind.PRIMARY) { uri.openUri(r.url) }
        }
    }
}
