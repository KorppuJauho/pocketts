package io.github.korppujauho.pocketts

import io.github.korppujauho.pocketts.UiSounds.Cue

/**
 * Which file of a sound pack plays for which event. Two kinds of pack are read, both a folder of sound files:
 * - a TeamSpeak 3 pack has a settings.ini, where each event's line names its file: `CONNECTION_CONNECTED = play("connected.wav")`.
 *   An event without a line, or with only spoken text (`say(...)`, not supported), stays silent.
 * - a TeamSpeak 5 or 6 pack has no settings: each file is named after its event (connected.wav, you_were_poked.wav).
 * Each event tries its names best first, then falls back to a broader one (someone who connected to your channel to
 * someone who joined it). Files are known by their lower-case name without folders, as they're kept in one folder.
 */
object SoundPackFiles {
    /** The sound files a pack may have. */
    val EXTENSIONS = setOf("wav", "ogg", "mp3", "opus", "flac", "m4a")

    /** An event's names: its TeamSpeak 3 settings keys, and its TeamSpeak 5/6 file names (without extension). */
    private class Names(val keys: List<String>, val files: List<String>)

    private val NAMES: Map<Cue, Names> = mapOf(
        Cue.CONNECTED to Names(listOf("CONNECTION_CONNECTED"), listOf("connected")),
        Cue.DISCONNECTED to Names(listOf("CONNECTION_DISCONNECTED"), listOf("disconnected")),
        Cue.CONNECTION_LOST to Names(listOf("CONNECTION_LOST_CONNECTION"), listOf("connection_lost")),
        Cue.KICKED to Names(listOf("YOU_WERE_KICKED_FROM_CHANNEL"), listOf("you_kicked_channel")),
        Cue.KICKED_SERVER to Names(listOf("YOU_WERE_KICKED_FROM_SERVER"), listOf("you_kicked_server")),
        Cue.BANNED to Names(listOf("YOU_WERE_BANNED"), listOf("you_were_banned")),
        Cue.CHANNEL_SWITCHED to Names(listOf("YOU_SWITCHED_CHANNEL"), listOf("channel_switched")),
        Cue.MOVED to Names(listOf("YOU_WERE_MOVED_TO_DIFFERENT_CHANNEL"), listOf("you_were_moved")),
        Cue.SOMEONE_JOINED to Names(
            listOf(
                "CLIENT_SWITCHED_TO_CURRENT_CHANNEL_STAYS", "CLIENT_SWITCHED_TO_CURRENT_CHANNEL_APPEARS",
                "CLIENT_MOVED_TO_CURRENT_CHANNEL_STAYS", "CLIENT_MOVED_TO_CURRENT_CHANNEL_APPEARS",
            ),
            listOf("neutral_switched_tocurrentchannel", "neutral_moved_tocurrentchannel", "client_joined"),
        ),
        Cue.SOMEONE_CONNECTED to Names(
            listOf("CLIENT_CONNECTION_CONNECTED_CURRENT_CHANNEL"),
            listOf("neutral_connection_connected_currentchannel"),
        ),
        Cue.SOMEONE_LEFT to Names(
            listOf(
                "CLIENT_SWITCHED_FROM_CURRENT_CHANNEL_STAYS", "CLIENT_SWITCHED_FROM_CURRENT_CHANNEL_DISAPPEARS",
                "CLIENT_MOVED_FROM_CURRENT_CHANNEL_STAYS", "CLIENT_MOVED_FROM_CURRENT_CHANNEL_DISAPPEARS",
            ),
            listOf("neutral_switched_awayfromcurrentchannel", "neutral_moved_awayfromcurrentchannel", "client_left"),
        ),
        Cue.SOMEONE_DISCONNECTED to Names(
            listOf("CLIENT_CONNECTION_DISCONNECTED_CURRENT_CHANNEL"),
            listOf("neutral_connection_disconnected_currentchannel"),
        ),
        Cue.SOMEONE_LOST_CONNECTION to Names(
            listOf("CLIENT_CONNECTION_LOST_CONNECTION_CURRENT_CHANNEL"),
            listOf("neutral_connection_connectionlost_currentchannel"),
        ),
        Cue.MESSAGE_IN to Names(
            listOf("CHAT_RECEIVED_MESSAGE_CLIENT", "CHAT_RECEIVED_MESSAGE_CHANNEL"),
            listOf("chat_message_inbound", "chat_inbound"),
        ),
        Cue.MESSAGE_OUT to Names(
            listOf("CHAT_SENT_MESSAGE_CLIENT", "CHAT_SENT_MESSAGE_CHANNEL"),
            listOf("chat_message_outbound", "chat_outbound"),
        ),
        Cue.POKE to Names(listOf("OTHER_RECEIVED_POKE"), listOf("you_were_poked")),
        Cue.MIC_OFF to Names(listOf("SOUND_CAPTURE_MUTED"), listOf("mic_muted")),
        Cue.MIC_ON to Names(listOf("SOUND_CAPTURE_UNMUTED"), listOf("mic_activated")),
        Cue.SOUND_OFF to Names(listOf("SOUND_PLAYBACK_MUTED"), listOf("sound_muted")),
        Cue.SOUND_ON to Names(listOf("SOUND_PLAYBACK_UNMUTED"), listOf("sound_resumed")),
        Cue.STREAM_STARTED to Names(emptyList(), listOf("stream_started")),
        Cue.STREAM_STOPPED to Names(emptyList(), listOf("stream_stopped")),
        Cue.SOMEONE_KICKED_CHANNEL to Names(
            listOf("CLIENT_WAS_KICKED_FROM_CHANNEL_FROM_CURRENT_CHANNEL_STAYS", "CLIENT_WAS_KICKED_FROM_CHANNEL_FROM_CURRENT_CHANNEL_DISAPPEARS"),
            listOf("neutral_kicked_channel_awayfromcurrentchannel"),
        ),
        Cue.SOMEONE_KICKED_SERVER to Names(listOf("CLIENT_WAS_KICKED_FROM_SERVER_CURRENT_CHANNEL"), listOf("neutral_kicked_server_currentchannel")),
        Cue.SOMEONE_BANNED to Names(listOf("CLIENT_WAS_BANNED_CURRENT_CHANNEL"), listOf("neutral_banned_server_currentchannel")),
        Cue.WHISPER_RECEIVED to Names(listOf("SPECIAL_WHISPER_NOTIFY"), listOf("whisper_received")),
        Cue.AWAY_ON to Names(emptyList(), listOf("away_activated")),
        Cue.AWAY_OFF to Names(emptyList(), listOf("away_deactivated")),
        Cue.TALK_POWER_GRANTED to Names(listOf("YOU_WERE_GRANTED_TALK_POWER"), listOf("talkpower_granted")),
        Cue.TALK_POWER_REVOKED to Names(listOf("YOU_WERE_REVOKED_TALK_POWER"), listOf("talkpower_revoked")),
        Cue.SERVER_GROUP_ADDED to Names(listOf("YOU_SERVERGROUP_ADDED_BY_USER", "YOU_SERVERGROUP_ADDED_BY_SERVER"), listOf("servergroup_assigned")),
        Cue.SERVER_GROUP_REMOVED to Names(listOf("YOU_SERVERGROUP_REMOVED_BY_USER", "YOU_SERVERGROUP_REMOVED_BY_SERVER"), listOf("servergroup_revoked")),
        Cue.CHANNEL_GROUP_CHANGED to Names(listOf("YOU_CHANNELGROUP_CHANGED_BY_USER", "YOU_CHANNELGROUP_CHANGED_BY_SERVER"), listOf("channelgroup_changed")),
        Cue.PERMISSION_REFUSED to Names(listOf("SERVER_INSUFFICIENT_PERMISSIONS"), listOf("insufficient_permissions")),
        Cue.CHANNEL_CREATED to Names(listOf("CHANNEL_CREATED_BY_OTHER", "CHANNEL_CREATED_BY_YOU"), listOf("channel_created")),
        Cue.CHANNEL_DELETED to Names(
            listOf("CHANNEL_DELETED_BY_OTHER", "CHANNEL_DELETED_BY_YOU", "CHANNEL_DELETED_BY_SERVER"), listOf("channel_deleted"),
        ),
        Cue.CHANNEL_EDITED to Names(
            listOf("CHANNEL_EDITED_OTHER_BY_OTHER", "CHANNEL_EDITED_OTHER_BY_YOU", "CHANNEL_EDITED_OTHER_BY_SERVER"), listOf("channel_edited"),
        ),
        Cue.YOUR_CHANNEL_EDITED to Names(listOf("CHANNEL_EDITED_CURRENT_BY_OTHER", "CHANNEL_EDITED_CURRENT_BY_YOU"), listOf("your_channel_was_edited")),
        Cue.CHANNEL_MOVED to Names(listOf("CHANNEL_MOVED_BY_OTHER", "CHANNEL_MOVED_BY_YOU"), listOf("channel_moved")),
    )

