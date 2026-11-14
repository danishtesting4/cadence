package app.cadence.online

/**
 * Pure helpers used to keep online search results focused on music.
 *
 * YouTube Music search responses mix songs with albums, playlists, artists,
 * podcasts and episodes. Renderers are parsed generically, so this decides
 * whether a parsed item should be treated as a playable song.
 */
object MusicFilter {

    /** Renderer kinds that are never a playable song. */
    private val excludedTypes = setOf(
        "album",
        "playlist",
        "artist",
        "episode",
        "podcast",
        "profile",
        "channel",
        "station",
        "video",
        "single",
        "ep",
        "music video",
        "audio",
        "live",
        "remix",
        "book",
        "audiobook",
    )

    /** Leading subtitle tokens that denote an item kind rather than an artist. */
    private val knownTypeTokens = setOf(
        "song",
        "album",
        "artist",
        "playlist",
        "episode",
        "podcast",
        "video",
        "single",
        "ep",
        "music video",
        "live",
        "audio",
        "remix",
        "book",
        "audiobook",
        "profile",
        "station",
    )

    /**
     * Returns the leading "type" token of a subtitle (e.g. "Song" in "Song • Artist • Album"),
     * or an empty string when the subtitle does not start with a recognized kind.
     */
    fun typeTokenOf(tokens: List<String>): String {
        val first = tokens.firstOrNull()?.trim().orEmpty()
        if (first.isEmpty() || first.contains(':')) return ""
        val normalized = first.lowercase()
        return if (normalized in knownTypeTokens) first else ""
    }

    /**
     * @param title item title
     * @param subtitleType leading subtitle token, e.g. "Song" or "Album" (may be null)
     * @param hasVideoId whether the item exposes a watchable video id
     */
    fun isSongItem(title: String, subtitleType: String?, hasVideoId: Boolean): Boolean {
        if (!hasVideoId) return false
        if (title.isBlank()) return false
        val type = subtitleType?.trim()?.lowercase().orEmpty()
        if (type.isEmpty()) return true
        if (type in excludedTypes) return false
        // Unknown kinds are kept (songs often omit a type token entirely).
        return true
    }

    /** Drops results that look like UI artefacts rather than songs. */
    fun isPlausibleTitle(title: String): Boolean {
        if (title.isBlank()) return false
        val normalized = title.lowercase().trim()
        if (normalized.length < 2) return false
        if (normalized.startsWith("youtube music")) return false
        return true
    }
}