package io.github.korppujauho.pocketts

import android.os.Build
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The app's log. Test builds also send it to the development machine (BuildConfig.DEV_LOG_URL), so problems on a
 * phone can be seen there; release builds don't.
 */
object AppLog {
    private const val TAG = "PocketTS"
    private val queue = LinkedBlockingQueue<String>(5000)
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    @Volatile private var started = false

    fun start() {
        val url = BuildConfig.DEV_LOG_URL
        if (started || url.isEmpty()) return
        started = true
        Thread({ sendLoop("$url/log") }, "log-sender").apply { isDaemon = true }.start()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log("CRASH in ${t.name}: ${Log.getStackTraceString(e)}")
            flushNow("$url/log")
            previous?.uncaughtException(t, e)
        }
        log("Started ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
    }

    fun log(msg: String) {
        Log.i(TAG, msg)
        if (started) {
            val line = synchronized(time) { time.format(Date()) } + " " + msg
            queue.offer(line)
        }
    }

    private fun sendLoop(url: String) {
        while (true) {
            val first = queue.poll(2, TimeUnit.SECONDS) ?: continue
            val lines = mutableListOf(first)
            queue.drainTo(lines, 500)
            post(url, lines)
        }
    }

    private fun flushNow(url: String) {
        val lines = mutableListOf<String>()
        queue.drainTo(lines)
        if (lines.isNotEmpty()) post(url, lines)
    }

    private fun post(url: String, lines: List<String>) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 3000
            c.readTimeout = 3000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(lines.joinToString("\n", postfix = "\n").toByteArray()) }
            c.responseCode
            c.disconnect()
        } catch (_: Exception) {
            // The development machine isn't reachable: these lines are only in the phone's own log.
        }
    }
}
