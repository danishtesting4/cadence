package app.cadence.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cadence.model.Playlist
import app.cadence.model.Track
import app.cadence.ui.icons.CadenceIcons
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.Surface2
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary
import app.cadence.ui.theme.TextTertiary
import app.cadence.util.asPlaybackTime

@Composable
fun TrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    compact: Boolean,
    playlists: List<Playlist>,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreatePlaylistWith: (String) -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val background = if (isCurrent) Accent.copy(alpha = 0.09f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .hoverBackground(Color.White.copy(alpha = 0.04f))
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (isCurrent && isPlaying) {
                PlayingBars()
            } else if (!compact) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }
        }

        Artwork(track, if (compact) 44.dp else 46.dp, 8.dp)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.displayArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!compact) {
            Text(
                text = track.displayAlbum,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(180.dp).padding(horizontal = 16.dp),
            )
        }

        Text(
            text = track.durationMs.asPlaybackTime(),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )

        TrackMenu(
            track = track,
            playlists = playlists,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onCreatePlaylistWith = onCreatePlaylistWith,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onForget = onForget,
        )
    }
}

@Composable
private fun TrackMenu(
    track: Track,
    playlists: List<Playlist>,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreatePlaylistWith: (String) -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onForget: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    var newPlaylistOpen by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                CadenceIcons.MoreVert,
                contentDescription = "More options for ${track.title}",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            DropdownMenuItem(
                text = { Text("Play next", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(CadenceIcons.PlaylistPlay, null, Modifier.size(18.dp)) },
                onClick = {
                    open = false
                    onPlayNext()
                },
            )
            DropdownMenuItem(
                text = { Text("Add to queue", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(CadenceIcons.PlaylistAdd, null, Modifier.size(18.dp)) },
                onClick = {
                    open = false
                    onAddToQueue()
                },
            )

            if (playlists.isNotEmpty()) {
                HorizontalDivider(color = Color(0x14FFFFFF))
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Add to ${playlist.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(CadenceIcons.PlaylistAdd, null, Modifier.size(18.dp)) },
                        onClick = {
                            open = false
                            onAddToPlaylist(playlist.id)
                        },
                    )
                }
            }

            DropdownMenuItem(
                text = { Text("New playlist…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(CadenceIcons.Add, null, Modifier.size(18.dp)) },
                onClick = {
                    open = false
                    newPlaylistOpen = true
                },
            )

            if (onRemoveFromPlaylist != null) {
                HorizontalDivider(color = Color(0x14FFFFFF))
                DropdownMenuItem(
                    text = { Text("Remove from playlist", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(CadenceIcons.PlaylistRemove, null, Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        onRemoveFromPlaylist()
                    },
                )
            }

            if (onForget != null) {
                HorizontalDivider(color = Color(0x14FFFFFF))
                DropdownMenuItem(
                    text = { Text("Remove from library", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(CadenceIcons.DeleteOutline, null, Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        onForget()
                    },
                )
            }
        }
    }

    if (newPlaylistOpen) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            initialValue = "",
            onDismiss = { newPlaylistOpen = false },
            onConfirm = { name ->
                newPlaylistOpen = false
                onCreatePlaylistWith(name)
            },
        )
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text(confirmLabel, color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}

@Composable
fun TrackListHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            "#",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.width(26.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "TITLE",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "ALBUM",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.width(212.dp),
        )
        Text(
            "TIME",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.width(56.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Spacer(Modifier.width(30.dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search your library",
) {
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = TextTertiary) },
        leadingIcon = {
            Icon(
                CadenceIcons.Search,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        CadenceIcons.Close,
                        contentDescription = "Clear search",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = Surface2,
            unfocusedContainerColor = Surface2,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = Accent.copy(alpha = 0.16f),
            selectedLabelColor = Accent,
            containerColor = Surface2,
            labelColor = TextSecondary,
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Suppress("unused")
private val unusedPadding = PaddingValues(0.dp)
