package io.github.korppujauho.pocketts.proto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinLinkTest {
    @Test
    fun teamSpeak3Links() {
        assertEquals(JoinLink("ts.example.com"), JoinLink.parse("ts3server://ts.example.com"))
        assertEquals(
            JoinLink("ts.example.com:9988", nickname = "Lee", password = "s3cr+t", channel = "Games/Raid", channelPassword = "x y", token = "ABC="),
            JoinLink.parse("ts3server://ts.example.com?port=9988&nickname=Lee&password=s3cr+t&channel=Games%2FRaid&channelpassword=x%20y&token=ABC%3D"),
        )
        // The address's own port wins; a bad ?port= makes it no link
        assertEquals("ts.example.com:9000", JoinLink.parse("ts3server://ts.example.com:9000?port=9988")?.address)
        assertNull(JoinLink.parse("ts3server://ts.example.com?port=abc"))
        // A channel by its id
        assertEquals("/12", JoinLink.parse("ts3server://ts.example.com?cid=12")?.channel)
        // IPv6
        assertEquals("[2001:db8::1]:9988", JoinLink.parse("ts3server://[2001:db8::1]?port=9988")?.address)
        // A nickname (the server's) works as an address too
        assertEquals("myclan", JoinLink.parse("ts3server://myclan/")?.address)
    }

    @Test
    fun teamSpeakLinks() {
        assertEquals("ts.example.com:9987", JoinLink.parse("teamspeak://connect?address=ts.example.com:9987&nickname=Lee")?.address)
        assertEquals("ts.example.com", JoinLink.parse("TeamSpeak://ts.example.com")?.address)
    }

    @Test
    fun tmspkLinks() {
        // An invite: only its code, however it comes
        val invite = JoinLink(address = "", invite = "Ab3dE9xY")
        assertEquals(invite, JoinLink.parse("https://tmspk.gg/Ab3dE9xY"))
        assertEquals(invite, JoinLink.parse("tmspk.gg/Ab3dE9xY/"))
        assertEquals(invite, JoinLink.parse("teamspeak://invite=Ab3dE9xY"))
        assertTrue(JoinLink.isLink("https://tmspk.gg/Ab3dE9xY"))
        assertNull(JoinLink.parse("teamspeak://invite=no spaces"))
        assertNull(JoinLink.parse("https://tmspk.gg/"))
        // s= is an address, as teamspeak://
        assertEquals("ts.example.com:9988", JoinLink.parse("https://tmspk.gg/s=ts.example.com?port=9988&nickname=Lee")?.address)
        assertEquals("Lee", JoinLink.parse("https://tmspk.gg/s=ts.example.com?port=9988&nickname=Lee")?.nickname)
        // Not TeamSpeak's
        assertEquals(false, JoinLink.isLink("https://tmspk.gg.example.com/x"))
    }

    @Test
    fun notLinks() {
        assertNull(JoinLink.parse("https://ts.example.com"))
        assertNull(JoinLink.parse("ts3server://"))
        assertNull(JoinLink.parse("ts3server://two words"))
        assertTrue(JoinLink.isLink(" ts3server://x"))
        assertEquals(false, JoinLink.isLink("http://x"))
        // A long nickname is cut to what TeamSpeak allows
        assertEquals(30, JoinLink.parse("ts3server://x?nickname=" + "a".repeat(50))?.nickname?.length)
    }
}
