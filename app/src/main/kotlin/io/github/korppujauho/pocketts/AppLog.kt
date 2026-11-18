package io.github.korppujauho.pocketts

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.korppujauho.pocketts.proto.LogPrivacy
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The app's log. Lines mark what could identify someone (see [LogPrivacy]); it goes to:
 * - the debug log ([DebugLog], when it's on in Settings › Advanced), cleaned of that, for a bug report;
 * - the phone's system log: cleaned too in the release, as written in test builds;
 * - in test builds, the development machine (BuildConfig.DEV_LOG_URL), as written, so problems on a phone can be
 *   seen there.
 *
 * Crashes are written down only where the log goes: the debug log or the development machine. The release with the
 * debug log off records nothing.
 */
object AppLog {
    private const val TAG = "PocketTS"
    private val queue = LinkedBlockingQueue<String>(5000)
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private val redactor = LogPrivacy.Redactor()
    @Volatile private var started = false // sending to the development machine
    @Volatile private var debug: DebugLog? = null
    private var began = false

    fun start(context: Context) {
        if (began) return
        began = true
        val url = BuildConfig.DEV_LOG_URL
        if (url.isNotEmpty()) {
            started = true
            Thread({ sendLoop("$url/log") }, "log-sender").apply { isDaemon = true }.start()
        }
        val settings = VoiceSettings.load(context)
        if (settings.debugLog) debug = DebugLog(context.applicationContext)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val files = context.applicationContext.filesDir
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            if (started || debug != null) {
                val crash = "CRASH in ${t.name}: ${Log.getStackTraceString(e)}"
                // Not through the queue: the sender could take it there and the app stop before it's sent
                log(crash, toMachine = false)
                if (started) {
                    // Kept on the phone first: sent again at the next start if it doesn't get through now
                    val line = synchronized(time) { time.format(Date()) } + " " + LogPrivacy.plain(crash)
                    reportCrash(files, line) { flushNow("$url/log", line) }
                }
                debug?.flush()
            }
            previous?.uncaughtException(t, e)
        }
        log("Started ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        if (debug != null) log("Settings: ${settings.forLog()}")
        // The debug log only says what happened while it was on; test builds' own log says all
        if (started) lastStops(context, 0L) else if (debug != null) lastStops(context, debugSince(context))
        if (started) unsentCrash(files)?.let { log("A crash before, not sent then:\n$it") }
    }

    /** The debug log turned on (starting with what matters for a bug report), or off (and deleted). */
    fun setDebugLog(context: Context, on: Boolean, settings: VoiceSettings) {
        if (on == (debug != null)) return
        if (on) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(SINCE, System.currentTimeMillis()).apply()
            debug = DebugLog(context.applicationContext)
            log("Debug log on: ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            log("Settings: ${settings.forLog()}")
        } else {
            debug?.stop()
            debug = null
            DebugLog.delete(context)
        }
    }

    /** The debug log as a file to share, with what's waiting written first; null when it's empty. */
    fun shareDebugLog(context: Context): android.net.Uri? {
        debug?.flush()
        return DebugLog.share(context)
    }

    /** Empties the debug log (it goes on while it's on). */
    fun clearDebugLog(context: Context) {
        debug?.clear() ?: DebugLog.delete(context)
    }

    // Why the app stopped the last times, as Android recorded it: a crash in native code (WebRTC's) or Android
    // stopping the app leaves no line of its own
    private const val PREFS = "debug-log"
    private const val SINCE = "since" // when the debug log was turned on

    private fun debugSince(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SINCE, Long.MAX_VALUE)

    // [since]: only stops after then
    private fun lastStops(context: Context, since: Long) {
        if (Build.VERSION.SDK_INT < 30) return
        val stops = try {
            context.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(null, 0, 5)
        } catch (_: Exception) {
            null
        } ?: return
        for (s in stops.filter { it.timestamp >= since }) {
            val at = synchronized(time) { time.format(Date(s.timestamp)) }
            log("Stopped before at $at: reason ${s.reason}, ${s.description.orEmpty()} (status ${s.status}, importance ${s.importance}, ${s.pss / 1024} MB)")
            val trace = when (s.reason) {
                ApplicationExitInfo.REASON_ANR -> readTrace(s)?.toString(Charsets.UTF_8)?.lineSequence()?.take(60)?.toList()
                // A tombstone, in a binary format: its readable parts (the stack's functions and libraries) are enough
                ApplicationExitInfo.REASON_CRASH_NATIVE -> readTrace(s)?.let { bytes ->
                    Regex("[ -~]{8,}").findAll(String(bytes, Charsets.ISO_8859_1)).map { it.value }.take(150).toList()
                }
                else -> null
            }
            trace?.let { log("  " + it.joinToString("\n  ")) }
        }
    }

    @RequiresApi(30)
    private fun readTrace(s: ApplicationExitInfo): ByteArray? = try {
        s.traceInputStream?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (out.size() < 512 * 1024) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    } catch (_: Exception) {
        null
    }

    /** [toMachine] false: not to the development machine (a crash's trace, sent on its own). */
    fun log(msg: String, toMachine: Boolean = true) {
        val d = debug
        // Cleaned for the debug log, and for the system log in the release
        val clean = if (d != null || !BuildConfig.DEBUG) redactor.redact(msg) else null
        Log.i(TAG, clean ?: LogPrivacy.plain(msg))
        if (started || d != null) {
            val stamp = synchronized(time) { time.format(Date()) }
            if (started && toMachine) queue.offer("$stamp ${LogPrivacy.plain(msg)}")
            d?.add("$stamp $clean")
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

    /** What's waiting, and [last], sent now; true when it got through. */
    private fun flushNow(url: String, last: String): Boolean {
        val lines = mutableListOf<String>()
        queue.drainTo(lines)
        return post(url, lines + last)
    }

    // A test build's crash trace, kept on the phone until the development machine has it
    private const val UNSENT_CRASH = "crash-unsent.txt"

    /**
     * [text] (a crash's trace) kept in [dir], then [send] tried on a thread of its own: a crash on the main thread
     * can't use the network there. The file goes once it's sent; true when it was.
     */
    internal fun reportCrash(dir: java.io.File, text: String, send: () -> Boolean): Boolean {
        val file = java.io.File(dir, UNSENT_CRASH)
        try {
            file.writeText(text)
        } catch (_: Exception) {
            // Not kept: sending it now is all there is
        }
        val sent = sendAside(send = send)
        if (sent) file.delete()
        return sent
    }

    /** [send] on a thread of its own, waited for at most [ms] (the app is about to stop); true when it got through. */
    internal fun sendAside(ms: Long = 4000, send: () -> Boolean): Boolean {
        val ok = java.util.concurrent.atomic.AtomicBoolean(false)
        val sender = Thread({ ok.set(try { send() } catch (_: Exception) { false }) }, "crash-sender").apply { isDaemon = true }
        sender.start()
        sender.join(ms)
        return ok.get()
    }

    /** A crash trace kept in [dir] that didn't get through before, and its file gone; null when there's none. */
    internal fun unsentCrash(dir: java.io.File): String? {
        val file = java.io.File(dir, UNSENT_CRASH)
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (_: Exception) {
            null
        } finally {
            file.delete()
        }
    }

    /** True when the development machine took [lines]. */
    private fun post(url: String, lines: List<String>): Boolean {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 3000
            c.readTimeout = 3000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(lines.joinToString("\n", postfix = "\n").toByteArray()) }
            val code = c.responseCode
            c.disconnect()
            code in 200..299
        } catch (_: Exception) {
            // The development machine isn't reachable: these lines are only in the phone's own log.
            false
        }
    }
}
