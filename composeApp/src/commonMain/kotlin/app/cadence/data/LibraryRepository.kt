package app.cadence.data

import app.cadence.model.Playlist
import app.cadence.model.Track
import app.cadence.platform.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Owns the persisted music library and playlists. */
class LibraryRepository(private val scope: CoroutineScope) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val trackListSerializer = ListSerializer(Track.serializer())
    private val playlistListSerializer = ListSerializer(Playlist.serializer())

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        restore()
    }

    private fun restore() {
        Platform.readState(KEY_LIBRARY)?.let { raw ->
            runCatching { json.decodeFromString(trackListSerializer, raw) }
                .onSuccess { _tracks.value = it }
        }
        Platform.readState(KEY_PLAYLISTS)?.let { raw ->
            runCatching { json.decodeFromString(playlistListSerializer, raw) }
                .onSuccess { _playlists.value = it }
        }
    }

    fun importLibrary() {
        if (_scanning.value) return
        scope.launch {
            _scanning.value = true
            try {
                if (!Platform.ensureLibraryAccess()) {
                    _message.value = "Music access was denied. Enable it in system settings and try again."
                    return@launch
                }
                val found = Platform.importTracks()
                if (found.isEmpty()) {
                    _message.value = if (Platform.isDesktop) {
                        "No playable audio found in that folder."
                    } else {
                        "No music found on this device yet."
                    }
                    return@launch
                }
                val before = _tracks.value.associateBy { it.uri }
                val merged = (before.values + found)
                    .distinctBy { it.uri }
                    .sortedWith(compareBy({ it.title.lowercase() }, { it.artist.lowercase() }))
                _tracks.value = merged
                persist(KEY_LIBRARY, json.encodeToString(trackListSerializer, merged))
                val added = merged.size - before.size
                _message.value = when {
                    added > 0 -> "Added $added track${if (added == 1) "" else "s"}"
                    else -> "Library is already up to date"
                }
            } catch (t: Throwable) {
                _message.value = t.message ?: "Could not import music"
            } finally {
                _scanning.value = false
            }
        }
    }

    fun forgetTrack(track: Track) {
        _tracks.value = _tracks.value.filterNot { it.id == track.id }
        _playlists.value = _playlists.value.map { it.copy(trackIds = it.trackIds - track.id) }
        persist(KEY_LIBRARY, json.encodeToString(trackListSerializer, _tracks.value))
        persist(KEY_PLAYLISTS, json.encodeToString(playlistListSerializer, _playlists.value))
    }

    fun createPlaylist(name: String, initialTrackId: String? = null): String {
        val id = "pl_" + Platform.nowMs().toString(36) + "_" + (0..9999).random()
        val playlist = Playlist(
            id = id,
            name = name.trim().ifBlank { "New playlist" },
            trackIds = listOfNotNull(initialTrackId),
            createdAt = Platform.nowMs(),
        )
        _playlists.value = _playlists.value + playlist
        persist(KEY_PLAYLISTS, json.encodeToString(playlistListSerializer, _playlists.value))
        return id
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        updatePlaylist(playlistId) { playlist ->
            if (trackId in playlist.trackIds) playlist
            else playlist.copy(trackIds = playlist.trackIds + trackId)
        }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        updatePlaylist(playlistId) { it.copy(trackIds = it.trackIds - trackId) }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        updatePlaylist(playlistId) { it.copy(name = clean) }
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        persist(KEY_PLAYLISTS, json.encodeToString(playlistListSerializer, _playlists.value))
    }

    fun tracksOf(playlist: Playlist): List<Track> {
        val byId = _tracks.value.associateBy { it.id }
        return playlist.trackIds.mapNotNull { byId[it] }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun updatePlaylist(id: String, transform: (Playlist) -> Playlist) {
        _playlists.value = _playlists.value.map { if (it.id == id) transform(it) else it }
        persist(KEY_PLAYLISTS, json.encodeToString(playlistListSerializer, _playlists.value))
    }

    private fun persist(key: String, value: String) {
        scope.launch(Dispatchers.IO) { Platform.writeState(key, value) }
    }

    private companion object {
        const val KEY_LIBRARY = "library.v1"
        const val KEY_PLAYLISTS = "playlists.v1"
    }
}
