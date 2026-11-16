package io.github.korppujauho.pocketts

import android.view.View
import io.github.korppujauho.pocketts.ui.ltr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Arabic and Persian phones get their own texts, right to left, with the product names as they are and counts in
 * the language's own digits and forms; the language list has each by its own name. A count like "3 / 10" is kept
 * left to right inside them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RightToLeftTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun ownTexts(tag: String, name: String) {
        assertEquals(name, AppLanguage.choices.single { it.first == tag }.second)
        assertEquals(View.LAYOUT_DIRECTION_RTL, context.resources.configuration.layoutDirection)
        assertNotEquals("Settings", context.getString(R.string.settings))
        assertEquals("PocketTS", context.getString(R.string.app_name))
        assertEquals("Opus Voice", context.getString(R.string.channel_codec_voice))
        val locale = context.resources.configuration.locales[0]
        for (count in listOf(0, 1, 2, 5, 22, 100)) {
            val text = context.resources.getQuantityString(R.plurals.channel_people, count, count)
            assertTrue(text.isNotBlank())
            // Larger numbers are written in the text, in the language's digits
            if (count > 2) assertTrue(text, text.contains(String.format(locale, "%d", count)))
        }
    }

    @Test @Config(qualifiers = "ar-rEG")
    fun arabic() = ownTexts("ar", "العربية")

    @Test @Config(qualifiers = "fa-rIR")
    fun persian() = ownTexts("fa", "فارسی")

    @Test
    fun aCountStaysLeftToRight() {
        assertEquals("⁦" + "3 / 10" + "⁩", ltr("3 / 10"))
    }
}
