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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.cadence.model.Playlist
import app.cadence.model.Track
import app.cadence.online.YouTubeMusicService
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
) {
    val scope = rememberCoroutineScope()
    val playerState by player.state.collectAsState()
    val settings by settingsRepo.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showSb by remember { mutableStateOf(false) }

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
                    OnlineResultRow(
                        index = index,
                        track = track,
                        isCurrent = playerState.track?.id == track.id || playerState.track?.videoId == track.videoId,
                        isPlaying = playerState.isPlaying,
                        compact = compact,
                        onPlay = { player.playQueue(results, index) },
                        onPlayNext = { player.playNext(track) },
                        onAddToQueue = { player.addToQueue(track) },
                    )
                }
            }
            else -> item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Type to find and stream songs. SponsorBlock can skip sponsors and non-music segments.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary,
                )
            }
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
private fun OnlineResultRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    compact: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
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
    }
}
