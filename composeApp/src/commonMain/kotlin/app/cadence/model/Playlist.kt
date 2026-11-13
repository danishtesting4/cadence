package app.cadence.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
)
