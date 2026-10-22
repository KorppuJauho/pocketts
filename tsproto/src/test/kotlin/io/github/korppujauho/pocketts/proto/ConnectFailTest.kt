package io.github.korppujauho.pocketts.proto

import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Connecting to an address where no TeamSpeak server answers says so, not that a connection was lost. */
class ConnectFailTest {
    private fun reasonFor(host: String, port: Int): String? {
        val reasons = ArrayBlockingQueue<String>(1)
        val client = TsClient(object : TsClient.Listener {
            override fun onDisconnected(reason: String?) {
                reasons.offer(reason ?: "(none)")
            }
        })
        client.connect(TsClient.Options(host, port, Identity.generate(8), "PocketTS test"))
        return reasons.poll(TsClient.SILENCE_MS + 5_000, TimeUnit.MILLISECONDS)
    }

    @Test
    fun aSilentAddress() {
        // Something listens there but never answers
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { silent ->
            assertEquals(TsClient.NO_ANSWER, reasonFor("127.0.0.1", silent.localPort))
        }
    }

    @Test
    fun aClosedPort() {
        val port = DatagramSocket(0, InetAddress.getLoopbackAddress()).use { it.localPort } // free again
        assertEquals(TsClient.NO_ANSWER, reasonFor("127.0.0.1", port))
    }

    @Test
    fun anUnknownName() {
        val reason = reasonFor("no-such-server.invalid", 9987)
        assertTrue(reason?.startsWith("Couldn't connect: no server found at \"no-such-server.invalid\"") == true, "was: $reason")
    }
}
