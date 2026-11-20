package io.github.korppujauho.pocketts

import io.github.korppujauho.pocketts.UiSounds.Cue

/**
 * Creating a channel yourself brings three events at once: the channel created, you moved into it, and your channel
 * group in it. A sound pack says each one, one after another; one is enough. So, for a short while after you create a
 * channel, your move into it is quiet when Channel created is heard, and your channel group there is quiet always.
 * When Channel created has no sound (the tones, or a pack without one), the move's sound plays as before.
 */
internal class NewChannelSounds(private val now: () -> Long = System::currentTimeMillis) {
    private var channel = 0
    private var until = 0L
    private var createdHeard = false

    /** You created [cid]; [heard]: Channel created plays (on, and with a sound). */
    @Synchronized
    fun created(cid: Int, heard: Boolean) {
        channel = cid
        until = now() + WINDOW_MS
        createdHeard = heard
    }

    /** Whether [cue], for your own move or channel group in [cid], still plays. */
    @Synchronized
    fun plays(cue: Cue, cid: Int): Boolean {
        if (cid != channel || now() > until) return true
        return when (cue) {
            Cue.CHANNEL_GROUP_CHANGED -> false
            Cue.CHANNEL_SWITCHED, Cue.MOVED -> !createdHeard
            else -> true
        }
    }

    private companion object {
        // The server sends the three together; a few seconds covers a slow connection
        const val WINDOW_MS = 5000L
    }
}
