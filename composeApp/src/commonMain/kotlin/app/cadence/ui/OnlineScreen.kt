package app.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cadence.data.LibraryRepository
import app.cadence.data.SettingsRepository
import app.cadence.data.SponsorBlockPreferences
import app.cadence.data.YouTubeAuthState
import app.cadence.download.DownloadState
import app.cadence.download.TrackDownloader
import app.cadence.model.Playlist
import app.cadence.model.Track
import app.cadence.online.OnlinePlaylistInfo
import app.cadence.online.YouTubeMusicService
import app.cadence.platform.Platform
import app.cadence.player.PlayerController
import app.cadence.ui.components.Artwork
import app.cadence.ui.components.SearchField
import app.cadence.ui.components.hoverBackground
import app.cadence.ui.icons.CadenceIcons
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary
import app.cadence.util.asPlaybackTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnlineScreen(
    online: YouTubeMusicService,
    player: PlayerController,
    playlists: List<Playlist>,
    library: LibraryRepository,
    compact: Boolean,
    settingsRepo: SettingsRepository,
    downloader: TrackDownloader,
) {
    val scope = rememberCoroutineScope()
    val playerState by player.state.collectAsState()
    val settings by settingsRepo.settings.collectAsState()
    val downloadStates by downloader.states.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showSb by remember { mutableStateOf(false) }

    // Sync state
    var syncing by remember { mutableStateOf<String?>(null) }
    var remotePlaylists by remember { mutableStateOf<List<OnlinePlaylistInfo>>(emptyList()) }
    var remotePlaylistsLoaded by remember { mutableStateOf(false) }

    fun runSearch(q: String) {
        val trimmed = q.trim()
        searchJob?.cancel()
        if (trimmed.length < 2) {
            results = emptyList(); searched = false; searching = false; error = null
            return
        }
        searching = true
        error = null
        searchJob = scope.launch {
            delay(350) // debounce
            val found = runCatching { online.searchSongs(trimmed) }
            searching = false
            searched = true
            found.onSuccess { results = it }
                .onFailure { error = it.message ?: "Search failed"; results = emptyList() }
        }
    }

    suspend fun syncLiked() {
        syncing = "liked"
        val songs = runCatching { online.getLikedSongs() }.getOrElse { emptyList() }
        if (songs.isNotEmpty()) {
            val ids = library.upsertOnlineTracks(songs)
            library.upsertSyncedPlaylist("Liked songs", "FEmusic_liked_videos", ids)
            library.notify("Synced ${songs.size} liked song${if (songs.size == 1) "" else "s"}")
        } else {
            library.notify("No liked songs found (check sign-in/cookies)")
        }
        syncing = null
    }

    suspend fun loadRemotePlaylists() {
        syncing = "playlists"
        val pls = runCatching { online.getSavedPlaylists() }.getOrElse { emptyList() }
        remotePlaylists = pls
        remotePlaylistsLoaded = true
        if (pls.isEmpty()) library.notify("No saved playlists found")
        syncing = null
    }

    suspend fun importPlaylist(info: OnlinePlaylistInfo) {
        syncing = info.browseId
        val songs = runCatching { online.getPlaylistTracks(info.browseId) }.getOrElse { emptyList() }
        if (songs.isNotEmpty()) {
            val ids = library.upsertOnlineTracks(songs)
            library.upsertSyncedPlaylist(info.name, info.browseId, ids, info.thumbnailUrl)
            library.notify("Imported \"${info.name}\" (${songs.size} tracks)")
        } else {
            library.notify("Couldn't load tracks for \"${info.name}\"")
        }
        syncing = null
    }

    // reset remote playlists view when signed out
    LaunchedEffect(settings.youtubeAuth.isSignedIn) {
        if (!settings.youtubeAuth.isSignedIn) {
            remotePlaylists = emptyList(); remotePlaylistsLoaded = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 26.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Spacer(Modifier.height(if (compact) 14.dp else 24.dp))
            Text("Online", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Search and stream from YouTube Music",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            AccountPanel(
                auth = settings.youtubeAuth,
                compact = compact,
                onSignIn = { cookie -> settingsRepo.setYouTubeAuth(settings.youtubeAuth.copy(cookie = cookie.trim())) },
                onSignOut = { settingsRepo.setYouTubeAuth(YouTubeAuthState()) },
            )
            Spacer(Modifier.height(10.dp))

            if (settings.youtubeAuth.isSignedIn) {
                SyncPanel(
                    syncing = syncing,
                    playlists = remotePlaylists,
                    playlistsLoaded = remotePlaylistsLoaded,
                    onSyncLiked = { scope.launch { syncLiked() } },
                    onLoadPlaylists = { scope.launch { loadRemotePlaylists() } },
                    onImportPlaylist = { info -> scope.launch { importPlaylist(info) } },
                )
                Spacer(Modifier.height(10.dp))
            }

            SearchField(
                value = query,
                onValueChange = { query = it; runSearch(it) },
                placeholder = "Search songs, artists, albums…",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            SponsorBlockPanel(
                prefs = settings.sponsorBlock,
                expanded = showSb,
                onToggleExpanded = { showSb = !showSb },
                onUpdate = { settingsRepo.updateSponsorBlock(it) },
            )
            Spacer(Modifier.height(8.dp))
        }

        when {
            searching -> item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Searching YouTube Music…", color = TextSecondary)
                    }
                }
            }
            error != null -> item {
                Text(error ?: "", color = TextSecondary, modifier = Modifier.padding(top = 24.dp))
            }
            searched && results.isEmpty() -> item {
                Text("No songs found for \"$query\".", color = TextSecondary, modifier = Modifier.padding(top = 24.dp))
            }
            results.isNotEmpty() -> {
                itemsIndexed(results, key = { _, t -> t.id }) { index, track ->
                    val dlState = downloadStates[track.videoId ?: track.id] ?: downloader.stateFor(track)
                    OnlineResultRow(
                        index = index,
                        track = track,
                        isCurrent = playerState.track?.id == track.id || playerState.track?.videoId == track.videoId,
                        isPlaying = playerState.isPlaying,
                        compact = compact,
                        downloadState = dlState,
                        onPlay = { player.playQueue(results, index) },
                        onPlayNext = { player.playNext(track) },
                        onAddToQueue = { player.addToQueue(track) },
                        onDownload = { scope.launch { downloader.download(track) } },
                    )
                }
            }
            else -> item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Type to find and stream songs. Sign in to sync your liked songs and playlists.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun SyncPanel(
    syncing: String?,
    playlists: List<OnlinePlaylistInfo>,
    playlistsLoaded: Boolean,
    onSyncLiked: () -> Unit,
    onLoadPlaylists: () -> Unit,
    onImportPlaylist: (OnlinePlaylistInfo) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Sync library", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onSyncLiked,
                enabled = syncing == null,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                if (syncing == "liked") {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.Black)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Sync liked songs", color = androidx.compose.ui.graphics.Color.Black)
            }
            TextButton(onClick = onLoadPlaylists, enabled = syncing == null) {
                if (syncing == "playlists") {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (playlistsLoaded) "Refresh playlists" else "Load playlists", color = Accent)
            }
        }
        if (playlistsLoaded) {
            if (playlists.isEmpty()) {
                Text("No saved playlists found.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                playlists.forEach { pl ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = pl.trackCount?.let { "$it tracks" } ?: "Playlist"
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        }
                        if (syncing == pl.browseId) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                        } else {
                            TextButton(onClick = { onImportPlaylist(pl) }, enabled = syncing == null) {
                                Text("Import", color = Accent)
                            }
                        }
                    }
                }
            }
        } else {
            Text("Load playlists to import them into your library.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun SponsorBlockPanel(
    prefs: SponsorBlockPreferences,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdate: ((SponsorBlockPreferences) -> SponsorBlockPreferences) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggleExpanded() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("SponsorBlock", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    if (prefs.enabled) "Skipping enabled categories" else "Disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Switch(
                checked = prefs.enabled,
                onCheckedChange = { on -> onUpdate { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = Accent),
            )
        }
        if (expanded && prefs.enabled) {
            Spacer(Modifier.height(6.dp))
            SbToggle("Sponsors", prefs.skipSponsor) { v -> onUpdate { it.copy(skipSponsor = v) } }
            SbToggle("Self-promotion", prefs.skipSelfPromo) { v -> onUpdate { it.copy(skipSelfPromo = v) } }
            SbToggle("Interaction reminders", prefs.skipInteraction) { v -> onUpdate { it.copy(skipInteraction = v) } }
            SbToggle("Intro", prefs.skipIntro) { v -> onUpdate { it.copy(skipIntro = v) } }
            SbToggle("Outro", prefs.skipOutro) { v -> onUpdate { it.copy(skipOutro = v) } }
            SbToggle("Preview/recap", prefs.skipPreview) { v -> onUpdate { it.copy(skipPreview = v) } }
            SbToggle("Filler/tangents", prefs.skipFiller) { v -> onUpdate { it.copy(skipFiller = v) } }
            SbToggle("Non-music segments", prefs.skipMusicOfftopic) { v -> onUpdate { it.copy(skipMusicOfftopic = v) } }
        } else if (prefs.enabled) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Tap row to choose categories · non-music & sponsors on by default",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun SbToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}

@Composable
private fun AccountPanel(
    auth: YouTubeAuthState,
    compact: Boolean,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var cookie by remember(auth.cookie) { mutableStateOf(auth.cookie) }
    var signingIn by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YouTube Music account", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    if (auth.isSignedIn) "Signed in · sync liked songs & playlists"
                    else "Sign in to sync liked songs and playlists",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (auth.isSignedIn) Accent else TextSecondary,
                )
            }
            if (auth.isSignedIn) {
                TextButton(onClick = onSignOut) { Text("Sign out", color = TextSecondary) }
            } else {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Close" else "Sign in", color = Accent)
                }
            }
        }
        if (!auth.isSignedIn && expanded) {
            Spacer(Modifier.height(8.dp))
            if (!Platform.isDesktop) {
                Button(
                    onClick = {
                        if (signingIn) return@Button
                        signingIn = true
                        scope.launch {
                            try {
                                val result = Platform.signInYouTube()
                                if (!result.isNullOrBlank()) onSignIn(result)
                            } finally {
                                signingIn = false
                            }
                        }
                    },
                    enabled = !signingIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    if (signingIn) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.Black)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Sign in with Google", color = androidx.compose.ui.graphics.Color.Black)
                }
                Spacer(Modifier.height(8.dp))
                Text("Or paste cookies manually:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
            } else {
                Text(
                    "Paste your music.youtube.com cookies to sign in (from browser devtools → Application/Storage → Cookies).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = cookie,
                onValueChange = { cookie = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("SAPISID=...; __Secure-1PSID=...; ...", color = TextTertiary) },
                singleLine = !compact,
                maxLines = if (compact) 3 else 5,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (cookie.isNotBlank()) onSignIn(cookie) },
                    enabled = cookie.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Save & sign in", color = androidx.compose.ui.graphics.Color.Black) }
            }
        }
    }
}

