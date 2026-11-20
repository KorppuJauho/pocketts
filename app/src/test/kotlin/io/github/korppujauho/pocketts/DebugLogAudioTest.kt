package io.github.korppujauho.pocketts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug log's numbers about sound and streams: what the microphone heard, and the kinds of route a stream found. */
class DebugLogAudioTest {
    @Test
    fun aSilencedMicrophoneIsAllZeros() {
        val s = MicSummary(frames = 4)
        assertNull(s.add(ShortArray(960), sending = false))
        s.add(ShortArray(960), false)
        s.add(ShortArray(960), false)
        assertEquals("Microphone in 0 s: loudest silent, digital silence 100 %, voice sent 0 s", s.add(ShortArray(960), false))
    }

    @Test
    fun aQuietRoomIsNotSilence() {
        val s = MicSummary(frames = 50)
        var line: String? = null
        repeat(50) { i -> line = s.add(ShortArray(960) { if (it % 2 == 0) 40 else -35 }, sending = i < 25) }
        // About -58 dBFS, never exactly zero; half the time voice went out
        assertEquals("Microphone in 1 s: loudest -58 dBFS, digital silence 0 %, voice sent 0 s", line)
    }

    @Test
    fun routesAreCountedByKindWithoutAddresses() {
        val r = IceRoutes()
        r.ours("candidate:1 1 udp 2122260223 192.0.2.10 54321 typ host generation 0")
        r.ours("candidate:2 1 udp 1686052607 198.51.100.7 61000 typ srflx raddr 192.0.2.10 rport 54321")
        r.theirsIn("v=0\r\na=candidate:3 1 udp 41885439 203.0.113.9 3478 typ relay raddr 0.0.0.0 rport 0\r\na=mid:0")
        val s = r.summary()
        assertEquals("ours local 1, public 1, relay 0; theirs local 0, public 0, relay 1", s)
        assertTrue("no address in the log", listOf("192.0.2", "198.51", "203.0.113").none { it in s })
        assertEquals("public", IceRoutes.kind("prflx"))
    }
}
