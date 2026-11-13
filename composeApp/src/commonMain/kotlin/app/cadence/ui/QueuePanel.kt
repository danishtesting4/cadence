package app.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cadence.model.Track
import app.cadence.player.PlayerController
import app.cadence.ui.components.Artwork
import app.cadence.ui.components.SectionLabel
import app.cadence.ui.icons.CadenceIcons
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.Bg1
import app.cadence.ui.theme.Hairline
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary

@Composable
fun QueuePanel(
    player: PlayerController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by player.state.collectAsState()

    Column(modifier.background(Bg1)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Up next", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(
                    "${state.upcoming.size} track${if (state.upcoming.size == 1) "" else "s"} in queue",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(CadenceIcons.Close, contentDescription = "Close queue", tint = TextSecondary)
            }
        }
        HorizontalDivider(color = Hairline)

        if (state.queue.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Play a track to start a queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                state.track?.let { track ->
                    item { SectionLabel("Now playing") }
                    item {
                        QueueRow(
                            track = track,
                            active = true,
                            trailing = null,
                            onClick = { player.jumpTo(state.index) },
                        )
                    }
                }

                if (state.upcoming.isNotEmpty()) {
                    item { SectionLabel("Next up") }
                    itemsIndexed(
                        state.upcoming,
                        key = { index, track -> "${track.id}-$index" },
                    ) { index, track ->
                        QueueRow(
                            track = track,
                            active = false,
                            trailing = "${index + 1}",
                            onClick = { player.jumpTo(state.index + 1 + index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    active: Boolean,
    trailing: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) Accent.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(track, 38.dp, 7.dp)
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) Accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                modifier = Modifier.width(18.dp),
            )
        }
    }
}
