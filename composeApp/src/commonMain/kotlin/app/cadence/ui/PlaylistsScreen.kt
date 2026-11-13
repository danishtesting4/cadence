package app.cadence.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cadence.data.LibraryRepository
import app.cadence.model.Playlist
import app.cadence.model.Track
import app.cadence.player.PlayerController
import app.cadence.ui.components.EmptyPlaylists
import app.cadence.ui.components.PlaylistCard
import app.cadence.ui.components.PlaylistMosaic
import app.cadence.ui.components.PlaylistNameDialog
import app.cadence.ui.components.TrackListHeader
import app.cadence.ui.components.TrackRow
import app.cadence.ui.icons.CadenceIcons
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.Bg0
import app.cadence.ui.theme.Surface2
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary
import app.cadence.util.asPlaybackTime

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    allTracks: List<Track>,
    library: LibraryRepository,
    compact: Boolean,
    onOpen: (String) -> Unit,
) {
    var createOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = if (compact) 18.dp else 30.dp,
                end = if (compact) 14.dp else 30.dp,
                top = if (compact) 18.dp else 26.dp,
                bottom = 14.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Playlists",
                    style = if (compact) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    color = TextPrimary,
                )
                Text(
                    "${playlists.size} playlist${if (playlists.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Button(
                onClick = { createOpen = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg0),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(CadenceIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New playlist", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (playlists.isEmpty()) {
            EmptyPlaylists(onCreate = { createOpen = true })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compact) 150.dp else 186.dp),
                contentPadding = PaddingValues(
                    start = if (compact) 14.dp else 26.dp,
                    end = if (compact) 14.dp else 26.dp,
                    top = 6.dp,
                    bottom = 28.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        name = playlist.name,
                        tracks = tracksOf(playlist, allTracks),
                        onClick = { onOpen(playlist.id) },
                    )
                }
            }
        }
    }

    if (createOpen) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            initialValue = "",
            onDismiss = { createOpen = false },
            onConfirm = { name ->
                createOpen = false
                onOpen(library.createPlaylist(name))
            },
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allTracks: List<Track>,
    playlists: List<Playlist>,
    player: PlayerController,
    library: LibraryRepository,
    compact: Boolean,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val tracks = remember(playlist, allTracks) { tracksOf(playlist, allTracks) }
    val playerState by player.state.collectAsState()
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = if (compact) 10.dp else 22.dp,
                end = 16.dp,
                top = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(CadenceIcons.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Spacer(Modifier.width(4.dp))
            Text("PLAYLIST", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(CadenceIcons.MoreVert, contentDescription = "Playlist options", tint = TextSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(CadenceIcons.Edit, null, Modifier.size(18.dp)) },
                        onClick = {
                            menuOpen = false
                            renameOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete playlist", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(CadenceIcons.DeleteOutline, null, Modifier.size(18.dp)) },
                        onClick = {
                            menuOpen = false
                            deleteOpen = true
                        },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(
                start = if (compact) 20.dp else 30.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 18.dp,
            ),
            verticalAlignment = Alignment.Bottom,
        ) {
            PlaylistMosaic(
                tracks = tracks,
                modifier = Modifier.size(if (compact) 112.dp else 168.dp),
            )
            Spacer(Modifier.width(if (compact) 16.dp else 24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = if (compact) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${tracks.size} track${if (tracks.size == 1) "" else "s"} · " +
                        tracks.sumOf { it.durationMs }.asPlaybackTime(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (tracks.isNotEmpty()) player.playQueue(tracks, 0) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg0),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(CadenceIcons.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = { if (tracks.isNotEmpty()) player.playQueue(tracks.shuffled(), 0) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(CadenceIcons.Shuffle, null, Modifier.size(17.dp), tint = TextSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "This playlist is empty",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Open any track's menu and choose \"Add to playlist\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            }
        } else {
            if (!compact) TrackListHeader()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (compact) 6.dp else 20.dp,
                    end = if (compact) 6.dp else 20.dp,
                    bottom = 28.dp,
                ),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        index = index,
                        track = track,
                        isCurrent = playerState.track?.id == track.id,
                        isPlaying = playerState.isPlaying,
                        compact = compact,
                        playlists = playlists,
                        onPlay = { player.playQueue(tracks, index) },
                        onPlayNext = { player.playNext(track) },
                        onAddToQueue = { player.addToQueue(track) },
                        onAddToPlaylist = { library.addToPlaylist(it, track.id) },
                        onCreatePlaylistWith = { library.createPlaylist(it, track.id) },
                        onRemoveFromPlaylist = { library.removeFromPlaylist(playlist.id, track.id) },
                    )
                }
            }
        }
    }

    if (renameOpen) {
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initialValue = playlist.name,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                renameOpen = false
                library.renamePlaylist(playlist.id, name)
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = Surface2,
            title = { Text("Delete playlist?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "\"${playlist.name}\" will be removed. Your music files are not deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteOpen = false
                        library.deletePlaylist(playlist.id)
                        onDeleted()
                    },
                ) {
                    Text("Delete", color = Color(0xFFFF9A9A))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
}

private fun tracksOf(playlist: Playlist, allTracks: List<Track>): List<Track> {
    if (playlist.trackIds.isEmpty()) return emptyList()
    val byId = allTracks.associateBy { it.id }
    return playlist.trackIds.mapNotNull { byId[it] }
}
