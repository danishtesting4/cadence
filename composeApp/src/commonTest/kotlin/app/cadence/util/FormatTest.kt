package app.cadence.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormatTest {

    @Test
    fun `formats seconds and minutes`() {
        assertEquals("0:00", 0L.asPlaybackTime())
        assertEquals("0:07", 7_000L.asPlaybackTime())
        assertEquals("3:09", 189_000L.asPlaybackTime())
        assertEquals("59:59", 3_599_000L.asPlaybackTime())
    }

    @Test
    fun `formats hours`() {
        assertEquals("1:00:00", 3_600_000L.asPlaybackTime())
        assertEquals("2:05:03", 7_503_000L.asPlaybackTime())
    }

    @Test
    fun `clamps negative durations`() {
        assertEquals("0:00", (-5_000L).asPlaybackTime())
    }

    @Test
    fun `search is case insensitive`() {
        assertTrue("Midnight Drive".matchesQuery("midnight"))
        assertTrue("Midnight Drive".matchesQuery("DRIVE"))
        assertFalse("Midnight Drive".matchesQuery("morning"))
        assertTrue("Midnight Drive".matchesQuery("  "))
    }
}
