package io.github.korppujauho.pocketts.proto

import io.github.korppujauho.pocketts.proto.LogPrivacy.channel
import io.github.korppujauho.pocketts.proto.LogPrivacy.folder
import io.github.korppujauho.pocketts.proto.LogPrivacy.message
import io.github.korppujauho.pocketts.proto.LogPrivacy.person
import io.github.korppujauho.pocketts.proto.LogPrivacy.server
import io.github.korppujauho.pocketts.proto.LogPrivacy.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LogPrivacyTest {
    @Test
    fun plainKeepsTheValues() {
        // Test builds' own log: as written
        assertEquals("Connecting to ts.example.com:9987", LogPrivacy.plain("Connecting to ${server("ts.example.com")}:9987"))
        assertEquals("Admin: kicked Jamie", LogPrivacy.plain("Admin: kicked ${person("Jamie")}"))
    }

    @Test
    fun runningNumbers() {
        val r = LogPrivacy.Redactor()
        assertEquals("Connecting to [server 1]:9987", r.redact("Connecting to ${server("ts.example.com")}:9987"))
        // The same one keeps its number (in any case), another gets the next, and each kind counts on its own
        assertEquals("[server 1] again", r.redact("${server("TS.Example.com")} again"))
        assertEquals("[server 2]", r.redact(server("voice.example.org")))
        assertEquals("Admin: moved [person 1] to [channel 1]", r.redact("Admin: moved ${person("Jamie")} to ${channel("Games")}"))
        assertEquals("[person 2], [person 1]", r.redact("${person("Lee")}, ${person("Jamie")}"))
        assertEquals("File list of [folder 1] failed", r.redact("File list of ${folder("/Holiday photos")} failed"))
        // A new run starts over
        assertEquals("[server 1]", LogPrivacy.Redactor().redact(server("voice.example.org")))
    }

    @Test
    fun wordsAndTexts() {
        val r = LogPrivacy.Redactor()
        // Someone's own words go altogether
        assertEquals("Disconnected (KICKED): [message]", r.redact("Disconnected (KICKED): ${message("Kicked from the server: bye Jamie")}"))
        assertEquals("Disconnected", r.redact("Disconnected${message("")}"))
        // Other text stays, without its addresses
        assertEquals(
            "Network error: Unable to resolve host \"[host]\": No address associated with hostname",
            r.redact("Network error: ${text("Unable to resolve host \"ts.example.com\": No address associated with hostname")}"),
        )
        assertEquals("Server error 2568: insufficient client permissions", r.redact("Server error 2568: ${text("insufficient client permissions")}"))
    }

    @Test
    fun patternNet() {
        // IP addresses, with and without a port; IPv6 whole, short and in brackets
        assertEquals("to [address]:9987", LogPrivacy.scrub("to 203.0.113.7:9987"))
        assertEquals("[address] and [address]", LogPrivacy.scrub("2a14:1ec1:1102:0064:: and 2001:db8:85a3:0:0:8a2e:370:7334"))
        assertEquals("[[address]]:10075", LogPrivacy.scrub("[fe80::1]:10075"))
        // Host names, links and email addresses
        assertEquals("host [host] failed", LogPrivacy.scrub("host ts.example.com failed"))
        assertEquals("open [link] now", LogPrivacy.scrub("open https://tmspk.gg/MW8RlC1u now"))
        assertEquals("[link]", LogPrivacy.scrub("ts3server://voice.example.org?port=9988&nickname=Jamie"))
        assertEquals("mail [email]", LogPrivacy.scrub("mail jamie.lee@example.com"))
        // Keys and identities: a long run of base64 or hex
        assertEquals("identity [key]", LogPrivacy.scrub("identity MEwDAgcAAgEgAiEA8Kr9rq3UVYVv0b0gwDH9vUKhoIZ6O3n+Bc0J5PX4qoA="))
        // What looks a little like them but isn't: times, versions, sizes, code
        for (s in listOf(
            "Stopped before at 14:30:27.123",
            "Started 1.5.0-debug on samsung SM-A566B, Android 16",
            "video 1080x1920 c2.exynos.h264.encoder, 27 fps",
            "at io.github.korppujauho.pocketts.Session.connect(Session.kt:338)",
            "java.net.UnknownHostException: oh no",
            "at android.os.Handler.dispatchMessage(Handler.java:103)",
            "Opening camera 0 (back, flash) at 1280x720, 10-30 fps",
        )) assertEquals(s, LogPrivacy.scrub(s), s)
    }

    @Test
    fun nothingOfTheRealValuesIsLeft() {
        val r = LogPrivacy.Redactor()
        val secrets = listOf("ts.example.com", "203.0.113.7", "Jamie", "Secret Clan", "/Holiday photos", "bye Jamie", "voice.example.org", "2001:db8::1")
        val lines = listOf(
            "Connecting to ${server("ts.example.com")}:9987 (DNS entry)",
            "Nickname lookup for ${server("myclan")} failed: ${text("connect to 203.0.113.7 timed out")}",
            "Admin: couldn't kick ${person("Jamie")}, error 2568: ${text("insufficient client permissions")}",
            "Admin: couldn't edit ${channel("Secret Clan")}",
            "File list of ${folder("/Holiday photos")} in channel 5 failed: ${text("not found")}",
            "Disconnected (KICKED): ${message("Kicked from the server: bye Jamie")}",
            // Unmarked, as a future line might be: the pattern net still takes the addresses
            "Can't reach voice.example.org:9987 or 2001:db8::1",
        )
        val out = lines.joinToString("\n") { r.redact(it) }
        for (s in secrets) assertFalse(s in out, "\"$s\" is still in:\n$out")
    }
}
