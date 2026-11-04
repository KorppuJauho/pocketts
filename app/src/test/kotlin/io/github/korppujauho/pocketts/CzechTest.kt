package io.github.korppujauho.pocketts

import android.app.LocaleManager
import android.os.LocaleList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A Czech phone gets the Czech texts with the product names as they are, Czech counts take their own forms, and the
 * language list knows Czech when it's chosen in Android's settings (as "cs-CZ"). After chochrunius's test in their fork.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "cs-rCZ")
class CzechTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun czechTextsAndProductNames() {
        assertEquals("Čeština", AppLanguage.choices.single { it.first == "cs" }.second)
        assertEquals("Nastavení", context.getString(R.string.settings))
        assertEquals("Výchozí kanál serveru", context.getString(R.string.favourite_channel_hint))
        assertEquals("PocketTS", context.getString(R.string.app_name))
        assertEquals("Opus Voice", context.getString(R.string.channel_codec_voice))
    }

    @Test
    fun czechCounts() {
        val people = mapOf(
            0 to "0 uživatelů", 1 to "1 uživatel", 2 to "2 uživatelé", 4 to "4 uživatelé", 5 to "5 uživatelů",
            11 to "11 uživatelů", 22 to "22 uživatelů", 101 to "101 uživatelů",
        )
        for ((count, text) in people) assertEquals(text, context.resources.getQuantityString(R.plurals.channel_people, count, count))
        assertEquals("asi minutu", context.resources.getQuantityString(R.plurals.about_minutes, 1, 1))
        assertEquals("asi 3 minuty", context.resources.getQuantityString(R.plurals.about_minutes, 3, 3))
        assertEquals("asi 5 minut", context.resources.getQuantityString(R.plurals.about_minutes, 5, 5))
        assertEquals("Obrázek 2 z 3 nebyl odeslán: chyba", context.getString(R.string.pictures_not_sent, 2, 3, "chyba"))
    }

    @Test
    fun chosenInAndroidSettings() {
        val manager = context.getSystemService(LocaleManager::class.java)
        val before = manager.applicationLocales
        try {
            manager.applicationLocales = LocaleList.forLanguageTags("cs-CZ")
            assertEquals("cs", AppLanguage.current(context))
        } finally {
            manager.applicationLocales = before
        }
    }
}
