package app.cadence.download

import app.cadence.data.LibraryRepository
import app.cadence.model.Track
import app.cadence.online.YouTubeMusicService
import app.cadence.platform.Platform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class DownloadState { IDLE, DOWNLOADING, DONE, FAILED }

data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long) {
    val fraction: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val isIndeterminate: Boolean get() = totalBytes <= 0
}

class TrackDownloader(
    private val http: HttpClient,
    private val online: YouTubeMusicService,
    private val library: LibraryRepository,
) {
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun close() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.cancel()
    }

    private fun key(track: Track): String = track.videoId ?: track.id

    fun stateFor(track: Track): DownloadState {
        val k = key(track)
        val live = _states.value[k]
        if (live == DownloadState.DOWNLOADING) return DownloadState.DOWNLOADING
        if (!track.localUri.isNullOrBlank() && Platform.fileExists(track.localUri)) return DownloadState.DONE
        return live ?: DownloadState.IDLE
    }

    fun progressFor(track: Track): DownloadProgress? {
        val k = key(track)
        if (stateFor(track) != DownloadState.DOWNLOADING) return null
        return _progress.value[k]
    }

    fun isDownloading(track: Track): Boolean = stateFor(track) == DownloadState.DOWNLOADING

    /** Total bytes stored in the app-managed downloads directory. */
    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
        val dir = File(Platform.downloadsDir())
        dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    /** Number of tracks currently stored for offline playback. */
    fun downloadedTracks(tracks: List<Track>): List<Track> = tracks.filter {
        !it.localUri.isNullOrBlank() && Platform.fileExists(it.localUri)
    }

    /** Remove all cached offline copies and reset their library entries. */
    suspend fun clearAll(tracks: List<Track>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        downloadedTracks(tracks).forEach { t ->
            if (library.removeDownloadAndReturnPath(t.id) != null) removed++
        }
        File(Platform.downloadsDir()).listFiles()?.forEach { runCatching { it.delete() } }
        _states.value = emptyMap()
        _progress.value = emptyMap()
        if (removed > 0) library.notify("Cleared $removed offline track${if (removed == 1) "" else "s"}")
        removed
    }

    fun cancel(track: Track) {
        val k = key(track)
        jobs.remove(k)?.cancel()
        _states.value = _states.value - k
        _progress.value = _progress.value - k
    }

    fun download(track: Track): Job {
        val k = key(track)
        val existing = jobs[k]
        if (existing != null && existing.isActive) return existing
        val job = scope.launch { downloadInternal(track) }
        jobs[k] = job
        return job
    }

    private suspend fun downloadInternal(track: Track) {
        val k = key(track)
        if (stateFor(track) == DownloadState.DOWNLOADING) return
        _states.value = _states.value + (k to DownloadState.DOWNLOADING)
        _progress.value = _progress.value + (k to DownloadProgress(0L, 0L))
        var tempFile: File? = null
        try {
            val ids = library.upsertOnlineTracks(listOf(track))
            val libId = ids.firstOrNull() ?: track.id

            val stream = online.resolveStream(track) ?: error("Could not resolve stream")
            val dir = File(Platform.downloadsDir())
            val ext = guessExt(stream.url, stream.mimeType.orEmpty())
            val safe = k.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val target = File(dir, "$safe$ext")
            tempFile = File(dir, "$safe$ext.part")

            val response = http.get(stream.url) {
                headers { stream.headers.forEach { (h, v) -> append(h, v) } }
            }
            if (!response.status.isSuccess()) error("HTTP ${response.status.value}")

            val total = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            var written = 0L
            tempFile.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    written += read
                    _progress.value = _progress.value + (k to DownloadProgress(written, total))
                }
            }

            val ok = Platform.writeFile(target.absolutePath, tempFile.readBytes())
            runCatching { tempFile.delete() }
            tempFile = null
            if (!ok) error("Failed to write file")

            val path = target.absolutePath
            library.markDownloaded(libId, path)
            if (track.videoId != null && track.videoId != libId) library.markDownloaded(track.videoId, path)

            _states.value = _states.value + (k to DownloadState.DONE)
            _progress.value = _progress.value - k
            library.notify("Saved \"${track.title}\" for offline")
        } catch (t: Throwable) {
            runCatching { tempFile?.delete() }
            _progress.value = _progress.value - k
            if (t is kotlinx.coroutines.CancellationException) {
                _states.value = _states.value - k
                throw t
            }
            _states.value = _states.value + (k to DownloadState.FAILED)
            library.notify(t.message ?: "Download failed")
        } finally {
            jobs.remove(k)
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
