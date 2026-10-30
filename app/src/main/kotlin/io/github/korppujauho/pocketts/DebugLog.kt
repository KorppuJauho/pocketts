package io.github.korppujauho.pocketts

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The debug log (Settings › Advanced › Debug log): the app's log for a bug report, without what identifies anyone
 * (see [io.github.korppujauho.pocketts.proto.LogPrivacy]: [AppLog] gives it lines already cleaned). It's kept in the
 * app's own storage, never backed up: at most about 1 MB in two files, the oldest lines dropping off. Lines are written
 * on a thread of their own, so nothing waits for the storage. The app never sends it: the person shares it ([share]) or
 * deletes it.
 */
class DebugLog(context: Context) {
    private val folder = folder(context).apply { mkdirs() }
    private val now = File(folder, NOW)
    private val before = File(folder, BEFORE)
    private val queue = LinkedBlockingQueue<String>(5000)
    @Volatile private var running = true

    init {
        Thread({
            while (running) {
                val first = queue.poll(1, TimeUnit.SECONDS) ?: continue
                val lines = mutableListOf(first)
                queue.drainTo(lines)
                write(lines)
            }
        }, "debug-log").apply { isDaemon = true }.start()
    }

    fun add(line: String) {
        queue.offer(line)
    }

    /** What's waiting, written now: before a crash closes the app, or before sharing. */
    fun flush() {
        val lines = mutableListOf<String>()
        queue.drainTo(lines)
        write(lines)
    }

    /** Stops writing (the setting turned off). */
    fun stop() {
        running = false
        flush()
    }

    /** Empties the log (it goes on with new lines). */
    @Synchronized
    fun clear() {
        queue.clear()
        now.delete()
        before.delete()
    }

    @Synchronized
    private fun write(lines: List<String>) {
        if (lines.isEmpty()) return
        try {
            now.appendText(lines.joinToString("\n", postfix = "\n"))
            if (now.length() > HALF) {
                before.delete()
                now.renameTo(before)
            }
        } catch (_: Exception) {
            // Storage full or gone: these lines are only in the phone's system log
        }
    }

    companion object {
        private const val NOW = "log.txt"
        private const val BEFORE = "log-1.txt"
        private const val HALF = 512 * 1024L

        private fun folder(context: Context) = File(context.noBackupFilesDir, "debug-log")

        /** Removes the log altogether (the setting turned off). */
        fun delete(context: Context) {
            folder(context).deleteRecursively()
        }

        /**
         * The log as one text file to share through Android's share menu (older lines first), or null when there's
         * nothing in it yet.
         */
        fun share(context: Context): Uri? {
            val dir = folder(context)
            val text = listOf(File(dir, BEFORE), File(dir, NOW)).filter { it.exists() }.joinToString("") { it.readText() }
            if (text.isBlank()) return null
            val out = File(context.cacheDir, "shared").apply { mkdirs() }.let { File(it, "PocketTS-debug-log.txt") }
            out.writeText(text)
            return FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        }
    }
}
