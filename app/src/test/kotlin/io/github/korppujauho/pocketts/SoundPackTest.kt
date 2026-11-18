package io.github.korppujauho.pocketts

import android.net.Uri
import io.github.korppujauho.pocketts.UiSounds.Cue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Sound packs: a TeamSpeak 5/6 pack's files are found by their names, a TeamSpeak 3 pack's by its settings.ini; a
 * finer event without a sound plays its broader one's, except where settings.ini makes it quiet on purpose. An import
 * keeps only the pack's sounds, under their own names inside the app's folder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundPackTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun clean() = SoundPack.remove(context)

    @Test
    fun teamSpeak6FilesByName() {
        val files = setOf("connected.wav", "you_were_poked.wav", "neutral_switched_tocurrentchannel.wav", "client_left.wav", "notes.wav")
        val m = SoundPackFiles.match(files, null)
        assertEquals("connected.wav", m[Cue.CONNECTED])
        assertEquals("you_were_poked.wav", m[Cue.POKE])
        assertEquals("neutral_switched_tocurrentchannel.wav", m[Cue.SOMEONE_JOINED])
        // Connecting to your channel has no sound of its own: joining it is the broader one
        assertEquals("neutral_switched_tocurrentchannel.wav", m[Cue.SOMEONE_CONNECTED])
        assertEquals("client_left.wav", m[Cue.SOMEONE_LOST_CONNECTION])
        assertNull(m[Cue.KICKED])
        assertFalse("notes.wav" in m.values)
    }

    /** A TeamSpeak 6 pack's file names (only the names): every event finds its own sound. */
    @Test
    fun aTeamSpeak6PackCoversEveryEvent() {
        val names = """
            away_activated away_deactivated call_incoming call_outgoing channel_created channel_deleted channel_edited
            channel_moved channel_switched channelgroup_changed chat_inbound chat_message_inbound chat_message_outbound
            chat_outbound client_joined client_left connected connection_lost disconnected error incoming_call
            insufficient_permissions mic_activated mic_muted neutral_banned_server_currentchannel
            neutral_connection_connected_currentchannel neutral_connection_connectionlost_currentchannel
            neutral_connection_disconnected_currentchannel neutral_kicked_channel_awayfromcurrentchannel
            neutral_kicked_channel_tocurrentchannel neutral_kicked_server_currentchannel neutral_moved_awayfromcurrentchannel
            neutral_moved_tocurrentchannel neutral_switched_awayfromcurrentchannel neutral_switched_tocurrentchannel
            no_whisper_target_found servergroup_assigned servergroup_revoked sound_muted sound_resumed stop_talking
            stream_started stream_stopped talkpower_granted talkpower_requested talkpower_revoked whisper_received
            you_edited_server you_kicked_channel you_kicked_server you_were_banned you_were_moved you_were_poked
            your_channel_was_edited
        """.trim().split(Regex("\\s+")).map { "$it.wav" }.toSet()
        val m = SoundPackFiles.match(names, null)
        assertEquals(emptyList<Cue>(), Cue.entries.filter { it !in m })
        assertEquals("whisper_received.wav", m[Cue.WHISPER_RECEIVED])
        assertEquals("neutral_kicked_server_currentchannel.wav", m[Cue.SOMEONE_KICKED_SERVER])
        assertEquals("your_channel_was_edited.wav", m[Cue.YOUR_CHANNEL_EDITED])
    }

    @Test
    fun theSurestFormatWhenThereAreTwo() {
        assertEquals("connected.wav", SoundPackFiles.match(setOf("connected.mp3", "connected.wav"), null)[Cue.CONNECTED])
    }

    @Test
    fun teamSpeak3SettingsName() {
        val ini = SoundPackFiles.parseIni(
            """
            [info]
            name = Radio Pack
            author = Jamie

            [sounds]
            CONNECTION_CONNECTED = play("sound/radio/On Air.WAV")
            OTHER_RECEIVED_POKE = play("poke.ogg")
            CLIENT_SWITCHED_TO_CURRENT_CHANNEL_STAYS = play("in.wav")
            CLIENT_CONNECTION_CONNECTED_CURRENT_CHANNEL = say("${'$'}{clientname} connected")
            YOU_WERE_KICKED_FROM_SERVER = play("kick.wav")
            """.trimIndent()
        )
        assertEquals("Radio Pack", ini.name)
        val m = SoundPackFiles.match(setOf("on air.wav", "poke.ogg", "in.wav", "kick.wav", "connected.wav"), ini)
        // By the settings, not by the files' names
        assertEquals("on air.wav", m[Cue.CONNECTED])
        assertEquals("poke.ogg", m[Cue.POKE])
        assertEquals("in.wav", m[Cue.SOMEONE_JOINED])
        // Spoken text only: quiet on purpose, not the broader event's sound
        assertNull(m[Cue.SOMEONE_CONNECTED])
        // Banned has no line: it plays being kicked from the server
        assertEquals("kick.wav", m[Cue.BANNED])
    }

    @Test
    fun aZipKeepsOnlyItsSoundsInsideTheAppsFolder() {
        val zip = File(context.cacheDir, "my-pack.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            for ((name, bytes) in listOf(
                "my-pack/connected.wav" to ByteArray(100),
                "my-pack/you_were_poked.wav" to ByteArray(100),
                "my-pack/readme.txt" to "hi".toByteArray(),
                "../../outside.wav" to ByteArray(10),
            )) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        val pack = SoundPack.import(context, Uri.fromFile(zip), folder = false)
        assertEquals("connected.wav", pack.files[Cue.CONNECTED])
        assertEquals("you_were_poked.wav", pack.files[Cue.POKE])
        val kept = File(context.filesDir, "soundpack").list()!!.toSet()
        assertEquals(setOf("connected.wav", "you_were_poked.wav", "pack.json"), kept)
        assertFalse(File(context.filesDir.parentFile, "outside.wav").exists())
        // Read again at the app's next start
        SoundPack.load(context)
        assertEquals(pack.files, SoundPack.pack!!.files)
    }

    @Test
    fun aTs3SoundpackFileIsReadAsTheZipItIs() {
        val file = File(context.cacheDir, "radio.ts3_soundpack")
        ZipOutputStream(file.outputStream()).use { z ->
            for ((name, text) in listOf(
                "package.ini" to "Name = Radio Pack\nType = Sound\n",
                "sound/radio/settings.ini" to "[info]\nname = Radio Pack\n[sounds]\nCONNECTION_CONNECTED = play(\"on.wav\")\nOTHER_RECEIVED_POKE = play(\"beep.wav\")\n",
                "sound/radio/on.wav" to "x",
                "sound/radio/beep.wav" to "x",
            )) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        val pack = SoundPack.import(context, Uri.fromFile(file), folder = false)
        assertEquals("Radio Pack", pack.name)
        assertEquals("on.wav", pack.files[Cue.CONNECTED])
        assertEquals("beep.wav", pack.files[Cue.POKE])
    }

    @Test
    fun aZipWithoutEventsIsRefused() {
        val zip = File(context.cacheDir, "music.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("song.mp3"))
            z.write(ByteArray(10))
            z.closeEntry()
        }
        val failed = runCatching { SoundPack.import(context, Uri.fromFile(zip), folder = false) }.exceptionOrNull()
        assertTrue(failed is SoundPack.ImportFailed)
        assertNull(SoundPack.pack)
    }
}
