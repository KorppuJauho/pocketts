package io.github.korppujauho.pocketts.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.pocketts.R
import io.github.korppujauho.pocketts.Session
import androidx.compose.ui.res.stringResource
import io.github.korppujauho.pocketts.proto.ServerModel

/** Moving someone: the server's channels as a tree, tap one. Spacers only when they may be joined (Settings). */
@Composable
fun MoveDialog(
    person: ServerModel.Client,
    tree: List<ServerModel.Node>,
    spacers: Boolean,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember(tree, spacers) {
        buildList {
            fun add(nodes: List<ServerModel.Node>, depth: Int) {
                for (n in nodes) {
                    if (spacers || n.channel.spacer == null) add(n.channel to depth)
                    add(n.children, depth + 1)
                }
            }
            add(tree, 0)
        }
    }
    TsDialog(
        onDismiss = onDismiss, title = stringResource(R.string.admin_move_title, person.nickname), icon = Icons.Rounded.SwapVert,
        subtitle = stringResource(R.string.admin_move_subtitle),
        buttons = { PillButton(stringResource(R.string.cancel), onClick = onDismiss) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            for ((channel, depth) in rows) {
                val here = channel.id == person.channel
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !here) { onMove(channel.id) }
                        .padding(start = (8 + depth * 16).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        channel.displayName.ifBlank { channel.name }, color = if (here) Ts.muted else Ts.text, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (here) Text(stringResource(R.string.admin_here_now), color = Ts.muted, fontSize = 12.sp)
                    if (channel.hasPassword) Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.admin_has_password), tint = Ts.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Kicking someone out of their channel or off the server, with a reason they see. */
@Composable
fun KickDialog(person: ServerModel.Client, onKick: (fromServer: Boolean, reason: String) -> Unit, onDismiss: () -> Unit) {
    var fromServer by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    TsDialog(
        onDismiss = onDismiss, title = stringResource(R.string.admin_kick_title, person.nickname), icon = Icons.AutoMirrored.Rounded.Logout, tint = Ts.red,
        subtitle = stringResource(R.string.admin_kick_subtitle),
        buttons = {
            PillButton(stringResource(R.string.cancel), onClick = onDismiss)
            PillButton(stringResource(R.string.admin_kick), PillKind.DANGER) { onKick(fromServer, reason) }
        },
    ) {
        Choice(stringResource(R.string.admin_kick_channel), !fromServer) { fromServer = false }
        Choice(stringResource(R.string.admin_kick_server), fromServer) { fromServer = true }
        TsField(reason, { reason = it.take(Session.REASON_MAX) }, stringResource(R.string.admin_reason))
    }
}

/** Banning someone for a while or for good: their identity and address, and the server removes them at once. */
@Composable
fun BanDialog(person: ServerModel.Client, onBan: (seconds: Long, reason: String) -> Unit, onDismiss: () -> Unit) {
    var seconds by remember { mutableLongStateOf(3600L) }
    var reason by remember { mutableStateOf("") }
    TsDialog(
        onDismiss = onDismiss, title = stringResource(R.string.admin_ban_title, person.nickname), icon = Icons.Rounded.Block, tint = Ts.red,
        subtitle = stringResource(R.string.admin_ban_subtitle),
        buttons = {
            PillButton(stringResource(R.string.cancel), onClick = onDismiss)
            PillButton(stringResource(R.string.admin_ban), PillKind.DANGER) { onBan(seconds, reason) }
        },
    ) {
        for ((label, s) in BAN_TIMES) Choice(stringResource(label), seconds == s) { seconds = s }
        TsField(reason, { reason = it.take(Session.REASON_MAX) }, stringResource(R.string.admin_reason))
    }
}

/** How long a ban lasts: 0 is for good. */
private val BAN_TIMES = listOf(
    R.string.ban_10_minutes to 600L, R.string.ban_1_hour to 3600L, R.string.ban_1_day to 86_400L, R.string.ban_1_week to 604_800L,
    R.string.ban_permanently to 0L,
)

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = Ts.accent, unselectedColor = Ts.muted))
        }
        Text(text, color = Ts.text, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** The person menu's admin row (Settings › UI › Show admin functions), and their server groups under it. */
@Composable
fun AdminTiles(onMove: () -> Unit, onKick: () -> Unit, onBan: () -> Unit, onGroups: (() -> Unit)? = null) {
    // As the channel menu's: one row each, the ones that remove someone in red, last
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.admin_row), color = Ts.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        ActionCard {
            ActionRow(Icons.Rounded.SwapVert, stringResource(R.string.admin_move), true, more = true, onClick = onMove)
            if (onGroups != null) ActionRow(Icons.Rounded.Groups, stringResource(R.string.admin_groups), false, more = true, onClick = onGroups)
            ActionRow(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.admin_kick), false, more = true, tint = Ts.red, onClick = onKick)
            ActionRow(Icons.Rounded.Block, stringResource(R.string.admin_ban), false, more = true, tint = Ts.red, onClick = onBan)
        }
    }
}

