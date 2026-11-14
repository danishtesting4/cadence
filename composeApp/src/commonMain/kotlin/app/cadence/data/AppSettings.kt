package app.cadence.data

import kotlinx.serialization.Serializable

@Serializable
data class SponsorBlockPreferences(
    val enabled: Boolean = true,
    val skipSponsor: Boolean = true,
    val skipSelfPromo: Boolean = true,
    val skipInteraction: Boolean = true,
    val skipIntro: Boolean = true,
    val skipOutro: Boolean = true,
    val skipPreview: Boolean = true,
    val skipFiller: Boolean = true,
    val skipMusicOfftopic: Boolean = true,
) {
    fun enabledCategories(): List<String> = buildList {
        if (!enabled) return@buildList
        if (skipSponsor) add("sponsor")
        if (skipSelfPromo) add("selfpromo")
        if (skipInteraction) add("interaction")
        if (skipIntro) add("intro")
        if (skipOutro) add("outro")
        if (skipPreview) add("preview")
        if (skipFiller) add("filler")
        if (skipMusicOfftopic) add("music_offtopic")
    }
}

@Serializable
data class YouTubeAuthState(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val accountName: String = "",
    val accountEmail: String = "",
) {
    val isSignedIn: Boolean get() = cookie.isNotBlank()
}

@Serializable
data class AppSettings(
    val sponsorBlock: SponsorBlockPreferences = SponsorBlockPreferences(),
    val youtubeAuth: YouTubeAuthState = YouTubeAuthState(),
)
