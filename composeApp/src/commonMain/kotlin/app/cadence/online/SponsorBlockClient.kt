package app.cadence.online

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Minimal SponsorBlock client for fetching skip segments.
 * See https://wiki.sponsor.ajay.app/w/API_Docs
 */
class SponsorBlockClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://sponsor.ajay.app",
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns segments to skip for [videoId]. On failure returns an empty list so playback is never
     * blocked by the SponsorBlock service.
     */
    suspend fun getSkipSegments(
        videoId: String,
        categories: List<String> = DEFAULT_MUSIC_CATEGORIES,
    ): List<SkipSegment> = runCatching {
        val body = http.get("$baseUrl/api/skipSegments") {
            parameter("videoID", videoId)
            parameter("service", "YouTube")
            categories.forEach { parameter("category", it) }
        }.bodyAsText()
        if (body.isBlank() || body.startsWith("Not Found")) return@runCatching emptyList()
        json.decodeFromString<List<SkipSegment>>(body)
            .filter { it.segment.size == 2 }
            .sortedBy { it.segment[0] }
    }.getOrDefault(emptyList())

    companion object {
        /** Categories useful for a music player: drop sponsors and non-music portions. */
        val DEFAULT_MUSIC_CATEGORIES = listOf(
            "sponsor",
            "selfpromo",
            "interaction",
            "intro",
            "outro",
            "preview",
            "filler",
            "music_offtopic",
        )
    }
}

@Serializable
data class SkipSegment(
    val segment: List<Double>,
    @SerialName("UUID") val uuid: String = "",
    val category: String = "",
    val actionType: String = "skip",
    val locked: Int = 0,
    val votes: Int = 0,
    val description: String = "",
) {
    val startMs: Long get() = (segment.getOrElse(0) { 0.0 } * 1000).toLong().coerceAtLeast(0L)
    val endMs: Long get() = (segment.getOrElse(1) { 0.0 } * 1000).toLong().coerceAtLeast(startMs)
}
