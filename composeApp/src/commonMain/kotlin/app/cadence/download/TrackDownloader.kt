package app.cadence.download

import app.cadence.data.LibraryRepository
import app.cadence.model.Track
import app.cadence.online.YouTubeMusicService
import app.cadence.platform.Platform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class DownloadState { IDLE, DOWNLOADING, DONE, FAILED }

class TrackDownloader(
    private val http: HttpClient,
    private val online: YouTubeMusicService,
    private val library: LibraryRepository,
) {
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    fun stateFor(track: Track): DownloadState {
        if (!track.localUri.isNullOrBlank() && Platform.fileExists(track.localUri!!)) return DownloadState.DONE
        val key = track.videoId ?: track.id
        return _states.value[key] ?: DownloadState.IDLE
    }

    suspend fun download(track: Track): Boolean = withContext(Dispatchers.IO) {
        val key = track.videoId ?: track.id
        if (stateFor(track) == DownloadState.DOWNLOADING) return@withContext false
        _states.value = _states.value + (key to DownloadState.DOWNLOADING)
        try {
            // ensure track is in library (for search results)
            val ids = library.upsertOnlineTracks(listOf(track))
            val libId = ids.firstOrNull() ?: track.id

            val stream = online.resolveStream(track) ?: error("Could not resolve stream")
            val bytes = http.get(stream.url) {
                headers { stream.headers.forEach { (k, v) -> append(k, v) } }
            }.bodyAsBytes()
            val ext = guessExt(stream.url, stream.mimeType.orEmpty())
            val dir = Platform.downloadsDir()
            val safe = (track.videoId ?: track.id).replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val path = File(dir, "$safe$ext").absolutePath
            val ok = Platform.writeFile(path, bytes)
            if (!ok) error("Failed to write file")
            library.markDownloaded(libId, path)
            // also mark by videoId mapping if different
            if (track.videoId != null) library.markDownloaded(track.videoId, path)
            _states.value = _states.value + (key to DownloadState.DONE)
            library.notify("Saved \"${track.title}\" for offline")
            true
        } catch (t: Throwable) {
            _states.value = _states.value + (key to DownloadState.FAILED)
            library.notify(t.message ?: "Download failed")
            false
        }
    }

    private fun guessExt(url: String, mime: String): String = when {
        mime.contains("webm") || url.contains("mime=audio%2Fwebm") -> ".webm"
        mime.contains("mp4") || mime.contains("m4a") || url.contains("mime=audio%2Fmp4") -> ".m4a"
        mime.contains("ogg") -> ".ogg"
        mime.contains("opus") -> ".opus"
        else -> ".m4a"
    }
}
