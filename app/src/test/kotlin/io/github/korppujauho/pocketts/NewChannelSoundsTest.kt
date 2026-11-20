package io.github.korppujauho.pocketts

import io.github.korppujauho.pocketts.UiSounds.Cue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewChannelSoundsTest {
    private var time = 1_000L
    private val sounds = NewChannelSounds { time }

    @Test
    fun createdHeardIsTheOnlySound() {
        sounds.created(7, heard = true)
        assertFalse(sounds.plays(Cue.CHANNEL_SWITCHED, 7))
        assertFalse(sounds.plays(Cue.MOVED, 7))
        assertFalse(sounds.plays(Cue.CHANNEL_GROUP_CHANGED, 7))
        // Being kicked still sounds
        assertTrue(sounds.plays(Cue.KICKED, 7))
    }

    @Test
    fun withoutCreatedTheMoveSounds() {
        // The tones (or a pack without Channel created): the move's sound is the one heard
        sounds.created(7, heard = false)
        assertTrue(sounds.plays(Cue.CHANNEL_SWITCHED, 7))
        assertTrue(sounds.plays(Cue.MOVED, 7))
        assertFalse(sounds.plays(Cue.CHANNEL_GROUP_CHANGED, 7))
    }

    @Test
    fun otherChannelsAndLaterAreUntouched() {
        assertTrue(sounds.plays(Cue.CHANNEL_SWITCHED, 7))
        sounds.created(7, heard = true)
        assertTrue(sounds.plays(Cue.CHANNEL_SWITCHED, 8))
        assertTrue(sounds.plays(Cue.CHANNEL_GROUP_CHANGED, 8))
        // Coming back to it later sounds as usual
        time += 6_000
        assertTrue(sounds.plays(Cue.CHANNEL_SWITCHED, 7))
        assertTrue(sounds.plays(Cue.CHANNEL_GROUP_CHANGED, 7))
    }
}