    /** A finer event without a sound of its own plays its broader one's. */
    private val BROADER = mapOf(
        Cue.KICKED_SERVER to Cue.KICKED,
        Cue.BANNED to Cue.KICKED_SERVER,
        Cue.SOMEONE_CONNECTED to Cue.SOMEONE_JOINED,
        Cue.SOMEONE_DISCONNECTED to Cue.SOMEONE_LEFT,
        Cue.SOMEONE_LOST_CONNECTION to Cue.SOMEONE_DISCONNECTED,
        Cue.SOMEONE_KICKED_CHANNEL to Cue.SOMEONE_LEFT,
        Cue.SOMEONE_KICKED_SERVER to Cue.SOMEONE_DISCONNECTED,
        Cue.SOMEONE_BANNED to Cue.SOMEONE_KICKED_SERVER,
        Cue.YOUR_CHANNEL_EDITED to Cue.CHANNEL_EDITED,
    )

    /** A pack's name and settings from its settings.ini (TeamSpeak 3): the [info] name, and each key's file. */
    class Ini(val name: String?, val files: Map<String, String?>)

    /** settings.ini: `KEY = play("file.wav")` per event (a key with no play(), such as say(), is a silent event). */
    fun parseIni(text: String): Ini {
        var name: String? = null
        var section = ""
        val files = HashMap<String, String?>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (section == "info") {
                if (key.equals("name", ignoreCase = true)) name = value.trim('"').ifBlank { null }
                continue
            }
            val file = PLAY.find(value)?.groupValues?.get(1)
            files[key.uppercase()] = file?.let { fileName(it) }
        }
        return Ini(name, files)
    }

    private val PLAY = Regex("""play\(\s*"([^"]+)"\s*\)""", RegexOption.IGNORE_CASE)

    /** A path as the pack's files are kept: its lower-case name, without folders. */
    fun fileName(path: String): String = path.replace('\\', '/').substringAfterLast('/').trim().lowercase()

    /** Which of the pack's [files] (lower-case names) each event plays; [ini] when the pack has a settings.ini. */
    fun match(files: Set<String>, ini: Ini?): Map<Cue, String> {
        val byStem = files.groupBy { it.substringBeforeLast('.') }.mapValues { (_, v) -> v.sortedBy(::rank).first() }
        val direct = HashMap<Cue, String>()
        // In settings.ini an event's line without a file is silent on purpose: it doesn't take a broader event's sound
        val silent = if (ini == null) emptySet() else NAMES.filter { (_, n) -> n.keys.any { it in ini.files } }.keys
        for ((cue, names) in NAMES) {
            val file = if (ini != null) {
                names.keys.firstNotNullOfOrNull { k -> ini.files[k]?.takeIf { it in files } }
            } else {
                names.files.firstNotNullOfOrNull { byStem[it] }
            }
            if (file != null) direct[cue] = file
        }
        val out = HashMap(direct)
        for (cue in Cue.entries) {
            if (cue in out || cue in silent) continue
            var broader = BROADER[cue]
            while (broader != null && broader !in direct) broader = BROADER[broader]
            broader?.let { b -> direct[b]?.let { out[cue] = it } }
        }
        return out
    }

    // When a pack has the same sound in two formats, the one Android plays most surely
    private fun rank(name: String) = listOf("wav", "ogg", "mp3", "opus", "flac", "m4a").indexOf(name.substringAfterLast('.'))
}
