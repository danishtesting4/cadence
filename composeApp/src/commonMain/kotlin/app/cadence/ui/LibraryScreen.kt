package app.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cadence.data.LibraryRepository
import app.cadence.model.Playlist
import app.cadence.model.Track
import app.cadence.player.PlayerController
import app.cadence.ui.components.Artwork
import app.cadence.ui.components.EmptyLibrary
import app.cadence.ui.components.FilterChip
import app.cadence.ui.components.SearchField
import app.cadence.ui.components.TrackListHeader
import app.cadence.ui.components.TrackRow
import app.cadence.ui.components.hoverBackground
import app.cadence.ui.icons.CadenceIcons
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.Bg0
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary
import app.cadence.util.asPlaybackTime
import app.cadence.util.matchesQuery

data class AlbumGroup(val name: String, val artist: String, val tracks: List<Track>)
data class ArtistGroup(val name: String, val tracks: List<Track>)

@Composable
fun LibraryScreen(
    allTracks: List<Track>,
    playlists: List<Playlist>,
    player: PlayerController,
    library: LibraryRepository,
    app: AppState,
    compact: Boolean,
    scanning: Boolean,
    onImport: () -> Unit,
) {
    val playerState by player.state.collectAsState()

    val visible = remember(allTracks, app.search) {
        allTracks.filter {
            it.title.matchesQuery(app.search) ||
                it.artist.matchesQuery(app.search) ||
                it.album.matchesQuery(app.search)
        }
    }
    val songs = remember(visible, app.sort) { visible.sortedWith(app.sort.comparator) }
    val albums = remember(visible) {
        visible.groupBy { it.displayAlbum to it.displayArtist }
            .map { (key, list) -> AlbumGroup(key.first, key.second, list.sortedBy { it.trackNumber }) }
            .sortedBy { it.name.lowercase() }
    }
    val artists = remember(visible) {
        visible.groupBy { it.displayArtist }
            .map { (name, list) -> ArtistGroup(name, list) }
            .sortedBy { it.name.lowercase() }
    }

    Column(Modifier.fillMaxSize()) {
        if (allTracks.isEmpty()) {
            EmptyLibrary(scanning = scanning, onImport = onImport)
            return@Column
        }

        LibraryHeader(
            trackCount = allTracks.size,
            totalDurationMs = allTracks.sumOf { it.durationMs },
            app = app,
            compact = compact,
            scanning = scanning,
            onImport = onImport,
        )

        when {
            visible.isEmpty() -> NoMatches(app.search)
            app.libraryView == LibraryView.Songs -> {
                if (!compact) TrackListHeader()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (compact) 6.dp else 20.dp,
                        end = if (compact) 6.dp else 20.dp,
                        top = 4.dp,
                        bottom = 28.dp,
                    ),
                ) {
                    itemsIndexed(songs, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            index = index,
                            track = track,
                            isCurrent = playerState.track?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            compact = compact,
                            playlists = playlists,
                            onPlay = { player.playQueue(songs, index) },
                            onPlayNext = { player.playNext(track) },
                            onAddToQueue = { player.addToQueue(track) },
                            onAddToPlaylist = { library.addToPlaylist(it, track.id) },
                            onCreatePlaylistWith = { library.createPlaylist(it, track.id) },
                            onForget = { library.forgetTrack(track) },
                            onRemoveDownload = { library.removeDownload(track.id) },
                        )
                    }
                }
            }

            app.libraryView == LibraryView.Albums -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (compact) 148.dp else 176.dp),
                    contentPadding = PaddingValues(
                        start = if (compact) 14.dp else 26.dp,
                        end = if (compact) 14.dp else 26.dp,
                        top = 8.dp,
                        bottom = 28.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(albums, key = { it.name + "|" + it.artist }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = {
                                app.collection = CollectionRef(CollectionKind.Album, album.name, album.artist)
                            },
                        )
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (compact) 130.dp else 156.dp),
                    contentPadding = PaddingValues(
                        start = if (compact) 14.dp else 26.dp,
                        end = if (compact) 14.dp else 26.dp,
                        top = 8.dp,
                        bottom = 28.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(artists, key = { it.name }) { artistGroup ->
                        ArtistCard(
                            artist = artistGroup,
                            onClick = {
                                app.collection = CollectionRef(CollectionKind.Artist, "", artistGroup.name)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    trackCount: Int,
    totalDurationMs: Long,
    app: AppState,
    compact: Boolean,
    scanning: Boolean,
    onImport: () -> Unit,
) {
    val summary = buildString {
        append(trackCount)
        append(if (trackCount == 1) " track" else " tracks")
        val minutes = totalDurationMs / 60_000
        if (minutes > 0) {
            append(" · ")
            if (minutes >= 60) {
                append("${minutes / 60} hr ${minutes % 60} min")
            } else {
                append("$minutes min")
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (compact) 18.dp else 30.dp,
                end = if (compact) 14.dp else 30.dp,
                top = if (compact) 18.dp else 26.dp,
                bottom = 10.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Library",
                    style = if (compact) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }

            if (!compact) {
                SearchField(
                    value = app.search,
                    onValueChange = { app.search = it },
                    modifier = Modifier.width(250.dp),
                )
                Spacer(Modifier.width(10.dp))
                SortMenu(app.sort) { app.sort = it }
                Spacer(Modifier.width(6.dp))
                ImportButton(scanning, onImport)
            } else {
                SortMenu(app.sort) { app.sort = it }
                IconButton(onClick = onImport, enabled = !scanning) {
                    if (scanning) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Accent,
                        )
                    } else {
                        Icon(
                            CadenceIcons.FolderOpen,
                            contentDescription = "Add music",
                            tint = TextSecondary,
                        )
                    }
                }
            }
        }

        if (compact) {
            Spacer(Modifier.height(12.dp))
            SearchField(
                value = app.search,
                onValueChange = { app.search = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryView.entries.forEach { view ->
                FilterChip(
                    label = view.label,
                    selected = app.libraryView == view,
                    onClick = { app.libraryView = view },
                )
            }
        }
    }
}

@Composable
private fun ImportButton(scanning: Boolean, onImport: () -> Unit) {
    Button(
        onClick = onImport,
        enabled = !scanning,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg0),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (scanning) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Bg0)
        } else {
            Icon(CadenceIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add music", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SortMenu(current: TrackSort, onSelect: (TrackSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                CadenceIcons.Sort,
                contentDescription = "Sort library",
                tint = TextSecondary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TrackSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        if (option == current) {
                            Icon(CadenceIcons.Check, null, tint = Accent, modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun NoMatches(query: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            CadenceIcons.SearchOff,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (query.isBlank()) "Nothing here yet" else "No matches for \"$query\"",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.weight(1.2f))
    }
}

@Composable
private fun AlbumCard(album: AlbumGroup, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .hoverBackground(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Artwork(album.tracks.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
        Spacer(Modifier.height(9.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${album.artist} · ${album.tracks.size} track${if (album.tracks.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistCard(artist: ArtistGroup, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .hoverBackground(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            artist.tracks.firstOrNull(),
            Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape),
            1000.dp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${artist.tracks.size} track${if (artist.tracks.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )
    }
}

@Composable
fun CollectionDetailScreen(
    ref: CollectionRef,
    allTracks: List<Track>,
    playlists: List<Playlist>,
    player: PlayerController,
    library: LibraryRepository,
    compact: Boolean,
    onBack: () -> Unit,
) {
    val tracks = remember(allTracks, ref) {
        if (ref.kind == CollectionKind.Album) {
            allTracks
                .filter { it.displayAlbum == ref.album && it.displayArtist == ref.artist }
                .sortedBy { it.trackNumber }
        } else {
            allTracks.filter { it.displayArtist == ref.artist }.sortedBy { it.title.lowercase() }
        }
    }
    val playerState by player.state.collectAsState()
    val title = if (ref.kind == CollectionKind.Album) ref.album else ref.artist
    val subtitle = if (ref.kind == CollectionKind.Album) ref.artist else "${tracks.size} tracks"

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = if (compact) 10.dp else 22.dp,
                end = 18.dp,
                top = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(CadenceIcons.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (ref.kind == CollectionKind.Album) "ALBUM" else "ARTIST",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(
                start = if (compact) 20.dp else 30.dp,
                end = 24.dp,
                top = 10.dp,
                bottom = 18.dp,
            ),
            verticalAlignment = Alignment.Bottom,
        ) {
            Artwork(
                tracks.firstOrNull(),
                Modifier.size(if (compact) 112.dp else 168.dp),
                16.dp,
            )
            Spacer(Modifier.width(if (compact) 16.dp else 24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
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
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${tracks.size} track${if (tracks.size == 1) "" else "s"} · " +
                        tracks.sumOf { it.durationMs }.asPlaybackTime(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
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
                        onClick = {
                            if (tracks.isNotEmpty()) player.playQueue(tracks.shuffled(), 0)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0x33FFFFFF),
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(CadenceIcons.Shuffle, null, Modifier.size(17.dp), tint = TextSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    }
                }
            }
        }

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
                    onRemoveDownload = { library.removeDownload(track.id) },
                )
            }
        }
    }
}

@Composable
private fun CollectionPlayButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Accent,
            contentColor = Bg0,
        ),
    ) {
        Icon(CadenceIcons.PlayArrow, contentDescription = "Play")
    }
}
