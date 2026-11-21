package io.github.korppujauho.pocketts

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The microphone's watcher starts on every Android version the app supports, also those older than Android 12. */
@RunWith(RobolectricTestRunner::class)
class MicWatchTest {
    @Test
    @Config(sdk = [29])
    fun startsOnAndroid10() = start()

    @Test
    @Config(sdk = [36])
    fun startsOnNewAndroid() = start()

    private fun start() {
        MicWatch(RuntimeEnvironment.getApplication()) { 0 }.stop()
    }
}
