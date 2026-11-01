package io.github.korppujauho.pocketts.proto

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * A busy channel, for measuring how the app holds up (scripts/dev.sh load): [CLIENTS] test clients join the test
 * server's default channel and, for [SECONDS], talk in turns as people do (pauses, short and long turns, now and then
 * two at once), and now and then write a channel message. Their voice is Opus's silent frame, so a phone watching
 * decodes real Opus but plays nothing. Skipped without TS_TEST_SERVER and TS_LOAD_CLIENTS.
 */
class LoadTest {
    private val server = System.getenv("TS_TEST_SERVER").orEmpty()
    private val clients = System.getenv("TS_LOAD_CLIENTS").orEmpty().toIntOrNull() ?: 0
    private val seconds = System.getenv("TS_LOAD_SECONDS").orEmpty().toIntOrNull() ?: 180

    private companion object {
        // Opus: one 20 ms frame of silence (CELT, full band, mono)
        val SILENCE = byteArrayOf(0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte())
        const val CODEC = 4 // Opus Voice
    }

    private class Bot(val name: String) : TsClient.Listener {
        val connected = CountDownLatch(1)
        @Volatile var gone: String? = null
        val client = TsClient(this) { }

        override fun onConnected(clientId: Int) = connected.countDown()
        override fun onDisconnected(reason: String?) {
            gone = reason ?: "left"
        }
    }

    @Test
    fun busyChannel() {
        assumeTrue(server.isNotEmpty() && clients > 0, "TS_TEST_SERVER and TS_LOAD_CLIENTS not set")
        val host = server.substringBeforeLast(':')
        val port = server.substringAfterLast(':').toInt()
        val run = System.currentTimeMillis() % 1000

        // One at a time, so the server's flood protection doesn't take it for an attack
        val bots = (1..clients).map { n ->
            Bot("Load $n").also { b ->
                b.client.connect(TsClient.Options(host, port, Identity.generate(8), "Load $run-$n"))
                assertTrue(b.connected.await(20, TimeUnit.SECONDS), "${b.name} didn't connect: ${b.gone}")
                Thread.sleep(400)
            }
        }
        println("$clients test clients in the default channel for $seconds s")

        val turns = AtomicInteger()
        val frames = AtomicInteger()
        val messages = AtomicInteger()
        val talking = AtomicInteger()
        val end = System.currentTimeMillis() + seconds * 1000L
        val threads = bots.mapIndexed { i, b ->
            Thread({
                val random = Random(run * 100 + i)
                Thread.sleep(random.nextLong(0, 3000))
                while (System.currentTimeMillis() < end && b.gone == null) {
                    // A pause, longer when others are talking (people mostly take turns)
                    Thread.sleep(random.nextLong(1500, 9000) + talking.get() * 2000L)
                    if (System.currentTimeMillis() >= end) break
                    if (random.nextInt(6) == 0) {
                        b.client.sendCommand(Commands.build("sendtextmessage", "targetmode" to 2, "target" to 0, "msg" to "load test ${messages.incrementAndGet()}"))
                    }
                    // A turn: mostly short, sometimes long
                    val ms = if (random.nextInt(4) == 0) random.nextLong(4000, 12000) else random.nextLong(400, 3000)
                    talking.incrementAndGet()
                    turns.incrementAndGet()
                    var at = System.nanoTime()
                    val stop = at + ms * 1_000_000
                    while (at < stop && b.gone == null) {
                        b.client.sendVoice(CODEC, SILENCE)
                        frames.incrementAndGet()
                        at += 20_000_000
                        val wait = (at - System.nanoTime()) / 1_000_000
                        if (wait > 0) Thread.sleep(wait)
                    }
                    b.client.sendVoiceStop(CODEC)
                    talking.decrementAndGet()
                }
            }, b.name).apply { start() }
        }
        // A line every 30 s while it runs
        while (System.currentTimeMillis() < end) {
            Thread.sleep(minOf(30_000L, end - System.currentTimeMillis()).coerceAtLeast(1))
            println("Load: ${turns.get()} turns, ${frames.get()} voice frames, ${messages.get()} messages, ${talking.get()} talking now")
        }
        threads.forEach { it.join(15_000) }
        val lost = bots.filter { it.gone != null }
        bots.forEach { it.client.disconnect("load test done") }
        Thread.sleep(1500)
        println("Done: ${turns.get()} turns, ${frames.get()} voice frames, ${messages.get()} messages; ${lost.size} lost on the way")
        lost.forEach { println("  ${it.name}: ${it.gone}") }
        assertTrue(lost.isEmpty(), "Test clients were lost")
    }
}
