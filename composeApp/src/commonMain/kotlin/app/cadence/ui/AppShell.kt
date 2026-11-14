package app.cadence.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cadence.data.LibraryRepository
import app.cadence.data.SettingsRepository
import app.cadence.download.TrackDownloader
import app.cadence.model.Playlist
import app.cadence.model.Track
import app.cadence.online.SponsorBlockClient
import app.cadence.online.YouTubeMusicService
import app.cadence.platform.createAudioEngine
import app.cadence.player.PlaybackSkipSegment
import app.cadence.player.PlayerController
import app.cadence.player.ResolvedPlayback
import app.cadence.ui.components.hoverBackground
import app.cadence.ui.icons.CadenceIcons
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.AccentDeep
import app.cadence.ui.theme.AppBackground
import app.cadence.ui.theme.Bg0
import app.cadence.ui.theme.Bg1
import app.cadence.ui.theme.CadenceTheme
import app.cadence.ui.theme.Hairline
import app.cadence.ui.theme.Surface2
import app.cadence.ui.theme.Surface3
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.delay

enum class Screen { Library, Playlists, Online }

enum class LibraryView(val label: String) {
    Songs("Songs"),
    Albums("Albums"),
    Artists("Artists"),
}

enum class CollectionKind { Album, Artist }

data class CollectionRef(
    val kind: CollectionKind,
    val album: String,
    val artist: String,
)

enum class TrackSort(val label: String) {
    Title("Title"),
    Artist("Artist"),
    Album("Album"),
    Recent("Recently added"),
    Duration("Duration");

    val comparator: Comparator<Track>
        get() = when (this) {
            Title -> compareBy({ it.title.lowercase() }, { it.artist.lowercase() })
            Artist -> compareBy(
                { it.displayArtist.lowercase() },
                { it.displayAlbum.lowercase() },
                { it.trackNumber },
            )
            Album -> compareBy({ it.displayAlbum.lowercase() }, { it.trackNumber })
            Recent -> compareByDescending { it.addedAt }
            Duration -> compareByDescending { it.durationMs }
        }
}

class AppState {
    var screen by mutableStateOf(Screen.Library)
    var openPlaylistId by mutableStateOf<String?>(null)
    var collection by mutableStateOf<CollectionRef?>(null)
    var nowPlaying by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var search by mutableStateOf("")
    var sort by mutableStateOf(TrackSort.Title)
    var libraryView by mutableStateOf(LibraryView.Songs)
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val engine = remember { createAudioEngine() }
    val library = remember { LibraryRepository(scope) }
    val settingsRepo = remember { SettingsRepository(scope) }
    val online = remember { YouTubeMusicService() }
    val sponsorHttp = remember { HttpClient(OkHttp) }
    val sponsorBlock = remember { SponsorBlockClient(sponsorHttp) }
    val player = remember {
        PlayerController(
            engine = engine,
            scope = scope,
            resolvePlayback = { track ->
                val stream = online.resolveStream(track) ?: return@PlayerController null
                ResolvedPlayback(
                    url = stream.url,
                    headers = stream.headers,
                    durationMs = 0L,
                )
            },
            fetchSkipSegments = { videoId ->
                val cats = settingsRepo.settings.value.sponsorBlock.enabledCategories()
                if (cats.isEmpty()) emptyList()
                else sponsorBlock.getSkipSegments(videoId, cats).map { PlaybackSkipSegment(it.startMs, it.endMs) }
            },
        )
    }
    val downloader = remember { TrackDownloader(sponsorHttp, online, library) }
    val app = remember { AppState() }

