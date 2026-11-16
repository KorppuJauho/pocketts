package io.github.korppujauho.pocketts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Portuguese, Polish, Turkish, Italian and Ukrainian phones get their own texts, with the product names as they are and counts in the
 * language's own forms; the language list has each by its own name. A Portugal phone gets the Portuguese too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewLanguagesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun ownTexts(tag: String, name: String) {
        assertEquals(name, AppLanguage.choices.single { it.first == tag }.second)
        assertNotEquals("Settings", context.getString(R.string.settings))
        assertNotEquals("Voices play as", context.getString(R.string.play_as))
        assertEquals("PocketTS", context.getString(R.string.app_name))
        assertEquals("Opus Voice", context.getString(R.string.channel_codec_voice))
        for (count in listOf(1, 2, 5, 22)) {
            assertTrue(context.resources.getQuantityString(R.plurals.channel_people, count, count).contains("$count"))
        }
    }

    @Test @Config(qualifiers = "pt-rBR")
    fun portuguese() = ownTexts("pt", "Português")

    @Test @Config(qualifiers = "pt-rPT")
    fun portugalGetsThePortugueseToo() = assertNotEquals("Settings", context.getString(R.string.settings))

    @Test @Config(qualifiers = "pl-rPL")
    fun polish() = ownTexts("pl", "Polski")

    @Test @Config(qualifiers = "tr-rTR")
    fun turkish() = ownTexts("tr", "Türkçe")

    @Test @Config(qualifiers = "it-rIT")
    fun italian() = ownTexts("it", "Italiano")

    @Test @Config(qualifiers = "uk-rUA")
    fun ukrainian() = ownTexts("uk", "Українська")
}