/** The channel groups shown for someone: in [channel] (the one they're in), the groups people can be given, and our powers there. */
data class ChannelGroups(val channel: String, val groups: List<ServerModel.Group>, val powers: ServerModel.GroupPowers)

/**
 * A person's groups, each with its icon. Their server groups are ticked: a tick puts them in at once, taking it off
 * takes them out. Their channel group in the channel they're in is one choice, which replaces the one they had. What
 * shows follows what the server then says. Groups our permissions don't reach, and the server's default group, are
 * greyed out.
 */
@Composable
fun ServerGroupsDialog(
    person: ServerModel.Client, choices: List<ServerModel.GroupChoice>, onChange: (ServerModel.Group, Boolean) -> Unit, onDismiss: () -> Unit,
    server: String = "", channelGroups: ChannelGroups? = null, onChannelGroup: (ServerModel.Group) -> Unit = {},
    error: String? = null, onDismissError: () -> Unit = {},
) {
    TsDialog(
        onDismiss = onDismiss, title = stringResource(R.string.admin_groups), subtitle = person.nickname, icon = Icons.Rounded.Groups,
        buttons = { PillButton(stringResource(R.string.close), PillKind.PRIMARY, onClick = onDismiss) },
    ) {
        if (choices.isEmpty() && channelGroups?.groups.isNullOrEmpty()) {
            Desc(stringResource(R.string.admin_groups_none))
            return@TsDialog
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Why a change didn't go through, here in the window rather than behind it
            if (error != null) WarningBanner(error, onDismissError, inset = 0.dp)
            if (choices.isNotEmpty()) {
                SectionLabel(stringResource(R.string.admin_server_groups))
                GroupCard {
                    choices.forEachIndexed { i, c ->
                        val member = c.group.id in person.serverGroups
                        GroupRow(
                            c.group, server, member, enabled = if (member) c.canRemove else c.canAdd, first = i == 0,
                            toggle = Modifier.toggleable(member, enabled = if (member) c.canRemove else c.canAdd, role = Role.Checkbox) { onChange(c.group, it) },
                        ) { enabled ->
                            Checkbox(member, onCheckedChange = null, enabled = enabled, colors = checkColors())
                        }
                    }
                }
            }
            channelGroups?.takeIf { it.groups.isNotEmpty() }?.let { cg ->
                Spacer(Modifier.height(4.dp))
                SectionLabel(stringResource(R.string.admin_channel_group, cg.channel))
                val current = cg.groups.firstOrNull { it.id == person.channelGroup }
                GroupCard {
                    cg.groups.forEachIndexed { i, g ->
                        val chosen = g.id == person.channelGroup
                        val enabled = chosen || cg.powers.canSwitch(current, g)
                        GroupRow(
                            g, server, chosen, enabled, first = i == 0,
                            toggle = Modifier.selectable(chosen, enabled = enabled && !chosen, role = Role.RadioButton) { onChannelGroup(g) },
                        ) { on ->
                            RadioButton(
                                chosen, onClick = null, enabled = on,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Ts.accent, unselectedColor = Ts.muted,
                                    disabledSelectedColor = Ts.accent.copy(alpha = 0.4f), disabledUnselectedColor = Ts.faint,
                                ),
                            )
                        }
                    }
                }
            }
            Desc(stringResource(R.string.admin_groups_desc))
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, color = Ts.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))

/** The rows of one kind of group, together in a box as the popups' other boxes. */
@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pop.field).border(1.dp, Ts.groupBorder, RoundedCornerShape(16.dp)),
    content = content,
)

/**
 * A group: its icon (the server's own picture, or TeamSpeak's built-in shield; a faint outline when it has none),
 * its name, and the tick or choice at the end. One the person has is lit in the accent colour.
 */
@Composable
private fun GroupRow(
    g: ServerModel.Group, server: String, on: Boolean, enabled: Boolean, first: Boolean, toggle: Modifier,
    control: @Composable (Boolean) -> Unit,
) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Ts.hairline))
    Row(
        Modifier.fillMaxWidth().background(if (on) Ts.accent.copy(alpha = 0.10f) else Color.Transparent).then(toggle)
            .padding(start = 14.dp, end = 6.dp).heightIn(min = 52.dp)
            // Greyed out as a whole, so it doesn't pass for one that can be ticked
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Pop.field), contentAlignment = Alignment.Center) {
            if (g.iconId > 0) TsIcon(g.iconId, server, 18.dp)
            else Icon(Icons.Outlined.Shield, contentDescription = null, tint = Ts.faint, modifier = Modifier.size(16.dp))
        }
        Text(
            g.name, color = when {
                !enabled -> Ts.muted
                on -> Ts.bright
                else -> Ts.text
            },
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        control(enabled)
    }
}

@Composable
private fun checkColors() = CheckboxDefaults.colors(
    checkedColor = Ts.accent, uncheckedColor = Ts.muted, disabledCheckedColor = Ts.accent.copy(alpha = 0.4f), disabledUncheckedColor = Ts.faint,
)
