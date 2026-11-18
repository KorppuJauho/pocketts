package io.github.korppujauho.pocketts

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import io.github.korppujauho.pocketts.UiSounds.Cue
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * A sound pack of the person's own, in place of the app's tones for events: imported from a folder or a .zip of one,
 * a TeamSpeak 3 pack (with its settings.ini) or a TeamSpeak 5/6 one (files named after events), see [SoundPackFiles].
 * Its files are copied into the app's own storage, one pack at a time; none is part of the app, and nothing is fetched
 * from anywhere.
 */
object SoundPack {
    /** The imported pack: its name, and the file each event plays (an event without one is silent). */
    class Pack(val name: String, val files: Map<Cue, String>, internal val dir: File)

    /** Why an import didn't happen. */
    class ImportFailed(message: String) : Exception(message)

    /** A decoded sound: 16-bit samples, interleaved when [channels] is 2. */
    class Pcm(val samples: ShortArray, val rate: Int, val channels: Int)

    private const val MAX_FILES = 300
    private const val MAX_FILE_BYTES = 5L * 1024 * 1024
    private const val MAX_BYTES = 20L * 1024 * 1024

    @Volatile var pack: Pack? = null
        private set
    private val decoded = HashMap<Cue, Pcm?>()

    private fun dir(context: Context) = File(context.filesDir, "soundpack")
    private fun meta(context: Context) = File(dir(context), "pack.json")

    /** The pack imported before, if any (at the app's start). */
    fun load(context: Context) {
        pack = try {
            val o = JSONObject(meta(context).readText())
            val map = o.getJSONObject("files")
            val files = Cue.entries.mapNotNull { cue -> map.optString(cue.name).ifEmpty { null }?.let { cue to it } }.toMap()
            Pack(o.optString("name"), files, dir(context))
        } catch (_: Exception) {
            null
        }
        synchronized(decoded) { decoded.clear() }
    }

    /** Lets go of the pack: the tones play again. */
    fun remove(context: Context) {
        dir(context).deleteRecursively()
        pack = null
        synchronized(decoded) { decoded.clear() }
        AppLog.log("Sound pack removed")
    }

    /** Imports the pack in folder [uri] (a tree from the folder picker), or in the .zip file [uri]; replaces the one before. */
    fun import(context: Context, uri: Uri, folder: Boolean): Pack {
        val staging = File(context.filesDir, "soundpack.new").apply { deleteRecursively(); mkdirs() }
        try {
            val budget = Budget()
            val name = if (folder) copyFolder(context, uri, staging, budget) else copyZip(context, uri, staging, budget)
            val files = staging.list()?.toSet().orEmpty() - "settings.ini"
            if (files.isEmpty()) throw ImportFailed(context.getString(R.string.sound_pack_no_sounds))
            val ini = File(staging, "settings.ini").takeIf { it.exists() }?.let { SoundPackFiles.parseIni(it.readText()) }
            val matched = SoundPackFiles.match(files, ini)
            if (matched.isEmpty()) throw ImportFailed(context.getString(R.string.sound_pack_no_events))
            // Only what's played is kept
            val used = matched.values.toSet()
            staging.listFiles()?.forEach { if (it.name !in used) it.delete() }
            val packName = ini?.name ?: name
            File(staging, "pack.json").writeText(
                JSONObject().put("name", packName).put("files", JSONObject(matched.mapKeys { it.key.name })).toString()
            )
            dir(context).deleteRecursively()
            if (!staging.renameTo(dir(context))) throw ImportFailed(context.getString(R.string.file_not_written))
            load(context)
            AppLog.log("Sound pack imported: ${matched.size} events have a sound (${if (ini != null) "TeamSpeak 3" else "file names"})")
            return pack ?: throw ImportFailed(context.getString(R.string.file_not_written))
        } finally {
            staging.deleteRecursively()
        }
    }

    /** What's left of the size limits while copying. */
    private class Budget(var files: Int = 0, var bytes: Long = 0)

    /** Keeps a pack's file (a sound or settings.ini) under its plain lower-case name; skips anything else. */
    private fun keep(name: String, input: InputStream, into: File, budget: Budget, context: Context) {
        val plain = SoundPackFiles.fileName(name)
        val ext = plain.substringAfterLast('.', "")
        if (plain != "settings.ini" && ext !in SoundPackFiles.EXTENSIONS) return
        val target = File(into, plain)
        if (target.exists()) return // the same name twice (another folder in the zip): the first one stays
        if (++budget.files > MAX_FILES) throw ImportFailed(context.getString(R.string.sound_pack_too_big))
        target.outputStream().use { out ->
            val buf = ByteArray(16 * 1024)
            var size = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                size += n
                budget.bytes += n
                if (size > MAX_FILE_BYTES || budget.bytes > MAX_BYTES) throw ImportFailed(context.getString(R.string.sound_pack_too_big))
                out.write(buf, 0, n)
            }
        }
    }

    private fun copyZip(context: Context, uri: Uri, into: File, budget: Budget): String {
        val input = context.contentResolver.openInputStream(uri) ?: throw ImportFailed(context.getString(R.string.file_not_opened))
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                // Names are flattened to the file's own name, so nothing lands outside the pack's folder
                if (!e.isDirectory) keep(e.name, zip, into, budget, context)
            }
        }
        return displayName(context, uri)?.substringBeforeLast('.') ?: "Sound pack"
    }

    private fun copyFolder(context: Context, tree: Uri, into: File, budget: Budget): String {
        val resolver = context.contentResolver
        val root = DocumentsContract.getTreeDocumentId(tree)
        // The chosen folder's own files, and one level of folders under it (a pack zipped or copied with its folder)
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 1) walk(id, depth + 1)
                        continue
                    }
                    resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { keep(name, it, into, budget, context) }
                }
            }
        }
        walk(root, 0)
        return root.substringAfterLast(':').substringAfterLast('/').ifBlank { "Sound pack" }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** [cue]'s sound in the pack, decoded (once); null when the pack has none for it, or it can't be read. */
    fun pcm(cue: Cue): Pcm? {
        val p = pack ?: return null
        val file = p.files[cue] ?: return null
        synchronized(decoded) {
            if (cue in decoded) return decoded[cue]
        }
        val pcm = try {
            decode(File(p.dir, file))
        } catch (e: Exception) {
            AppLog.log("Sound pack: ${cue.name} can't be played: ${e.javaClass.simpleName}")
            null
        }
        synchronized(decoded) { decoded[cue] = pcm }
        return pcm
    }

    /** A sound file as 16-bit samples, with Android's own decoders (at most 10 s of it: these are short sounds). */
    private fun decode(file: File): Pcm {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.path)
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            val out = java.io.ByteArrayOutputStream()
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (true) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (o >= 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        val bytes = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.get(bytes)
                        out.write(bytes)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        if (out.size() > 10 * rate * channels * 2) break
                    }
                }
            } finally {
                codec.release()
            }
            val shorts = ShortArray(out.size() / 2)
            java.nio.ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return Pcm(shorts, rate, channels.coerceIn(1, 2))
        } finally {
            ex.release()
        }
    }
}
