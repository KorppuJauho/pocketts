package io.github.korppujauho.pocketts

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import io.github.korppujauho.pocketts.proto.Streams
import io.github.korppujauho.pocketts.ui.LocalVideoStill
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.ServerScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of streams (scripts/dev.sh screenshots → docs/screenshots/): watching Riley's stream, the same in full
 * screen, and sharing the back camera. The video needs the phone's graphics, so a made-up still picture stands in for
 * it (generated for this, in src/test/resources).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class StreamPicturesTest {
    init {
        io.github.korppujauho.pocketts.ui.chatNow = { Example.T0 + 600_000 }
    }

    @get:Rule
    val compose = androidx.compose.ui.test.junit4.createComposeRule()

    private fun path(name: String) = "${roborazziSystemPropertyOutputDirectory()}/$name.png"

    @Test
    fun watching() = captureRoboImage(path("stream-watching")) { Screen(StreamExample.watching(), StreamExample.game()) }

    /** The full screen is a window of its own: opened with its button, and the whole screen captured. */
    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    fun fullScreen() {
        compose.setContent { Screen(StreamExample.watching(), StreamExample.game()) }
        compose.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(R.string.stream_full_screen)).performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage(path("stream-full-screen"))
    }

    @Test
    fun sharing() = captureRoboImage(path("stream-sharing")) { Screen(StreamExample.sharing(), StreamExample.camera()) }

    @Composable
    private fun Screen(state: Session.UiState, still: ImageBitmap) = PocketTsTheme {
        CompositionLocalProvider(LocalVideoStill provides still) {
            ServerScreen(state, Example.noActions, voices = Example.voices(setOf(4)))
        }
    }
}

/** Example streams for the pictures: Riley shares a game, and you share your back camera. */
object StreamExample {
    // Stand-ins for WebRTC's tracks, never used: the still picture is drawn instead
    private inline fun <reified T> track(): T =
        T::class.java.getDeclaredConstructor(Long::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(1L)

    private fun still(name: String): ImageBitmap =
        StreamExample::class.java.getResourceAsStream("/$name")!!.use { BitmapFactory.decodeStream(it) }.asImageBitmap()

    /** A made-up co-op game: two explorers on a rope bridge between floating islands. */
    fun game() = still("stream-game.jpg")

    /** A made-up autumn lake, as a phone's camera sees it upright. */
    fun camera() = still("stream-camera.jpg")

    private fun ptt(s: Session.UiState) =
        s.copy(voice = VoiceSettings(activation = VoiceSettings.Activation.PTT), micAllowed = true)

    /** Watching Riley's stream, with its sound. */
    fun watching() = ptt(Example.state()).let {
        it.copy(stream = StreamEngine.State(watching = 4, watchVideo = track(), watchAudio = track()))
    }

    /** Sharing the back camera, which has a flashlight, live to two viewers. */
    fun sharing() = ptt(Example.state()).let {
        it.copy(
            stream = StreamEngine.State(
                sharing = Streams.Kind.CAMERA, shareLive = true, viewers = 2, shareFront = false, shareFlash = true,
                localVideo = track(),
                zoomMin = 1f, zoomMax = 8f,
            ),
        )
    }
}
