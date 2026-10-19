package io.github.korppujauho.mobilets6

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.korppujauho.mobilets6.proto.ServerModel

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.start()
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { AppTheme { App() } }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
private fun App() {
    val state by Session.state.collectAsState()
    if (state.phase == Session.Phase.IDLE) ConnectScreen(state) else ServerScreen(state)
}

@Composable
private fun ConnectScreen(state: Session.UiState) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    var nickname by remember { mutableStateOf(settings.nickname) }
    var address by remember { mutableStateOf(settings.address) }
    var password by remember { mutableStateOf(settings.serverPassword) }

    fun connect() {
        settings.nickname = nickname.trim()
        settings.address = address.trim()
        settings.serverPassword = password
        Session.connect(ctx, address, nickname.trim(), password)
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.connect_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = nickname, onValueChange = { nickname = it }, singleLine = true,
            label = { Text(stringResource(R.string.nickname)) }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = address, onValueChange = { address = it }, singleLine = true,
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text("ts.example.com") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true,
            label = { Text(stringResource(R.string.server_password)) }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { connect() },
            enabled = nickname.isNotBlank() && address.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.connect)) }
    }
}

private sealed interface ListRow
private data class ChannelRow(val channel: ServerModel.Channel, val depth: Int) : ListRow
private data class ClientRow(val client: ServerModel.Client, val depth: Int) : ListRow

private fun flatten(nodes: List<ServerModel.Node>, depth: Int = 0, out: MutableList<ListRow> = mutableListOf()): List<ListRow> {
    for (n in nodes) {
        out.add(ChannelRow(n.channel, depth))
        n.clients.forEach { out.add(ClientRow(it, depth + 1)) }
        flatten(n.children, depth + 1, out)
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerScreen(state: Session.UiState) {
    var askPassword by remember { mutableStateOf<ServerModel.Channel?>(null) }
    val rows = remember(state.tree) { flatten(state.tree) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.serverName.ifEmpty { stringResource(R.string.connecting_to, state.address) },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { Session.disconnect() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.disconnect))
                    }
                },
            )
        },
    ) { padding ->
        if (state.phase == Session.Phase.CONNECTING) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.error?.let {
                Text(
                    it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().clickable { Session.clearError() }.padding(16.dp, 8.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { r -> if (r is ChannelRow) "c${r.channel.id}" else "u${(r as ClientRow).client.id}" }) { r ->
                    when (r) {
                        is ChannelRow -> ChannelLine(r, isOwn = r.channel.id == state.ownChannel) {
                            if (r.channel.id == state.ownChannel) return@ChannelLine
                            if (r.channel.hasPassword) askPassword = r.channel else Session.joinChannel(r.channel.id)
                        }
                        is ClientRow -> ClientLine(r, isSelf = r.client.id == state.ownId, talking = r.client.id in state.talking)
                    }
                }
            }
        }
    }

    askPassword?.let { ch ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPassword = null },
            title = { Text(ch.name) },
            text = {
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, singleLine = true,
                    label = { Text(stringResource(R.string.channel_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(onClick = { Session.joinChannel(ch.id, pw); askPassword = null }) { Text(stringResource(R.string.join)) }
            },
            dismissButton = { TextButton(onClick = { askPassword = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ChannelLine(r: ChannelRow, isOwn: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (16 + r.depth * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            r.channel.name, style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isOwn) FontWeight.Bold else FontWeight.Medium,
            color = if (isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (r.channel.hasPassword) Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ClientLine(r: ClientRow, isSelf: Boolean, talking: Boolean) {
    val c = r.client
    Row(
        Modifier.fillMaxWidth().padding(start = (16 + r.depth * 16).dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(if (talking) Color(0xFF3DDC84) else MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            c.nickname + if (c.away) "  (${stringResource(R.string.away)})" else "",
            fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (c.inputMuted || !c.inputHardware) Icon(Icons.Filled.MicOff, contentDescription = null, modifier = Modifier.size(16.dp))
        if (c.outputMuted || !c.outputHardware) Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}
