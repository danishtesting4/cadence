package app.cadence.platform

import app.cadence.model.Track
import app.cadence.player.AudioEngine
import app.cadence.player.DesktopAudioEngine
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.math.abs

actual object Platform {

    actual val isDesktop: Boolean = true

    private val audioExtensions = setOf(
        "mp3", "flac", "ogg", "oga", "wav", "m4a", "aac", "opus", "aiff", "aif", "wma",
    )

    private val dataDirectory: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val base = when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"))
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(System.getProperty("user.home"), ".local/share").path)
        }
        File(base, "Cadence").apply { mkdirs() }
    }

    private fun stateFile(key: String) = File(dataDirectory, "$key.json")

    actual suspend fun ensureLibraryAccess(): Boolean = true

    actual suspend fun importTracks(): List<Track> {
        val folder = chooseFolder() ?: return emptyList()
        return withContext(Dispatchers.IO) { scanFolder(folder) }
    }

    private suspend fun chooseFolder(): File? = withContext(Dispatchers.Swing) {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a folder with music"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun scanFolder(folder: File): List<Track> = folder
        .walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in audioExtensions }
        .map { readTrack(it) }
        .sortedWith(compareBy({ it.title.lowercase() }, { it.artist.lowercase() }))
        .toList()

    private fun readTrack(file: File): Track {
        var title = file.nameWithoutExtension
        var artist = ""
        var album = ""
        var trackNumber = 0
        var year = 0
        var durationMs = 0L

        runCatching {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            if (tag != null) {
                tag.getFirst(FieldKey.TITLE).takeIf { it.isNotBlank() }?.let { title = it }
                artist = tag.getFirst(FieldKey.ARTIST).orEmpty()
                if (artist.isBlank()) artist = tag.getFirst(FieldKey.ALBUM_ARTIST).orEmpty()
                album = tag.getFirst(FieldKey.ALBUM).orEmpty()
                trackNumber = tag.getFirst(FieldKey.TRACK).toIntOrNull() ?: 0
                year = tag.getFirst(FieldKey.YEAR).take(4).toIntOrNull() ?: 0
            }
            durationMs = (audio.audioHeader?.trackLength ?: 0).toLong() * 1000L
        }

        if (artist.isBlank() && album.isBlank() && title == file.nameWithoutExtension && title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            artist = parts[0].trim()
            title = parts[1].trim()
        }

        val id = "d_" + abs(file.absolutePath.hashCode()).toString(16)
        return Track(
            id = id,
            uri = file.absolutePath,
            title = title.trim().ifBlank { file.nameWithoutExtension },
            artist = artist.trim(),
            album = album.trim(),
            durationMs = durationMs,
            trackNumber = trackNumber,
            year = year,
            addedAt = file.lastModified(),
        )
    }

    actual suspend fun loadArtwork(track: Track): ByteArray? = withContext(Dispatchers.IO) {
        val cached = File(artworkDirectory(), "${track.id}.img")
        if (cached.isFile && cached.length() > 0L) return@withContext cached.readBytes()

        val bytes = extractArtwork(track) ?: return@withContext null
        runCatching {
            artworkDirectory().mkdirs()
            cached.writeBytes(bytes)
        }
        bytes
    }

    private fun artworkDirectory() = File(dataDirectory, "artwork")

    private fun extractArtwork(track: Track): ByteArray? {
        val file = File(track.uri)
        if (!file.canRead()) return null

        runCatching {
            AudioFileIO.read(file).tag?.firstArtwork?.binaryData
        }.getOrNull()?.let { if (it.isNotEmpty()) return it }

        // Fall back to the usual folder cover files next to the track.
        val folder = file.parentFile ?: return null
        val names = listOf("cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.png", "front.jpg")
        for (name in names) {
            val cover = File(folder, name)
            if (cover.isFile && cover.length() > 0L) {
                return runCatching { cover.readBytes() }.getOrNull()
            }
        }
        return null
    }

    actual fun readState(key: String): String? = runCatching {
        stateFile(key).takeIf { it.isFile }?.readText()
    }.getOrNull()

    actual fun writeState(key: String, value: String) {
        runCatching {
            dataDirectory.mkdirs()
            stateFile(key).writeText(value)
        }
    }

    actual fun downloadsDir(): String = File(dataDirectory, "downloads").apply { mkdirs() }.absolutePath

    actual fun writeFile(path: String, bytes: ByteArray): Boolean = runCatching {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        true
    }.getOrDefault(false)

    actual fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    actual fun fileSize(path: String): Long = runCatching { File(path).length() }.getOrDefault(0L)

    actual fun fileExists(path: String): Boolean = runCatching { File(path).isFile }.getOrDefault(false)

    actual fun nowMs(): Long = System.currentTimeMillis()
}

actual fun ByteArray.toImageBitmap(): ImageBitmap? = runCatching {
    org.jetbrains.skia.Image.makeFromEncoded(this).toComposeImageBitmap()
}.getOrNull()

actual fun createAudioEngine(): AudioEngine = DesktopAudioEngine()
