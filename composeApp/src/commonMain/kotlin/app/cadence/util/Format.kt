package app.cadence.util

/** Formats milliseconds as `m:ss` or `h:mm:ss`. */
fun Long.asPlaybackTime(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    if (minutes >= 60) {
        val hours = minutes / 60
        return "$hours:${(minutes % 60).toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun String.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return contains(q, ignoreCase = true)
}
