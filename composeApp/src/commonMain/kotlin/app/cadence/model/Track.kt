package app.cadence.model

import kotlinx.serialization.Serializable

/**
 * A playable track, either from the local library or an online source.
 *
 * For local tracks [uri] is a platform-specific handle (absolute file path on
 * desktop, `content://` media URI on Android). For online tracks [uri] holds a
 * resolved stream URL (when known) while [videoId] identifies the source item.
 */
@Serializable
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val addedAt: Long = 0L,
    /** Local file, online stream, or a downloaded copy of an online track. */
    val source: TrackSource = TrackSource.LOCAL,
    /** Source video/song id for online tracks (e.g. YouTube videoId). */
    val videoId: String? = null,
    /** Remote artwork/thumbnail URL for online tracks. */
    val artworkUrl: String? = null,
    /** Path/URI of a locally cached/downloaded copy, when available. */
    val localUri: String? = null,
) {
    val displayArtist: String get() = artist.ifBlank { "Unknown artist" }
    val displayAlbum: String get() = album.ifBlank { "Unknown album" }

    val isLocal: Boolean get() = source == TrackSource.LOCAL
    val isOnline: Boolean get() = source == TrackSource.ONLINE
    val isDownloaded: Boolean get() = source == TrackSource.DOWNLOADED || !localUri.isNullOrBlank()

    /** URI the player should prefer: local/cached copy when present, otherwise [uri]. */
    val playbackUri: String get() = localUri?.takeIf { it.isNotBlank() } ?: uri
}

@Serializable
enum class TrackSource { LOCAL, ONLINE, DOWNLOADED }
