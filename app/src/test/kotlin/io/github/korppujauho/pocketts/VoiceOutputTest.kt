package io.github.korppujauho.pocketts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Where voices play is the choice the rest follows: the earpiece always brings call audio along, the loudspeaker has
 * it for echo cancellation, unless this phone was found to play call audio from its earpiece all the same.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceOutputTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun theEarpieceAlwaysBringsCallAudio() {
        assertTrue(VoiceSettings(earpiece = true, echoCancellation = false).callAudio)
        assertTrue(VoiceSettings(earpiece = true, echoCancellation = true).callAudio)
        assertTrue(VoiceSettings(earpiece = true, echoCancellation = false, speakerRefusedAt = BuildConfig.VERSION_CODE).callAudio)
    }

    @Test
    fun theLoudspeakerHasCallAudioForEchoCancellation() {
        assertTrue(VoiceSettings(echoCancellation = true).callAudio)
        assertFalse(VoiceSettings(echoCancellation = false).callAudio)
    }

    @Test
    fun aPhoneThatRefusedTheLoudspeakerPlaysItAsMedia() {
        val refused = VoiceSettings(echoCancellation = true, speakerRefusedAt = BuildConfig.VERSION_CODE)
        assertTrue(refused.speakerRefused)
        assertFalse(refused.callAudio)
        // Found by another version of the app: tried again
        val older = VoiceSettings(echoCancellation = true, speakerRefusedAt = BuildConfig.VERSION_CODE - 1)
        assertFalse(older.speakerRefused)
        assertTrue(older.callAudio)
        assertFalse(VoiceSettings().speakerRefused)
    }

    @Test
    fun theChoiceIsSaved() {
        VoiceSettings(earpiece = true, useHeadset = false, speakerRefusedAt = 7).save(context)
        val loaded = VoiceSettings.load(context)
        assertTrue(loaded.earpiece)
        assertFalse(loaded.useHeadset)
        assertEquals(7, loaded.speakerRefusedAt)
    }
}
