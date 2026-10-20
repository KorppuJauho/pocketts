package io.github.korppujauho.pocketts.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** WebTS's colours, so the app looks like the web client. */
object Ts {
    val page = Color(0xFF121212)
    val header = Color(0xFF1A1B1E)
    val panel = Color(0xFF2B2D31) // the channel tree's and the chat's boxes
    val card = Color(0xFF1E1F22) // controls bar, channel boxes, panel headers
    val button = Color(0xFF2B2D31)
    val buttonBorder = Color(0x1AFFFFFF)
    val hairline = Color(0x0DFFFFFF)
    val row = Color(0x08FFFFFF)
    val input = Color(0xFF383A40)

    val text = Color(0xFFDBDEE1)
    val title = Color(0xFFF2F3F5)
    val bright = Color(0xFFF8FAFC)
    val username = Color(0xFFCBD5E1)
    val muted = Color(0xFF80848E)
    val faint = Color(0xFF64748B)
    val event = Color(0xFF949BA4)
    val spacer = Color(0xFFCBD5E1)
    val spacerRepeat = Color(0xFF94A3B8)

    val brand = Color(0xFF00A8FC)
    val accent = Color(0xFF3B82F6)
    val accentHover = Color(0xFF2563EB)
    val talking = Color(0xFF38BDF8)
    val sender = Color(0xFF93C5FD)
    val red = Color(0xFFEF4444)
    val redButton = Color(0xFFDA373C)
    val green = Color(0xFF22C55E)
    val amber = Color(0xFFF59E0B)
    val afk = Color(0xFFFDE047)
    val afkBg = Color(0x33EAB308)
    val afkBorder = Color(0x4DEAB308)
    val you = Color(0xFFC084FC)
    val youBorder = Color(0xFFA855F7)
}

@Composable
fun PocketTsTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Ts.accent,
        onPrimary = Color.White,
        secondary = Ts.talking,
        background = Ts.page,
        onBackground = Ts.text,
        surface = Ts.panel,
        onSurface = Ts.text,
        surfaceVariant = Ts.card,
        onSurfaceVariant = Ts.muted,
        surfaceContainerHigh = Ts.panel,
        surfaceContainer = Ts.card,
        outline = Ts.buttonBorder,
        error = Ts.red,
    )
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = Ts.page, content = content)
    }
}
