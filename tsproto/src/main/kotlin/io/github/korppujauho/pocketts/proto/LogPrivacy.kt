package io.github.korppujauho.pocketts.proto

/**
 * Keeps the debug log free of what identifies anyone, by removing it (not hiding or hashing it: nothing of it is left).
 *
 * A log line marks its private parts ([server], [person], [channel], [folder], [message], [text]). The debug log gets
 * them as running numbers, "[server 1]", "[person 3]", which only say that two lines mean the same one; which real
 * value a number stands for is kept in memory only ([Redactor]), never written, and starts over with each run. A
 * person's own words ([message]) go altogether. Then a pattern net ([scrub]) takes out anything with a recognisable
 * shape that a line didn't mark: addresses, host names, email addresses, links, keys. Test builds' own log, sent to the
 * development machine, gets the marked parts as they are ([plain]).
 */
object LogPrivacy {
    private const val START = ''
    private const val SEP = ''
    private const val END = ''

    enum class Kind(val label: String) { SERVER("server"), PERSON("person"), CHANNEL("channel"), FOLDER("folder"), MESSAGE("message"), TEXT("") }

    private fun mark(kind: Kind, value: Any?) = "$START${kind.ordinal}$SEP${value ?: ""}$END"

    /** A server: its address, host name or nickname. */
    fun server(value: Any?) = mark(Kind.SERVER, value)
    /** Someone's nickname (or another identifying name, such as a device's). */
    fun person(value: Any?) = mark(Kind.PERSON, value)
    fun channel(value: Any?) = mark(Kind.CHANNEL, value)
    /** A path or file name in a channel's files or the server's. */
    fun folder(value: Any?) = mark(Kind.FOLDER, value)
    /** Someone's own words (a kick reason, say): never in the debug log. */
    fun message(value: Any?) = mark(Kind.MESSAGE, value)
    /** Text from elsewhere (an error, a server's answer) that may hold an address: kept, through the pattern net. */
    fun text(value: Any?) = mark(Kind.TEXT, value)

    private val MARKED = Regex("$START(\\d)$SEP([^$END]*)$END")

    /** The line as it was: the marked parts as they are. */
    fun plain(line: String): String = MARKED.replace(line) { it.groupValues[2] }

    /** Running numbers per kind, for one run of the app: the same value keeps its number. */
    class Redactor {
        private val numbers = HashMap<Pair<Kind, String>, Int>()
        private val counts = HashMap<Kind, Int>()

        /** The line for the debug log: marked parts as numbers (or gone), then [scrub] over all of it. */
        @Synchronized
        fun redact(line: String): String {
            val marked = MARKED.replace(line) { m ->
                val kind = Kind.entries[m.groupValues[1].toInt()]
                val value = m.groupValues[2]
                when (kind) {
                    Kind.TEXT -> value
                    Kind.MESSAGE -> if (value.isEmpty()) "" else "[message]"
                    else -> if (value.isEmpty()) "[${kind.label}]" else {
                        val n = numbers.getOrPut(kind to value.lowercase()) { (counts[kind] ?: 0).plus(1).also { counts[kind] = it } }
                        "[${kind.label} $n]"
                    }
                }
            }
            return scrub(marked)
        }
    }

    // A link: everything up to a space or a quote
    private val LINK = Regex("""(?i)\b(?:https?|ts3server|teamspeak|ts)://[^\s"'<>]+""")
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    private val IPV4 = Regex("""(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])""")
    // Hex groups with colons, standing alone; whether it's really an address (not a time) is checked after
    private val IPV6 = Regex("""(?i)(?<![\w:.])[0-9a-f]{0,4}(?::[0-9a-f]{0,4}){2,7}(?![\w:.])""")
    // Lower-case dotted names ending in letters, as host names are written; names that start like Java packages or
    // Android's codecs (stack traces, "c2.exynos.h264.encoder") are code, not hosts
    private val HOST = Regex("""(?<![\w.-])[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\.[a-z]{2,24}(?![\w-])(?!\.\w)""")
    private val CODE = setOf("java", "javax", "jdk", "sun", "kotlin", "kotlinx", "android", "androidx", "dalvik", "libcore", "io", "org", "com", "c2", "omx")
    // Keys, identities, long ids: a long run of base64 or hex
    private val KEY = Regex("""(?<![\w+/=-])[A-Za-z0-9+/=_-]{32,}(?![\w+/=-])""")

    /** Takes out what has a recognisable shape: links, email addresses, IP addresses, host names, keys. */
    fun scrub(text: String): String {
        var s = LINK.replace(text, "[link]")
        s = EMAIL.replace(s, "[email]")
        s = IPV4.replace(s, "[address]")
        s = IPV6.replace(s) { m ->
            val v = m.value
            // "::" or six and more groups: an address; "14:30:27" is a time
            if ("::" in v || v.count { it == ':' } >= 5) "[address]" else v
        }
        s = HOST.replace(s) { m -> if (m.value.substringBefore('.') in CODE) m.value else "[host]" }
        s = KEY.replace(s, "[key]")
        return s
    }
}
