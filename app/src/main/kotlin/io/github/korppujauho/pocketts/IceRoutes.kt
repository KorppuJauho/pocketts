package io.github.korppujauho.pocketts

import java.util.concurrent.ConcurrentHashMap

/**
 * The kinds of route a stream connection found, ours and theirs, for the log: "local" (an address on the device),
 * "public" (the network's outside address, from STUN), "relay" (through a TURN server). Only the kind is read from a
 * candidate line, never its address. When no route works, this says which kinds there were: behind a shared mobile
 * network (carrier-grade NAT) "public" ones often can't reach each other, and only a relay helps.
 */
class IceRoutes {
    private val ours = ConcurrentHashMap<String, Int>()
    private val theirs = ConcurrentHashMap<String, Int>()

    fun ours(candidate: String) = count(ours, candidate)
    fun theirs(candidate: String) = count(theirs, candidate)

    /** Their routes that came inside their offer or answer, not one by one. */
    fun theirsIn(sdp: String) = sdp.lineSequence().filter { it.startsWith("a=candidate") }.forEach { theirs(it) }

    fun summary() = "ours ${text(ours)}; theirs ${text(theirs)}"

    private fun count(to: ConcurrentHashMap<String, Int>, candidate: String) {
        kind(candidate)?.let { to.merge(it, 1, Int::plus) }
    }

    private fun text(m: Map<String, Int>) =
        KINDS.joinToString(", ") { "$it ${m[it] ?: 0}" }

    companion object {
        private val KINDS = listOf("local", "public", "relay")
        private val TYP = Regex("""\btyp (\w+)""")

        /** A candidate's kind by its "typ", or a stats report's candidateType ("host", "srflx", "prflx", "relay"). */
        fun kind(candidate: String): String? = when (TYP.find(candidate)?.groupValues?.get(1) ?: candidate) {
            "host" -> "local"
            "srflx", "prflx" -> "public"
            "relay" -> "relay"
            else -> null
        }
    }
}
