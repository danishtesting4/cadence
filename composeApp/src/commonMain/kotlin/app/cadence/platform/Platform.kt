package app.cadence.platform

import app.cadence.model.Track
import app.cadence.player.AudioEngine
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The small surface each platform has to provide. Everything else (library,
 * queue, playlists, UI) is shared Compose code.
 */
expect object Platform {
    val isDesktop: Boolean

    /** Ask for whatever permission is needed to read the local music library. */
    suspend fun ensureLibraryAccess(): Boolean

    /** Let the user pick music (folder on desktop, device library on Android). */
    suspend fun importTracks(): List<Track>

    /** Embedded cover art bytes for [track], or null. */
    suspend fun loadArtwork(track: Track): ByteArray?

    fun readState(key: String): String?
    fun writeState(key: String, value: String)

    /** Directory for app-managed downloads/caches (created if needed). */
    fun downloadsDir(): String
    /** Write [bytes] to [path], creating parent dirs. Returns true on success. */
    fun writeFile(path: String, bytes: ByteArray): Boolean
    /** Delete a file if it exists. */
    fun deleteFile(path: String): Boolean
    /** File size in bytes, or 0 if missing. */
    fun fileSize(path: String): Long
    fun fileExists(path: String): Boolean

    fun nowMs(): Long
}

expect fun ByteArray.toImageBitmap(): ImageBitmap?

expect fun createAudioEngine(): AudioEngine
