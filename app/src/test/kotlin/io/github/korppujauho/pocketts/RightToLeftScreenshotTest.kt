package io.github.korppujauho.pocketts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import io.github.korppujauho.pocketts.ui.ConnectScreen
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.ServerScreen
import io.github.korppujauho.pocketts.ui.SettingsContent
import io.github.korppujauho.pocketts.ui.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of the main screens in Arabic, mirrored right to left, for checking that direction without a phone
 * (scripts/dev.sh screenshots → docs/screenshots/rtl-*.png). Robolectric doesn't turn the screens itself, so the
 * direction is set as the phone would.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ar-rEG-w411dp-h891dp-xxhdpi", sdk = [36])
class RightToLeftScreenshotTest {
    init {
        io.github.korppujauho.pocketts.ui.chatNow = { Example.T0 + 600_000 }
    }

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/rtl-$name.png") {
            PocketTsTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() } }
        }

    @Test
    fun server() = shot("server") { ServerScreen(Example.state(), Example.noActions, voices = Example.voices()) }

    @Test
    fun chat() = shot("chat") { ServerScreen(Example.chatState(), Example.noActions, voices = Example.voices(), startView = View.CHAT) }

    @Test
    fun connect() = shot("connect") {
        ConnectScreen(
            "Jamie", "ts.example.com", "", listOf(Favorite("ts.example.com", "Friends"), Favorite("192.0.2.10:9987", "Test server")),
            null, Example.noConnect, settingsActions = Example.noSettings,
        )
    }

    @Test
    fun settings() = shot("settings") {
        SettingsContent(VoiceSettings(), "Jamie", Example.noSettings, {}, startTab = 2, modifier = Modifier.fillMaxSize())
    }
}