    val settings by settingsRepo.settings.collectAsState()
    LaunchedEffect(settings.youtubeAuth.cookie, settings.youtubeAuth.visitorData, settings.youtubeAuth.dataSyncId) {
        val auth = settings.youtubeAuth
        if (auth.isSignedIn) online.applyAuth(auth.cookie, auth.visitorData.ifBlank { null }, auth.dataSyncId.ifBlank { null })
        else online.clearAuth()
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.release()
            online.close()
            sponsorHttp.close()
        }
    }

    val tracks by library.tracks.collectAsState()
    val playlists by library.playlists.collectAsState()
    val scanning by library.scanning.collectAsState()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    CadenceTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(AppBackground)) {
            val compact = maxWidth < 780.dp

            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.Spacebar -> {
                                player.toggle()
                                true
                            }
                            Key.DirectionRight -> {
                                player.seekRelative(5_000)
                                true
                            }
                            Key.DirectionLeft -> {
                                player.seekRelative(-5_000)
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                if (compact) {
                    MobileShell(
                        app = app,
                        library = library,
                        player = player,
                        tracks = tracks,
                        playlists = playlists,
                        scanning = scanning,
                        online = online,
                        settingsRepo = settingsRepo,
                        downloader = downloader,
                    )
                } else {
                    DesktopShell(
                        app = app,
                        library = library,
                        player = player,
                        tracks = tracks,
                        playlists = playlists,
                        scanning = scanning,
                        online = online,
                        settingsRepo = settingsRepo,
                        downloader = downloader,
                    )
                }

                if (app.nowPlaying) {
                    NowPlayingOverlay(
                        player = player,
                        compact = compact,
                        onClose = { app.nowPlaying = false },
                    )
                }
            }

            MessageHost(library)
        }
    }
}

@Composable
private fun DesktopShell(
    app: AppState,
    library: LibraryRepository,
    player: PlayerController,
    tracks: List<Track>,
    playlists: List<Playlist>,
    scanning: Boolean,
    online: YouTubeMusicService,
    settingsRepo: SettingsRepository,
    downloader: TrackDownloader,
) {
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Sidebar(
            app = app,
            playlists = playlists,
            scanning = scanning,
            onImport = library::importLibrary,
        )
        VerticalRule()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AppContent(
                    app = app,
                    library = library,
                    player = player,
                    tracks = tracks,
                    playlists = playlists,
                    scanning = scanning,
                    compact = false,
                    online = online,
                    settingsRepo = settingsRepo,
                    downloader = downloader,
                )
            }
            DesktopPlayerBar(
                player = player,
                onExpand = { app.nowPlaying = true },
                queueOpen = app.queueOpen,
                onToggleQueue = { app.queueOpen = !app.queueOpen },
            )
        }
        if (app.queueOpen) {
            VerticalRule()
            QueuePanel(
                player = player,
                onClose = { app.queueOpen = false },
                modifier = Modifier.width(300.dp).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MobileShell(
    app: AppState,
    library: LibraryRepository,
    player: PlayerController,
    tracks: List<Track>,
    playlists: List<Playlist>,
    scanning: Boolean,
    online: YouTubeMusicService,
    settingsRepo: SettingsRepository,
    downloader: TrackDownloader,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AppContent(
                app = app,
                library = library,
                player = player,
                tracks = tracks,
                playlists = playlists,
                scanning = scanning,
                compact = true,
                online = online,
                settingsRepo = settingsRepo,
                downloader = downloader,
            )
        }
        MiniPlayer(player = player, onExpand = { app.nowPlaying = true })
        MobileNavBar(app)
    }
}

@Composable
private fun AppContent(
    app: AppState,
    library: LibraryRepository,
    player: PlayerController,
    tracks: List<Track>,
    playlists: List<Playlist>,
    scanning: Boolean,
    compact: Boolean,
    online: YouTubeMusicService,
    settingsRepo: SettingsRepository,
    downloader: TrackDownloader,
) {
    val playlist = app.openPlaylistId?.let { id -> playlists.firstOrNull { it.id == id } }

    when {
        app.screen == Screen.Playlists && playlist != null -> {
            PlaylistDetailScreen(
                playlist = playlist,
                allTracks = tracks,
                playlists = playlists,
                player = player,
                library = library,
                compact = compact,
                onBack = { app.openPlaylistId = null },
                onDeleted = { app.openPlaylistId = null },
            )
        }

        app.screen == Screen.Playlists -> {
            PlaylistsScreen(
                playlists = playlists,
                allTracks = tracks,
                library = library,
                compact = compact,
                onOpen = { app.openPlaylistId = it },
            )
        }

        app.collection != null -> {
            CollectionDetailScreen(
                ref = app.collection!!,
                allTracks = tracks,
                playlists = playlists,
                player = player,
                library = library,
                compact = compact,
                onBack = { app.collection = null },
            )
        }

        app.screen == Screen.Online -> {
            OnlineScreen(
                online = online,
                player = player,
                playlists = playlists,
                library = library,
                compact = compact,
                settingsRepo = settingsRepo,
                downloader = downloader,
            )
        }

        else -> {
            LibraryScreen(
                allTracks = tracks,
                playlists = playlists,
                player = player,
                library = library,
                app = app,
                compact = compact,
                scanning = scanning,
                onImport = library::importLibrary,
            )
        }
    }
}

