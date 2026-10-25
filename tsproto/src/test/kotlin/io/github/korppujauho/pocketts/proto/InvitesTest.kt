package io.github.korppujauho.pocketts.proto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InvitesTest {
    private val now = 1_800_000_000L

    // As the invite service answers (made-up server)
    private val answer = """{"id":"Ab3dE9xY","type":"server","expires":${now + 86_400},"address":"ts.example.com:9987",""" +
        """"name":"Friends","channel_id":"5","channel_name":"[cspacer1]Games"}"""

    @Test
    fun parsing() {
        assertEquals(Invites.Invite("ts.example.com:9987", "Friends", 5, "Games"), Invites.parse(answer, now))
        // Without a channel, or with channel 0
        val plain = """{"id":"x","type":"server","address":"ts.example.com","name":"Friends"}"""
        assertEquals(Invites.Invite("ts.example.com", "Friends", null, ""), Invites.parse(plain, now))
        assertNull(Invites.parse(answer.replace("\"5\"", "\"0\""), now)?.channelId)
        // Expired, not a server, no address, not JSON
        assertNull(Invites.parse(answer, now + 2 * 86_400))
        assertNull(Invites.parse(answer.replace("\"server\"", "\"contact\""), now))
        assertNull(Invites.parse(answer.replace("ts.example.com:9987", ""), now))
        assertNull(Invites.parse("<html>", now))
    }

    @Test
    fun resolving() {
        val asked = mutableListOf<String>()
        val link = JoinLink.parse("https://tmspk.gg/Ab3dE9xY")!!
        val found = Invites.resolve(link, { asked.add(it); answer.replace("${now + 86_400}", "0") })
        assertEquals(listOf("Ab3dE9xY"), asked)
        assertEquals(
            JoinLink(address = "ts.example.com:9987", serverName = "Friends", channel = "/5", channelName = "Games"),
            found,
        )
        // Unknown or expired: null; the service unreachable: thrown
        assertNull(Invites.resolve(link, { null }))
        assertFailsWith<java.io.IOException> { Invites.resolve(link, { throw java.io.IOException("offline") }) }
        // A link that isn't an invite is left as it is, without asking
        val plain = JoinLink("ts.example.com")
        assertEquals(plain, Invites.resolve(plain, { error("not asked") }))
    }
}
