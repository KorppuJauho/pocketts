package io.github.korppujauho.pocketts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.korppujauho.pocketts.ui.Pop
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import io.github.korppujauho.pocketts.ui.PopColors
import io.github.korppujauho.pocketts.ui.Ts
import io.github.korppujauho.pocketts.ui.TsColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The screens take their colours from the theme they're shown in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun defaultColours() {
        var seen: TsColors? = null
        compose.setContent { PocketTsTheme { seen = Ts } }
        compose.waitForIdle()
        assertEquals(TsColors(), seen)
        assertEquals(Color(0xFF3B82F6), seen?.accent)
    }

    @Test
    fun anotherSetReachesEveryone() {
        val red = TsColors(accent = Color.Red, page = Color.Black, pop = PopColors(field = Color.Green))
        val seen = mutableListOf<Color>()
        compose.setContent {
            PocketTsTheme(red) {
                seen += Ts.accent
                seen += Pop.field
                seen += MaterialTheme.colorScheme.primary // Material's own parts (switches, text boxes) follow too
                seen += MaterialTheme.colorScheme.background
            }
        }
        compose.waitForIdle()
        assertEquals(listOf(Color.Red, Color.Green, Color.Red, Color.Black), seen.take(4))
    }
}
