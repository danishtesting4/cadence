package app.cadence.data

import app.cadence.model.Playlist
import app.cadence.model.Track
import app.cadence.model.TrackSource
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
                val merged = mergeLocal(before.values.toList(), found)
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

    private fun mergeLocal(existing: List<Track>, found: List<Track>): List<Track> {
        val localByUri = existing.filter { it.isLocal }.associateBy { it.uri }.toMutableMap()
        found.forEach { localByUri[it.uri] = it }
        val nonLocal = existing.filter { !it.isLocal }
        return (localByUri.values + nonLocal)
            .distinctBy { dedupeKey(it) }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.artist.lowercase() }))
    }

    private fun dedupeKey(t: Track): String = when {
        t.isLocal -> "local:${t.uri}"
        !t.videoId.isNullOrBlank() -> "yt:${t.videoId}"
        else -> "uri:${t.uri}"
    }

    /** Upsert online tracks into the library, returning the resolved track ids in order. */
    fun upsertOnlineTracks(tracks: List<Track>): List<String> {
        if (tracks.isEmpty()) return emptyList()
        val byKey = _tracks.value.associateBy { dedupeKey(it) }.toMutableMap()
        val ids = tracks.map { incoming ->
            val key = dedupeKey(incoming)
            val existing = byKey[key]
            val resolved = if (existing != null) {
                existing.copy(
                    title = incoming.title.ifBlank { existing.title },
                    artist = incoming.artist.ifBlank { existing.artist },
                    album = incoming.album.ifBlank { existing.album },
                    durationMs = if (incoming.durationMs > 0) incoming.durationMs else existing.durationMs,
                    artworkUrl = incoming.artworkUrl ?: existing.artworkUrl,
                    localUri = existing.localUri ?: incoming.localUri,
                    source = if (existing.isDownloaded) TrackSource.DOWNLOADED else incoming.source,
                )
            } else incoming
            byKey[key] = resolved
            resolved.id
        }
        _tracks.value = byKey.values
            .distinctBy { dedupeKey(it) }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.artist.lowercase() }))
        persist(KEY_LIBRARY, json.encodeToString(trackListSerializer, _tracks.value))
        return ids
    }

    /** Create or update a synced online playlist by its remote browse id. */
    fun upsertSyncedPlaylist(name: String, remoteBrowseId: String, trackIds: List<String>, thumbnailUrl: String? = null): String {
        val existing = _playlists.value.firstOrNull { it.remoteBrowseId == remoteBrowseId }
        val id = existing?.id ?: ("ytpl_" + Platform.nowMs().toString(36) + "_" + (0..9999).random())
        val updated = Playlist(
            id = id,
            name = name.trim().ifBlank { existing?.name ?: "Playlist" },
            trackIds = trackIds,
            createdAt = existing?.createdAt ?: Platform.nowMs(),
            source = TrackSource.ONLINE,
            remoteBrowseId = remoteBrowseId,
            thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
        )
        _playlists.value = if (existing != null) {
            _playlists.value.map { if (it.id == existing.id) updated else it }
        } else {
            _playlists.value + updated
        }
        persist(KEY_PLAYLISTS, json.encodeToString(playlistListSerializer, _playlists.value))
        return id
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

    fun notify(message: String) { _message.value = message }

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
