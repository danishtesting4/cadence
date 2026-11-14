package app.cadence.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusicFilterTest {

    @Test
    fun `detects leading type tokens`() {
        assertEquals("Song", MusicFilter.typeTokenOf(listOf("Song", "Artist", "Album")))
        assertEquals("Album", MusicFilter.typeTokenOf(listOf("Album", "Artist")))
        assertEquals("Episode", MusicFilter.typeTokenOf(listOf("Episode", "Podcast")))
        assertEquals("", MusicFilter.typeTokenOf(listOf("Artist Name", "Album")))
    }

    @Test
    fun `rejects non song kinds`() {
        assertFalse(MusicFilter.isSongItem("Greatest Hits", "Album", hasVideoId = true))
        assertFalse(MusicFilter.isSongItem("Chill Mix", "Playlist", hasVideoId = true))
        assertFalse(MusicFilter.isSongItem("Some Talk", "Episode", hasVideoId = true))
        assertFalse(MusicFilter.isSongItem("No Id", "Song", hasVideoId = false))
    }

    @Test
    fun `keeps songs with and without type tokens`() {
        assertTrue(MusicFilter.isSongItem("Midnight Drive", "Song", hasVideoId = true))
        assertTrue(MusicFilter.isSongItem("Midnight Drive", "", hasVideoId = true))
        assertTrue(MusicFilter.isSongItem("Midnight Drive", null, hasVideoId = true))
    }

    @Test
    fun `filters artefact titles`() {
        assertFalse(MusicFilter.isPlausibleTitle(""))
        assertFalse(MusicFilter.isPlausibleTitle("YouTube Music"))
        assertTrue(MusicFilter.isPlausibleTitle("Midnight Drive"))
    }
}
