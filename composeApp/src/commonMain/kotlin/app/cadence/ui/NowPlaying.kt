package app.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cadence.player.PlayerController
import app.cadence.player.PlayerUiState
import app.cadence.ui.components.Artwork
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.Bg0
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary
import app.cadence.util.asPlaybackTime

@Composable
fun NowPlayingOverlay(
    player: PlayerController,
    compact: Boolean,
    onClose: () -> Unit,
) {
    val state by player.state.collectAsState()
    val track = state.track

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1C1629), Color(0xFF0C0B12), Bg0),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = if (compact) 22.dp else 48.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Close now playing",
                        tint = TextSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }

            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Artwork(
                    track = track,
                    modifier = if (compact) {
                        Modifier.fillMaxWidth(0.84f).aspectRatio(1f)
                    } else {
                        Modifier.size(320.dp)
                    },
                    corner = 20.dp,
                )
                Spacer(Modifier.height(if (compact) 26.dp else 34.dp))
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = track?.displayArtist ?: "Pick something from your library",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!track?.album.isNullOrBlank()) {
                    Text(
                        text = track.displayAlbum,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.height(if (compact) 24.dp else 30.dp))

                Row(
                    Modifier.fillMaxWidth(if (compact) 1f else 0.62f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.positionMs.asPlaybackTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        modifier = Modifier.width(40.dp),
                    )
                    SeekSlider(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        onSeek = player::seekTo,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        state.durationMs.asPlaybackTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End,
                    )
                }

                Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

                Row(Modifier.fillMaxWidth(if (compact) 1f else 0.62f)) {
                    TransportControls(state, player, large = true)
                }

                if (!compact) {
                    Spacer(Modifier.height(14.dp))
                    VolumeControl(state.volume, player::setVolume)
                }

                if (compact && state.upcoming.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    UpNextRow(state)
                }
            }
        }
    }
}

@Composable
private fun UpNextRow(state: PlayerUiState) {
    val next = state.upcoming.firstOrNull() ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "UP NEXT",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Spacer(Modifier.width(12.dp))
        Artwork(next, 32.dp, 6.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                next.title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                next.displayArtist,
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
