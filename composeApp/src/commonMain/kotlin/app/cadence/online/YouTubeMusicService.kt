package app.cadence.online

import app.cadence.data.YouTubeAuthState
import app.cadence.model.Track
import app.cadence.model.TrackSource
import app.cadence.platform.Platform
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class OnlinePlaylistInfo(
    val id: String,
    val name: String,
    val browseId: String,
    val thumbnailUrl: String? = null,
    val trackCount: Int? = null,
)

/**
 * YouTube Music online source backed by innertubex.
 *
 * Provides search and stream resolution. Authentication/sync and offline caching are layered on
 * top of this service in later phases.
 */
class YouTubeMusicService {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = HttpClient(OkHttp)

    private val innerTube = InnerTube(
        httpClient,
        { _ -> },
        InnerTubeLogger.NONE,
    )

    private val extractor: InnerTubeExtractor by lazy {
        val configStore = RemotePlayerConfigStore(
            innerTube.httpClient,
            PlayerConfigRepository.disabled(),
        )
        val cipher = YouTubeCipherService(innerTube.httpClient, configStore)
        InnerTubeExtractor(
            YtConfigParserImpl(innerTube.httpClient, innerTube, configStore, cipherService = cipher),
            cipher,
            innerTube,
        )
    }

    private val extractMutex = Mutex()
    private val streamCache = mutableMapOf<String, CachedStream>()

