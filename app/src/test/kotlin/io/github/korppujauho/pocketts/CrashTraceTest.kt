package io.github.korppujauho.pocketts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A test build's crash trace reaches the development machine: sent from a thread of its own (the crashing main
 * thread can't use the network), and kept on the phone until it got through, for the next start.
 */
class CrashTraceTest {
    @get:Rule
    val dir = TemporaryFolder()

    private val trace = "12:00:00.000 CRASH in main: java.lang.IllegalStateException"

    @Test
    fun sentFromAThreadOfItsOwn() {
        var sender: Thread? = null
        assertTrue(AppLog.sendAside { sender = Thread.currentThread(); true })
        assertNotEquals(Thread.currentThread(), sender)
    }

    @Test
    fun aSendThatHangsIsntWaitedForLong() {
        val started = System.currentTimeMillis()
        assertFalse(AppLog.sendAside(ms = 200) { Thread.sleep(5_000); true })
        assertTrue(System.currentTimeMillis() - started < 2_000)
    }

    @Test
    fun sentNowLeavesNothingBehind() {
        var got: String? = null
        assertTrue(AppLog.reportCrash(dir.root, trace) { got = trace; true })
        assertEquals(trace, got)
        assertNull(AppLog.unsentCrash(dir.root))
    }

    @Test
    fun notSentIsKeptForTheNextStartOnce() {
        assertFalse(AppLog.reportCrash(dir.root, trace) { false })
        assertFalse(AppLog.reportCrash(File(dir.root, "other").apply { mkdirs() }, trace) { error("no network on this thread") })
        assertEquals(trace, AppLog.unsentCrash(dir.root))
        // Once only
        assertNull(AppLog.unsentCrash(dir.root))
    }
}
