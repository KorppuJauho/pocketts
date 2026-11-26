package io.github.korppujauho.pocketts.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The app's colours, one set per theme. The defaults are WebTS's, so the app looks like the web client. Screens take
 * every colour from here (as [Ts] and [Pop]), so that another set changes the whole look. Video and pictures keep
 * their own black and white, since they aren't part of the look.
 */
@Immutable
data class TsColors(
    val page: Color = Color(0xFF121212),
    val header: Color = Color(0xFF1A1B1E),
    val panel: Color = Color(0xFF2B2D31), // the channel tree's and the chat's boxes
    val card: Color = Color(0xFF1E1F22), // controls bar, channel boxes, panel headers
    val button: Color = Color(0xFF2B2D31),
    val buttonBorder: Color = Color(0x1AFFFFFF),
    val hairline: Color = Color(0x0DFFFFFF),
    val row: Color = Color(0x08FFFFFF),
    val input: Color = Color(0xFF383A40),
    val subtle: Color = Color(0x05FFFFFF), // the faintest box and edge
    val groupBorder: Color = Color(0x0AFFFFFF), // the edge of a group of rows, in popups and settings
    val line: Color = Color(0x14FFFFFF), // dividers and quiet borders
    val boxBorder: Color = Color(0x1FFFFFFF), // a box that can be held or tapped
    val selected: Color = Color(0x12FFFFFF), // the chosen tab
    val pill: Color = Color(0x0FFFFFFF), // a button that isn't the main one
    val separator: Color = Color(0xFF111214), // the dark line under headers, and a level meter's track
    val lifted: Color = Color(0xFF2A2C31), // a channel being moved
    val avatar: Color = Color(0xFF3F4147), // behind someone without a picture

    val text: Color = Color(0xFFDBDEE1),
    val title: Color = Color(0xFFF2F3F5),
    val bright: Color = Color(0xFFF8FAFC),
    val username: Color = Color(0xFFCBD5E1),
    // Lighter than WebTS's: small text needs at least 4.5 : 1 against the panels and popups to read easily
    val muted: Color = Color(0xFF9AA1AA),
    val faint: Color = Color(0xFF929CAE),
    val event: Color = Color(0xFF949BA4),
    val spacer: Color = Color(0xFFCBD5E1),
    val spacerRepeat: Color = Color(0xFF94A3B8),
    val spacerLine: Color = Color(0x7394A3B8),
    val tab: Color = Color(0xFF94A3B8), // a tab that isn't chosen
    val placeholder: Color = Color(0xFF8E9297), // a text box's hint, and the figure of someone without a picture
    val onFill: Color = Color.White, // text and icons on a filled button
    val typed: Color = Color.White, // what's typed in the chat
    val redText: Color = Color(0xFFF87171), // delete, stop, and errors in text
    val errorBg: Color = Color(0xFF3A1D20),
    val errorText: Color = Color(0xFFFECACA),

    val brand: Color = Color(0xFF00A8FC),
    val accent: Color = Color(0xFF3B82F6),
    val accentFill: Color = Color(0xFF2563EB), // filled buttons: dark enough for their white text to read easily
    val talking: Color = Color(0xFF38BDF8),
    val sender: Color = Color(0xFF93C5FD),
    val red: Color = Color(0xFFEF4444),
    val redButton: Color = Color(0xFFDA373C),
    val green: Color = Color(0xFF22C55E),
    val amber: Color = Color(0xFFF59E0B),
    val lit: Color = Color(0xFFFBBF24), // a camera's light, or its turning, switched on
    val afk: Color = Color(0xFFFDE047),
    val afkBg: Color = Color(0x33EAB308),
    val afkBorder: Color = Color(0x4DEAB308),
    val you: Color = Color(0xFFC084FC),
    val youBorder: Color = Color(0xFFA855F7),
    val whisper: Color = Color(0xFFF59E0B), // whispering: the hold button, the list, and who whispers
    val meterOff: Color = Color(0xFF64748B), // a level meter below the voice activation level

    val talkButton: Color = Color(0xC71E1F22), // the round hold-to-talk button
    val talkButtonBorder: Color = Color(0x24FFFFFF),
    val talkHeld: Color = Color(0xEB0E7490),
    val talkHeldIcon: Color = Color(0xFFE0F2FE),
    val share: Color = Color(0xFF23A559), // sharing a screen or a camera
    val shareText: Color = Color(0xFF4ADE80),
    val watch: Color = Color(0xFF15803D), // dark enough for WATCH's white letters

    val pop: PopColors = PopColors(),
)

/** The popups' own colours: a card a little lighter than the page, fading down, with a faint edge. */
@Immutable
data class PopColors(
    val top: Color = Color(0xFF31333A),
    val bottom: Color = Color(0xFF24252A),
    val edge: Color = Color(0x1AFFFFFF),
    val field: Color = Color(0x0DFFFFFF), // boxes inside a popup
    val handle: Color = Color(0x40FFFFFF),
) {
    val background = Brush.verticalGradient(listOf(top, bottom))
}

val LocalTsColors = staticCompositionLocalOf { TsColors() }

/** The current theme's colours. */
val Ts: TsColors
    @Composable @ReadOnlyComposable get() = LocalTsColors.current

/** The current theme's popup colours. */
val Pop: PopColors
    @Composable @ReadOnlyComposable get() = LocalTsColors.current.pop

@Composable
fun PocketTsTheme(colors: TsColors = TsColors(), content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onFill,
        secondary = colors.talking,
        background = colors.page,
        onBackground = colors.text,
        surface = colors.panel,
        onSurface = colors.text,
        surfaceVariant = colors.card,
        onSurfaceVariant = colors.muted,
        surfaceContainerHigh = colors.panel,
        surfaceContainer = colors.card,
        outline = colors.buttonBorder,
        error = colors.red,
    )
    CompositionLocalProvider(LocalTsColors provides colors) {
        MaterialTheme(colorScheme = scheme) {
            Surface(Modifier.fillMaxSize(), color = colors.page, content = content)
        }
    }
}