@Composable
private fun Sidebar(
    app: AppState,
    playlists: List<Playlist>,
    scanning: Boolean,
    onImport: () -> Unit,
) {
    Column(
        Modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(Bg1)
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Row(
            Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Accent, AccentDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    CadenceIcons.GraphicEq,
                    contentDescription = null,
                    tint = Bg0,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Cadence",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                letterSpacing = (-0.3).sp,
            )
        }

        Spacer(Modifier.height(26.dp))

        SidebarItem(
            label = "Library",
            icon = CadenceIcons.LibraryMusic,
            selected = app.screen == Screen.Library,
        ) {
            app.screen = Screen.Library
            app.openPlaylistId = null
            app.collection = null
        }
        Spacer(Modifier.height(2.dp))
        SidebarItem(
            label = "Playlists",
            icon = CadenceIcons.QueueMusic,
            selected = app.screen == Screen.Playlists && app.openPlaylistId == null,
        ) {
            app.screen = Screen.Playlists
            app.openPlaylistId = null
            app.collection = null
        }
        Spacer(Modifier.height(2.dp))
        SidebarItem(
            label = "Online",
            icon = CadenceIcons.Search,
            selected = app.screen == Screen.Online,
        ) {
            app.screen = Screen.Online
            app.openPlaylistId = null
            app.collection = null
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "YOUR PLAYLISTS",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
        )

        LazyColumn(Modifier.weight(1f)) {
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "No playlists yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                SidebarItem(
                    label = playlist.name,
                    icon = CadenceIcons.PlaylistPlay,
                    selected = app.openPlaylistId == playlist.id,
                    small = true,
                ) {
                    app.screen = Screen.Playlists
                    app.openPlaylistId = playlist.id
                    app.collection = null
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onImport,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                Spacer(Modifier.width(8.dp))
                Text("Scanning…", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            } else {
                Icon(
                    CadenceIcons.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = TextSecondary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Add music",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "GPL-3.0 · v0.1.0",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Surface2 else Color.Transparent)
            .hoverBackground(Color.White.copy(alpha = 0.035f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (small) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Accent else TextSecondary,
            modifier = Modifier.size(if (small) 15.dp else 19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = if (small) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MobileNavBar(app: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Bg1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MobileNavItem(
            label = "Library",
            icon = CadenceIcons.LibraryMusic,
            selected = app.screen == Screen.Library,
            modifier = Modifier.weight(1f),
        ) {
            app.screen = Screen.Library
            app.openPlaylistId = null
            app.collection = null
        }
        MobileNavItem(
            label = "Playlists",
            icon = CadenceIcons.QueueMusic,
            selected = app.screen == Screen.Playlists,
            modifier = Modifier.weight(1f),
        ) {
            app.screen = Screen.Playlists
            app.openPlaylistId = null
            app.collection = null
        }
        MobileNavItem(
            label = "Online",
            icon = CadenceIcons.Search,
            selected = app.screen == Screen.Online,
            modifier = Modifier.weight(1f),
        ) {
            app.screen = Screen.Online
            app.openPlaylistId = null
            app.collection = null
        }
    }
}

@Composable
private fun MobileNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Accent else TextTertiary,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Accent else TextTertiary,
        )
    }
}

@Composable
private fun VerticalRule() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
}

@Composable
private fun MessageHost(library: LibraryRepository) {
    val message by library.message.collectAsState()

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_600)
            library.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Surface(
                color = Surface3,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 10.dp,
                modifier = Modifier.padding(bottom = 128.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }
    }
}
