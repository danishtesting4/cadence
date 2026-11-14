package app.cadence.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cadence.model.Track
import app.cadence.platform.Platform
import app.cadence.platform.toImageBitmap
import app.cadence.ui.icons.CadenceIcons
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.AccentDeep
import app.cadence.ui.theme.Bg0
import app.cadence.ui.theme.Hairline
import app.cadence.ui.theme.Surface2
import app.cadence.ui.theme.Surface3
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

object ArtworkCache {
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private val http by lazy { io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) }

    fun peek(id: String): ImageBitmap? = cache[id]

    suspend fun load(track: Track): ImageBitmap? {
        cache[track.id]?.let { return it }
        val bytes = Platform.loadArtwork(track) ?: loadRemote(track.artworkUrl)
        val bitmap = bytes?.toImageBitmap() ?: return null
        if (cache.size > 64) cache.remove(cache.keys.first())
        cache[track.id] = bitmap
        return bitmap
    }

    private suspend fun loadRemote(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            http.get(url).bodyAsBytes()
        }.getOrNull()
    }
}

private val placeholderGradients = listOf(
    listOf(Color(0xFF3A2E5C), Color(0xFF191524)),
    listOf(Color(0xFF1D3A38), Color(0xFF0F1B1A)),
    listOf(Color(0xFF3C2A2A), Color(0xFF1A1212)),
    listOf(Color(0xFF28304A), Color(0xFF121522)),
    listOf(Color(0xFF3B2A3C), Color(0xFF171018)),
)

@Composable
fun rememberArtwork(track: Track?): ImageBitmap? {
    val key = track?.id
    val state = produceState<ImageBitmap?>(initialValue = key?.let { ArtworkCache.peek(it) }, key) {
        if (track != null && value == null) value = ArtworkCache.load(track)
    }
    return state.value
}

@Composable
fun Artwork(track: Track?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val bitmap = rememberArtwork(track)
    val shape = RoundedCornerShape(corner)
    val gradient = placeholderGradients[
        ((track?.id?.hashCode() ?: 0) and 0x7fffffff) % placeholderGradients.size,
    ]

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .border(1.dp, Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = CadenceIcons.MusicNote,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxSize(0.34f),
            )
        }
    }
}

@Composable
fun Artwork(track: Track?, size: Dp, corner: Dp = 10.dp) {
    Artwork(track, Modifier.size(size), corner)
}

@Composable
fun PlayingBars(modifier: Modifier = Modifier, color: Color = Accent) {
    val transition = rememberInfiniteTransition(label = "playing-bars")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val height by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 420 + index * 140,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar-$index",
            )
            Box(
                Modifier
                    .width(2.5.dp)
                    .height((5 + 11 * height).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

@Composable
fun VinylArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF2A2A3E), Color(0xFF0B0B11)),
                center = center,
                radius = radius,
            ),
            radius = radius,
        )
        for (i in 1..7) {
            drawCircle(
                color = Color(0x14FFFFFF),
                radius = radius * (0.95f - i * 0.095f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Accent, AccentDeep),
                start = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
                end = Offset(center.x + radius * 0.2f, center.y + radius * 0.2f),
            ),
            radius = radius * 0.2f,
        )
        drawCircle(color = Color(0xFF07070B), radius = radius * 0.05f)
        drawLine(
            color = Color(0x22FFFFFF),
            start = Offset(center.x - radius * 0.75f, center.y - radius * 0.75f),
            end = Offset(center.x, center.y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun EmptyLibrary(scanning: Boolean, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        VinylArt(Modifier.size(170.dp))
        Spacer(Modifier.height(34.dp))
        Text(
            text = "Your music, your rules",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (Platform.isDesktop) {
                "Pick a folder and Cadence will read your tags, cover art and build a library that stays on your machine."
            } else {
                "Cadence reads the music already on your device. Nothing is uploaded, nothing is tracked."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(420.dp),
        )
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = onImport,
            enabled = !scanning,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Bg0,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 26.dp,
                vertical = 14.dp,
            ),
        ) {
            Text(
                text = if (scanning) "Scanning…" else "Add music",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Free and open source · GPL-3.0",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
        )
        Spacer(Modifier.weight(1.2f))
    }
}

@Composable
fun EmptyPlaylists(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Surface3, Surface2))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                CadenceIcons.MusicNote,
                contentDescription = null,
                tint = Accent.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Build your first playlist",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Group tracks for a mood, a drive, or a late night.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onCreate,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg0),
        ) {
            Text("New playlist")
        }
        Spacer(Modifier.weight(1.2f))
    }
}

@Composable
fun PlaylistMosaic(tracks: List<Track>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.clip(shape).background(Surface2).border(1.dp, Hairline, shape)) {
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    CadenceIcons.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize(0.3f),
                )
            }
        } else if (tracks.size < 4) {
            Artwork(tracks.first(), Modifier.fillMaxSize(), corner = 15.dp)
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Artwork(tracks[0], Modifier.weight(1f).fillMaxHeight(), corner = 0.dp)
                    Artwork(tracks[1], Modifier.weight(1f).fillMaxHeight(), corner = 0.dp)
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Artwork(tracks[2], Modifier.weight(1f).fillMaxHeight(), corner = 0.dp)
                    Artwork(tracks[3], Modifier.weight(1f).fillMaxHeight(), corner = 0.dp)
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    name: String,
    tracks: List<Track>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .hoverBackground(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        PlaylistMosaic(
            tracks = tracks,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            text = "${tracks.size} track${if (tracks.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )
    }
}
