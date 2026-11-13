package app.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cadence.player.PlayerController
import app.cadence.player.PlayerUiState
import app.cadence.player.RepeatMode
import app.cadence.ui.components.Artwork
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.Bg0
import app.cadence.ui.theme.Bg1
import app.cadence.ui.theme.Hairline
import app.cadence.ui.theme.Sky
import app.cadence.ui.theme.Surface2
import app.cadence.ui.theme.Surface3
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary
import app.cadence.util.asPlaybackTime

@Composable
fun DesktopPlayerBar(
    player: PlayerController,
    onExpand: () -> Unit,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
) {
    val state by player.state.collectAsState()

    Column(Modifier.fillMaxWidth().background(Bg1)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        state.error?.let { error ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x22FF6B6B))
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFB4B4))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = player::clearError) {
                    Text("Dismiss", color = Color(0xFFFFB4B4), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 960.dp
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .width(if (wide) 260.dp else 190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onExpand)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(state.track, 52.dp, 9.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.track?.title ?: "Nothing playing",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.track == null) TextSecondary else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.track?.displayArtist ?: "Add music to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(Modifier.width(if (wide) 404.dp else 332.dp)) {
                    TransportControls(state, player, large = false)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.positionMs.asPlaybackTime(),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                            modifier = Modifier.width(38.dp),
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
                }

                Spacer(Modifier.weight(1f))

                Row(
                    Modifier.width(if (wide) 210.dp else 46.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (wide) {
                        VolumeControl(state.volume, player::setVolume)
                    }
                    IconButton(onClick = onToggleQueue) {
                        Icon(
                            Icons.Rounded.QueueMusic,
                            contentDescription = "Up next",
                            tint = if (queueOpen) Accent else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransportControls(
    state: PlayerUiState,
    player: PlayerController,
    large: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = player::toggleShuffle) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffle) Accent else TextSecondary,
                modifier = Modifier.size(if (large) 24.dp else 19.dp),
            )
        }
        IconButton(onClick = player::previous) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = TextPrimary,
                modifier = Modifier.size(if (large) 34.dp else 26.dp),
            )
        }
        Box(
            Modifier
                .padding(horizontal = if (large) 12.dp else 6.dp)
                .size(if (large) 64.dp else 44.dp)
                .clip(CircleShape)
                .background(Accent)
                .clickable(onClick = player::toggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = Bg0,
                modifier = Modifier.size(if (large) 34.dp else 24.dp),
            )
        }
        IconButton(onClick = player::next) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = TextPrimary,
                modifier = Modifier.size(if (large) 34.dp else 26.dp),
            )
        }
        IconButton(onClick = player::cycleRepeat) {
            Icon(
                imageVector = if (state.repeat == RepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = "Repeat",
                tint = if (state.repeat == RepeatMode.OFF) TextSecondary else Accent,
                modifier = Modifier.size(if (large) 24.dp else 19.dp),
            )
        }
    }
}

@Composable
fun SeekSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
) {
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableStateOf(1f) }
    val progress = dragProgress
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(height)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(durationMs, widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var latest = (down.position.x / widthPx).coerceIn(0f, 1f)
                    dragProgress = latest
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            latest = (change.position.x / widthPx).coerceIn(0f, 1f)
                            dragProgress = latest
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                    if (durationMs > 0) onSeek((latest * durationMs).toLong())
                    dragProgress = null
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val centerY = size.height / 2f
            val trackHeight = 4.dp.toPx()
            val radius = CornerRadius(trackHeight / 2f)
            drawRoundRect(
                color = Color(0x33FFFFFF),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = radius,
            )
            if (progress > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Accent, Sky)),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width * progress, trackHeight),
                    cornerRadius = radius,
                )
            }
            drawCircle(
                color = Accent,
                radius = if (dragProgress != null) 7.dp.toPx() else 5.dp.toPx(),
                center = Offset(size.width * progress, centerY),
            )
        }
    }
}

@Composable
fun VolumeControl(volume: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val muted = volume <= 0.01f
        IconButton(
            onClick = { onChange(if (muted) 0.7f else 0f) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        SliderThin(
            value = volume,
            onChange = onChange,
            modifier = Modifier.width(78.dp),
        )
    }
}

@Composable
private fun SliderThin(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var widthPx by remember { mutableStateOf(1f) }
    Box(
        modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var latest = (down.position.x / widthPx).coerceIn(0f, 1f)
                    onChange(latest)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            latest = (change.position.x / widthPx).coerceIn(0f, 1f)
                            onChange(latest)
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 3.dp.toPx()
            drawRoundRect(
                color = Surface3,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawRoundRect(
                color = TextSecondary,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width * value, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawCircle(
                color = TextPrimary,
                radius = 4.dp.toPx(),
                center = Offset(size.width * value, centerY),
            )
        }
    }
}

@Composable
fun MiniPlayer(
    player: PlayerController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by player.state.collectAsState()
    val track = state.track ?: return

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Surface2)
                .clickable(onClick = onExpand),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track, 42.dp, 8.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = player::toggle, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = player::next, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp),
                color = Accent,
                trackColor = Surface3,
            )
        }
    }
}

@Composable
fun NowPlayingHeader(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.06f),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close now playing",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