@Composable
private fun OnlineResultRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    compact: Boolean,
    downloadState: DownloadState,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
) {
    val bg = if (isCurrent) Accent.copy(alpha = 0.09f) else androidx.compose.ui.graphics.Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .hoverBackground(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (isCurrent && isPlaying) {
                Icon(CadenceIcons.GraphicEq, null, tint = Accent, modifier = Modifier.size(15.dp))
            } else {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) Accent else TextTertiary,
                )
            }
        }
        Artwork(track = track, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(track.displayArtist, track.displayAlbum.takeIf { it != "Unknown album" })
                    .filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0) {
            Text(
                track.durationMs.asPlaybackTime(),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
            )
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onPlayNext, modifier = Modifier.size(30.dp)) {
            Icon(CadenceIcons.PlaylistPlay, "Play next", tint = TextTertiary, modifier = Modifier.size(18.dp))
        }
        if (!compact) {
            IconButton(onClick = onAddToQueue, modifier = Modifier.size(30.dp)) {
                Icon(CadenceIcons.PlaylistAdd, "Add to queue", tint = TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
        when (downloadState) {
            DownloadState.DOWNLOADING -> CircularProgressIndicator(Modifier.size(16.dp).padding(horizontal = 4.dp), strokeWidth = 2.dp, color = Accent)
            DownloadState.DONE -> Icon(CadenceIcons.Check, "Downloaded", tint = Accent, modifier = Modifier.size(18.dp).padding(horizontal = 4.dp))
            DownloadState.FAILED, DownloadState.IDLE -> IconButton(onClick = onDownload, modifier = Modifier.size(30.dp)) {
                Icon(CadenceIcons.Download, "Save offline", tint = TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
