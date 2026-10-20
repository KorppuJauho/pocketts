package io.github.korppujauho.mobilets6.proto

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * TeamSpeak's file browser: folders, uploads and downloads (pictures in the chat, icons, avatars), as WebTS does it.
 * The request goes over the connection (ftinitupload / ftinitdownload); the server answers with a key and a port, and
 * the bytes go over a TCP connection to that port: first the key, then the file.
 *
 * The calls block, so they belong on a background thread (never the connection's). Feed every command to [onCommand].
 */
class FileTransfer(
    private val client: TsClient,
    /** Where the bytes go: normally the server's own address, and the port it names unless [portOverride] is set. */
    private val portOverride: Int? = null,
) {
    class Error(message: String, val code: Int = 0) : Exception(message)

    companion object {
        const val FILE_EXISTS = 2050
        private const val ANSWER_MS = 15_000L
        private const val TRANSFER_MS = 120_000
    }

    private class Waiter(val pick: (Command) -> Any?, val result: CompletableFuture<Any?>)

    private val waiters = CopyOnWriteArrayList<Waiter>()
    private val counter = AtomicInteger(0)
    private val slots = Semaphore(4) // a few at once, like WebTS

    private fun nextId() = counter.updateAndGet { (it % 65535) + 1 }

    /** Every command from the server (any thread). */
    fun onCommand(cmd: Command) {
        for (w in waiters) {
            try {
                val v = w.pick(cmd) ?: continue
                w.result.complete(v)
            } catch (e: Exception) {
                w.result.completeExceptionally(e)
            }
        }
    }

    fun onDisconnected() {
        for (w in waiters) w.result.completeExceptionally(Error("The connection closed"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> await(command: String, pick: (Command) -> T?): T {
        val w = Waiter(pick, CompletableFuture())
        waiters.add(w)
        try {
            client.sendCommand(command)
            return w.result.get(ANSWER_MS, TimeUnit.MILLISECONDS) as T
        } catch (e: TimeoutException) {
            throw Error("TeamSpeak didn't answer")
        } catch (e: ExecutionException) {
            throw (e.cause as? Error) ?: Error(e.cause?.message ?: "failed")
        } finally {
            waiters.remove(w)
        }
    }

    private fun answer(cmd: Command, returnCode: String): Pair<Int, String>? {
        if (cmd.name != "error" || cmd["return_code"] != returnCode) return null
        return (cmd["id"]?.toIntOrNull() ?: 0) to cmd["msg"].orEmpty()
    }

    /** A folder in a channel's files ("/webTS"); one that already exists is fine. */
    fun createDirectory(cid: Int, dirname: String, cpw: String = "") {
        val rc = "ft-${nextId()}"
        val (id, msg) = await(Commands.build("ftcreatedir", "cid" to cid, "cpw" to cpw, "dirname" to dirname, "return_code" to rc)) { answer(it, rc) }
        if (id != 0 && id != FILE_EXISTS) throw Error(msg.ifEmpty { "refused" }, id)
    }

    private class Start(val serverftfid: String, val key: String, val port: Int, val size: Long)

    private fun start(command: String, params: List<Pair<String, Any?>>, started: String): Start {
        val ftid = nextId()
        val rc = "ft-${nextId()}"
        val cmd = Commands.build(command, *(listOf("clientftfid" to ftid) + params + ("return_code" to rc)).toTypedArray())
        return await(cmd) { c ->
            val p = c.params
            when {
                c.name == started && p["clientftfid"]?.toIntOrNull() == ftid -> {
                    val port = p["port"]?.toIntOrNull() ?: 0
                    val key = p["ftkey"].orEmpty()
                    if (key.isEmpty() || port !in 1..65535) throw Error("TeamSpeak gave no transfer address")
                    Start(p["serverftfid"].orEmpty(), key, port, p["size"]?.toLongOrNull() ?: 0)
                }
                c.name == "notifystatusfiletransfer" && p["clientftfid"]?.toIntOrNull() == ftid ->
                    throw Error(p["msg"].orEmpty().ifEmpty { "refused" }, p["status"]?.toIntOrNull() ?: 0)
                else -> answer(c, rc)?.let { (id, msg) -> if (id != 0) throw Error(msg.ifEmpty { "refused" }, id) else null }
            }
        }
    }

    private fun socket(port: Int): Socket {
        val host = client.serverAddress ?: throw Error("Not connected")
        return Socket().apply {
            connect(InetSocketAddress(host, portOverride ?: port), 10_000)
            soTimeout = TRANSFER_MS
        }
    }

    /** Downloads [name] (a full path) from channel [cid]'s files; refused before reading when over [maxBytes]. */
    fun download(cid: Int, name: String, maxBytes: Long, cpw: String = ""): ByteArray {
        slots.acquire()
        try {
            val s = start("ftinitdownload", listOf("name" to name, "cid" to cid, "cpw" to cpw, "seekpos" to 0), "notifystartdownload")
            if (s.size > maxBytes) {
                if (s.serverftfid.isNotEmpty()) client.sendCommand(Commands.build("ftstop", "serverftfid" to s.serverftfid, "delete" to 0))
                throw Error("The file is too big")
            }
            if (s.size == 0L) return ByteArray(0)
            socket(s.port).use { sock ->
                sock.getOutputStream().apply { write(s.key.toByteArray(Charsets.US_ASCII)); flush() }
                val input = sock.getInputStream()
                val out = ByteArrayOutputStream(s.size.toInt())
                val buf = ByteArray(16384)
                while (out.size() < s.size) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), s.size - out.size()).toInt())
                    if (n < 0) throw Error("The download connection closed early")
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            slots.release()
        }
    }

    /** Uploads [data] as [name] (a full path) into channel [cid]'s files (channel 0: the server's own, e.g. "/avatar"). */
    fun upload(cid: Int, name: String, data: ByteArray, cpw: String = "", overwrite: Boolean = false) {
        slots.acquire()
        try {
            val s = start(
                "ftinitupload",
                listOf("name" to name, "cid" to cid, "cpw" to cpw, "size" to data.size, "overwrite" to if (overwrite) 1 else 0, "resume" to 0),
                "notifystartupload",
            )
            socket(s.port).use { sock ->
                val out = sock.getOutputStream()
                out.write(s.key.toByteArray(Charsets.US_ASCII))
                out.write(data)
                out.flush()
                sock.shutdownOutput()
                // Wait for the server to close its side (it has everything then)
                try {
                    while (sock.getInputStream().read() >= 0) Unit
                } catch (_: Exception) {
                }
            }
        } finally {
            slots.release()
        }
    }
}
