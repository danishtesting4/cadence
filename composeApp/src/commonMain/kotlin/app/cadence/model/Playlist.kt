package app.cadence.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    /** Local playlist or a synced online playlist. */
    val source: TrackSource = TrackSource.LOCAL,
    /** Remote browse/playlist id for synced online playlists. */
    val remoteBrowseId: String? = null,
    val thumbnailUrl: String? = null,
) {
    val isOnline: Boolean get() = source == TrackSource.ONLINE
}
