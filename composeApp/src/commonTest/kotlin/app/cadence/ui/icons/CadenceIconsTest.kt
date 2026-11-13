package app.cadence.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the vendored icon set: every icon must build into a valid 24dp vector with at least one
 * path, otherwise the Material icon replacement would silently render nothing.
 */
class CadenceIconsTest {

    private val icons = listOf(
        CadenceIcons.Add,
        CadenceIcons.ArrowBack,
        CadenceIcons.Check,
        CadenceIcons.Close,
        CadenceIcons.DeleteOutline,
        CadenceIcons.Edit,
        CadenceIcons.FolderOpen,
        CadenceIcons.GraphicEq,
        CadenceIcons.KeyboardArrowDown,
        CadenceIcons.LibraryMusic,
        CadenceIcons.MoreVert,
        CadenceIcons.MusicNote,
        CadenceIcons.Pause,
        CadenceIcons.PlayArrow,
        CadenceIcons.PlaylistAdd,
        CadenceIcons.PlaylistPlay,
        CadenceIcons.PlaylistRemove,
        CadenceIcons.QueueMusic,
        CadenceIcons.Repeat,
        CadenceIcons.RepeatOne,
        CadenceIcons.Search,
        CadenceIcons.SearchOff,
        CadenceIcons.Shuffle,
        CadenceIcons.SkipNext,
        CadenceIcons.SkipPrevious,
        CadenceIcons.Sort,
        CadenceIcons.VolumeOff,
        CadenceIcons.VolumeUp,
    )

    @Test
    fun everyIconBuildsWithPaths() {
        assertEquals(28, icons.size, "expected 28 vendored icons")
        icons.forEach { icon ->
            assertEquals(24f, icon.defaultWidth.value, "${icon.name} width")
            assertEquals(24f, icon.defaultHeight.value, "${icon.name} height")
            assertTrue(icon.pathCount() > 0, "${icon.name} has no paths")
        }
    }

    @Test
    fun everyIconHasPathCommands() {
        icons.forEach { icon ->
            val total = icon.nodeCount()
            assertTrue(total >= 4, "${icon.name} path too short ($total nodes)")
        }
    }

    private fun ImageVector.pathCount(): Int = root.countPaths()

    private fun ImageVector.nodeCount(): Int = root.countNodes()

    private fun VectorGroup.countPaths(): Int =
        sumOf { node -> if (node is VectorPath) 1 else (node as? VectorGroup)?.countPaths() ?: 0 }

    private fun VectorGroup.countNodes(): Int =
        sumOf { node ->
            when (node) {
                is VectorPath -> node.pathData.size
                is VectorGroup -> node.countNodes()
                else -> 0
            }
        }
}
