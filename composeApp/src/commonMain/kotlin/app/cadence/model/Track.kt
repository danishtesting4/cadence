package app.cadence.model

import kotlinx.serialization.Serializable

/**
 * A single playable audio file.
 *
 * [uri] is a platform specific handle: an absolute file path on desktop and a
 * `content://` media URI on Android. The library itself is source agnostic so a
 * streaming backend can be added later by producing the same model.
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
) {
    val displayArtist: String get() = artist.ifBlank { "Unknown artist" }
    val displayAlbum: String get() = album.ifBlank { "Unknown album" }
}