    /** Search YouTube Music for songs matching [query]. */
    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.Default) {
        runCatching {
            val response = innerTube.search(
                client = YouTubeClient.Companion.WEB_REMIX,
                query = query,
                params = SONG_FILTER_PARAMS,
            ).bodyAsText()
            parseSongResults(response)
        }.getOrDefault(emptyList())
    }

    /** Resolve a playable stream for [track] (or [videoId]). */
    suspend fun resolveStream(track: Track): ResolvedStream? = resolveStream(track.videoId ?: return null)

    suspend fun resolveStream(videoId: String): ResolvedStream? = withContext(Dispatchers.Default) {
        extractMutex.withLock {
            val cached = streamCache[videoId]
            if (cached != null && cached.expiresAtMs > Platform.nowMs() + 60_000L) {
                return@withLock cached.resolved
            }
            runCatching {
                val stream = extractor.extract(
                    videoId,
                    ContentHints(
                        null, null, null,
                        false, null, null,
                        null, false,
                        null, false, null,
                    ),
                    emptySet<String>(),
                    AudioQuality.HIGH,
                    "",
                ) ?: return@runCatching null
                val expires = runCatching { stream.expiresAt?.toEpochMilliseconds() }.getOrNull()
                val resolved = ResolvedStream(
                    url = stream.audioUrl,
                    headers = stream.headers,
                    mimeType = stream.mimeType,
                    expiresAtMs = expires ?: (Platform.nowMs() + 5 * 60 * 60 * 1000L),
                    loudnessDb = stream.loudnessDb,
                )
                streamCache[videoId] = CachedStream(resolved, resolved.expiresAtMs)
                resolved
            }.getOrNull()
        }
    }

    fun applyAuth(cookie: String?, visitorData: String? = null, dataSyncId: String? = null) {
        innerTube.cookie = cookie?.takeIf { it.isNotBlank() }
        innerTube.visitorData = visitorData?.takeIf { it.isNotBlank() }
        innerTube.dataSyncId = dataSyncId?.takeIf { it.isNotBlank() }
        innerTube.useLoginForBrowse = !cookie.isNullOrBlank()
    }

    fun clearAuth() {
        innerTube.cookie = null
        innerTube.visitorData = null
        innerTube.dataSyncId = null
        innerTube.useLoginForBrowse = false
    }

    /** Fetch liked songs for the signed-in account (follows continuations). */
    suspend fun getLikedSongs(maxPages: Int = 15): List<Track> = withContext(Dispatchers.Default) {
        fetchAllSongs("FEmusic_liked_videos", maxPages)
    }

    /** Fetch saved/library playlists for the signed-in account (follows continuations). */
    suspend fun getSavedPlaylists(maxPages: Int = 15): List<OnlinePlaylistInfo> = withContext(Dispatchers.Default) {
        fetchAllPlaylists("FEmusic_liked_playlists", maxPages)
    }

    /** Fetch tracks inside an online playlist/album by its browseId (follows continuations). */
    suspend fun getPlaylistTracks(browseId: String, maxPages: Int = 20): List<Track> = withContext(Dispatchers.Default) {
        fetchAllSongs(browseId, maxPages)
    }

    private suspend fun fetchAllSongs(browseId: String, maxPages: Int): List<Track> {
        val all = linkedMapOf<String, Track>()
        var continuation: String? = null
        var pages = 0
        while (pages < maxPages) {
            val body = runCatching {
                if (continuation == null) {
                    innerTube.browse(YouTubeClient.WEB_REMIX, browseId, null, null, true).bodyAsText()
                } else {
                    innerTube.browse(YouTubeClient.WEB_REMIX, "", null, continuation, true).bodyAsText()
                }
            }.getOrNull() ?: break
            parseSongResults(body).forEach { t -> all.putIfAbsent(t.videoId ?: t.id, t) }
            continuation = extractContinuation(body)
            pages++
            if (continuation.isNullOrBlank()) break
        }
        return all.values.toList()
    }

    private suspend fun fetchAllPlaylists(browseId: String, maxPages: Int): List<OnlinePlaylistInfo> {
        val all = linkedMapOf<String, OnlinePlaylistInfo>()
        var continuation: String? = null
        var pages = 0
        while (pages < maxPages) {
            val body = runCatching {
                if (continuation == null) {
                    innerTube.browse(YouTubeClient.WEB_REMIX, browseId, null, null, true).bodyAsText()
                } else {
                    innerTube.browse(YouTubeClient.WEB_REMIX, "", null, continuation, true).bodyAsText()
                }
            }.getOrNull() ?: break
            parsePlaylistResults(body).forEach { p -> all.putIfAbsent(p.browseId, p) }
            continuation = extractContinuation(body)
            pages++
            if (continuation.isNullOrBlank()) break
        }
        return all.values.toList()
    }

    private fun extractContinuation(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val tokens = mutableListOf<String>()
        fun walk(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    el["nextContinuationData"]?.jsonObject?.str("continuation")?.let { if (it.isNotBlank()) tokens += it }
                    el["continuation"]?.let { v ->
                        if (v is kotlinx.serialization.json.JsonPrimitive && v.isString) {
                            v.contentOrNull?.let { if (it.isNotBlank()) tokens += it }
                        }
                    }
                    el.values.forEach(::walk)
                }
                is JsonArray -> el.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return tokens.firstOrNull { it.length > 10 }
    }

    fun close() {
        innerTube.close()
        httpClient.close()
        streamCache.clear()
    }

    private fun parseSongResults(raw: String): List<Track> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val results = mutableListOf<Track>()
        val seen = linkedSetOf<String>()

        fun walk(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    val mrlir = el["musicResponsiveListItemRenderer"]?.jsonObject
                    if (mrlir != null) {
                        trackFromItem(mrlir)?.let { if (seen.add(it.videoId ?: it.id)) results += it }
                    }
                    el.values.forEach(::walk)
                }
                is JsonArray -> el.forEach(::walk)
                else -> Unit
            }
        }

        // Prefer music shelves from tabbed search results to avoid duplicates from suggestions.
        val tabs = root.obj("contents")?.obj("tabbedSearchResultsRenderer")
            ?.arr("tabs") ?: run { walk(root); return results.distinctBy { it.videoId ?: it.id } }
        tabs.forEach { tab ->
            val section = tab.obj("tabRenderer")?.obj("content")?.obj("sectionListRenderer")
            section?.arr("contents")?.forEach { sectionContent ->
                val shelf = sectionContent.obj("musicShelfRenderer")
                shelf?.arr("contents")?.forEach { content ->
                    val item = content.obj("musicResponsiveListItemRenderer") ?: return@forEach
                    trackFromItem(item)?.let { if (seen.add(it.videoId ?: it.id)) results += it }
                }
            }
        }
        if (results.isEmpty()) walk(root)
        return results.distinctBy { it.videoId ?: it.id }
    }

    private fun trackFromItem(item: JsonObject): Track? {
        val overlayVideoId = item.obj("overlay")
            ?.obj("musicItemThumbnailOverlayRenderer")
            ?.obj("content")?.obj("musicPlayButtonRenderer")
            ?.obj("playNavigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
        val playlistDataId = item.obj("playlistItemData")?.str("videoId")
        val navVideoId = item.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
        val flexVideoId = item.arr("flexColumns").asSequence().mapNotNull { col ->
            col.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")?.arr("runs")
                ?.firstNotNullOfOrNull { run -> run.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId") }
        }.firstOrNull()
        val videoId = overlayVideoId ?: playlistDataId ?: navVideoId ?: flexVideoId ?: return null

        val flex = item.arr("flexColumns").mapNotNull { col ->
            col.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")
        }
        val title = flex.getOrNull(0)?.arr("runs")?.joinText()
            ?.takeIf { it.isNotBlank() } ?: return null
        val subtitleRuns = flex.getOrNull(1)?.arr("runs") ?: emptyList()
        val subtitleParts = subtitleRuns.mapNotNull { it.obj()?.str("text")?.trim() }
            .filter { it.isNotBlank() && it != "•" && it != "·" && it != "&" }
        // Typical: [Type, " • ", Artist, " • ", Album, " • ", Duration] or [Artist, " • ", Duration]
        val durationText = subtitleParts.lastOrNull { it.contains(':') && it.any(Char::isDigit) }
        val meta = subtitleParts.filter { it != durationText }
        val artist = meta.firstOrNull().orEmpty()
        val album = meta.drop(1).firstOrNull().orEmpty()

        val thumbs = item.obj("thumbnail")?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")?.arr("thumbnails")
        val thumbUrl = thumbs?.mapNotNull { it.obj()?.str("url") }?.lastOrNull()

        return Track(
            id = "yt:$videoId",
            uri = "https://music.youtube.com/watch?v=$videoId",
            title = title,
            artist = artist,
            album = album,
            durationMs = parseDurationMs(durationText),
            addedAt = 0L,
            source = TrackSource.ONLINE,
            videoId = videoId,
            artworkUrl = thumbUrl?.let(::normalizeThumbUrl),
        )
    }

    private fun parseDurationMs(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val parts = text.trim().split(':').mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> ((parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000)
            2 -> ((parts[0] * 60 + parts[1]) * 1000)
            1 -> (parts[0] * 1000)
            else -> 0L
        }
    }

    private fun normalizeThumbUrl(url: String): String =
        url.replace(Regex("=w\\d+-h\\d+(-[a-z0-9-]+)*"), "=w544-h544")

    private fun parsePlaylistResults(raw: String): List<OnlinePlaylistInfo> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val results = mutableListOf<OnlinePlaylistInfo>()
        val seen = linkedSetOf<String>()

        fun walk(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    // musicTwoRowItemRenderer (common for playlists in library grid)
                    el["musicTwoRowItemRenderer"]?.jsonObject?.let { r ->
                        playlistFromTwoRow(r)?.let { if (seen.add(it.browseId)) results += it }
                    }
                    // gridPlaylistRenderer
                    el["gridPlaylistRenderer"]?.jsonObject?.let { r ->
                        playlistFromGrid(r)?.let { if (seen.add(it.browseId)) results += it }
                    }
                    // musicResponsiveListItemRenderer can also represent playlists
                    el["musicResponsiveListItemRenderer"]?.jsonObject?.let { r ->
                        playlistFromResponsive(r)?.let { if (seen.add(it.browseId)) results += it }
                    }
                    el.values.forEach(::walk)
                }
                is JsonArray -> el.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return results
    }

    private fun playlistFromTwoRow(r: JsonObject): OnlinePlaylistInfo? {
        val title = r.obj("title")?.arr("runs")?.joinText()?.takeIf { it.isNotBlank() } ?: return null
        val browseId = r.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
            ?: r.obj("title")?.arr("runs")?.firstNotNullOfOrNull { it.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId") }
            ?: return null
        val thumbs = r.obj("thumbnailRenderer")?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")?.arr("thumbnails")
            ?: r.obj("thumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")?.arr("thumbnails")
        val thumb = thumbs?.mapNotNull { it.obj()?.str("url") }?.lastOrNull()?.let(::normalizeThumbUrl)
        val subtitle = r.obj("subtitle")?.arr("runs")?.joinText().orEmpty()
        val count = subtitle.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
        return OnlinePlaylistInfo(id = browseId, name = title, browseId = browseId, thumbnailUrl = thumb, trackCount = count)
    }

    private fun playlistFromGrid(r: JsonObject): OnlinePlaylistInfo? {
        val title = r.obj("title")?.arr("runs")?.joinText()?.takeIf { it.isNotBlank() } ?: return null
        val browseId = r.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId") ?: return null
        val thumbs = r.obj("thumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")?.arr("thumbnails")
        val thumb = thumbs?.mapNotNull { it.obj()?.str("url") }?.lastOrNull()?.let(::normalizeThumbUrl)
        return OnlinePlaylistInfo(id = browseId, name = title, browseId = browseId, thumbnailUrl = thumb)
    }

    private fun playlistFromResponsive(r: JsonObject): OnlinePlaylistInfo? {
        val flex = r.arr("flexColumns").mapNotNull { it.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text") }
        val title = flex.getOrNull(0)?.arr("runs")?.joinText()?.takeIf { it.isNotBlank() } ?: return null
        val nav = r.obj("navigationEndpoint")?.obj("browseEndpoint")
            ?: flex.getOrNull(0)?.arr("runs")?.firstNotNullOfOrNull { it.obj("navigationEndpoint")?.obj("browseEndpoint") }
        val browseId = nav?.str("browseId") ?: return null
        // Only treat as playlist/album if it's not a song (songs have watchEndpoint videoId)
        if (r.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId") != null) return null
        val thumbs = r.obj("thumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")?.arr("thumbnails")
        val thumb = thumbs?.mapNotNull { it.obj()?.str("url") }?.lastOrNull()?.let(::normalizeThumbUrl)
        return OnlinePlaylistInfo(id = browseId, name = title, browseId = browseId, thumbnailUrl = thumb)
    }

    companion object {
        /** YouTube Music "Songs" filter param. */
        const val SONG_FILTER_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
    }

    private data class CachedStream(val resolved: ResolvedStream, val expiresAtMs: Long)
}

data class ResolvedStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val expiresAtMs: Long = 0L,
    val loudnessDb: Double? = null,
)

private fun JsonElement.obj(): JsonObject? = this as? JsonObject
private fun JsonElement.obj(key: String): JsonObject? = obj()?.get(key)?.obj()
private fun JsonElement.arr(key: String): JsonArray = obj()?.get(key) as? JsonArray ?: JsonArray(emptyList())
private fun JsonElement.str(key: String): String? = obj()?.get(key)?.jsonPrimitive?.contentOrNull
private fun JsonArray.joinText(): String = mapNotNull { it.obj()?.str("text") }.joinToString("")
