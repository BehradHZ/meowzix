package dev.behradhz.meowzix.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.feature.telegram.TelegramForwardSheet
import java.util.UUID

/** Forward the selected row, not necessarily the track currently playing. */
internal data class TrackForwardSelection(val id: UUID, val title: String, val artist: String?)
internal val LocalTrackForwardAction =
    staticCompositionLocalOf<(TrackForwardSelection) -> Unit> { {} }

internal enum class TrackActionsPage { ACTIONS, PLAYLISTS }

/** Compact popup anchored to the calling track's overflow button in Library and Queue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackActionsMenu(
    expanded: Boolean,
    title: String,
    artist: String?,
    favorite: Boolean,
    isQueue: Boolean,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onPrimary: () -> Unit,
    onAddToQueue: () -> Unit,
    onFavorite: () -> Unit,
    onForward: () -> Unit,
    onGoToArtist: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
    onContinueVibe: () -> Unit,
    onEditMetadata: () -> Unit,
    onManageDuplicates: () -> Unit,
    onWhy: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    var page by remember(title, artist, isQueue) { mutableStateOf(TrackActionsPage.ACTIONS) }
    val accent = MaterialTheme.colorScheme.primary
    val glass = MaterialTheme.colorScheme.surface
    val menuShape = RoundedCornerShape(18.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(280.dp),
        offset = DpOffset(x = (-8).dp, y = 0.dp),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .clip(menuShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            glass.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
                            glass.copy(alpha = 0.995f),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), glassShape)
                .testTag("glass-track-actions-menu"),
        ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "track-action-page",
        ) { activePage ->
            if (activePage == TrackActionsPage.PLAYLISTS) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page = TrackActionsPage.ACTIONS }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to track actions")
                        }
                        Text("Add to playlist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(title, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 44.dp, bottom = 8.dp))
                    if (playlists.isEmpty()) {
                        Text("No playlists yet", modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).testTag("track-playlist-picker"),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            items(playlists, key = { it.id }) { playlist ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(accent.copy(alpha = 0.075f), RoundedCornerShape(16.dp))
                                        .clickable { onDismiss(); onAddToPlaylist(playlist.id) }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = accent)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${playlist.trackCount} tracks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag(if (isQueue) "queue-track-actions" else "library-track-actions"),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist ?: "Unknown artist", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionIcon(
                            if (isQueue) Icons.Rounded.DeleteOutline else Icons.Rounded.PlaylistPlay,
                            if (isQueue) "Remove from queue" else "Play next",
                            danger = isQueue,
                        ) { onDismiss(); onPrimary() }
                        ActionIcon(Icons.Rounded.QueueMusic, "Add to queue") { onDismiss(); onAddToQueue() }
                        ActionIcon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            if (favorite) "Remove favorite" else "Favorite",
                            highlighted = favorite,
                        ) { onDismiss(); onFavorite() }
                        ActionIcon(Icons.Rounded.Send, "Forward") { onDismiss(); onForward() }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.085f), Color.White.copy(alpha = 0.035f)),
                                ),
                                RoundedCornerShape(13.dp),
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
                            .padding(horizontal = 8.dp),
                    ) {
                    ActionLine(Icons.Rounded.Person, "Go to artist", enabled = !artist.isNullOrBlank()) {
                        onDismiss(); onGoToArtist()
                    }
                    ActionLine(Icons.Rounded.PlaylistAdd, "Add to playlist", trailing = true) {
                        page = TrackActionsPage.PLAYLISTS
                    }
                    ActionLine(Icons.Rounded.AutoAwesome, "Continue the vibe") {
                        onDismiss(); onContinueVibe()
                    }
                    ActionLine(Icons.Rounded.Edit, "Edit metadata") {
                        onDismiss(); onEditMetadata()
                    }
                    ActionLine(Icons.Rounded.ContentCopy, "Manage duplicates") {
                        onDismiss(); onManageDuplicates()
                    }
                    }
                    // Keep playlist-only management operations while retaining Why as the last row.
                    onMoveUp?.let { action ->
                        ActionLine(Icons.Rounded.PlaylistPlay, "Move up in playlist") { onDismiss(); action() }
                    }
                    onMoveDown?.let { action ->
                        ActionLine(Icons.Rounded.PlaylistPlay, "Move down in playlist") { onDismiss(); action() }
                    }
                    onRemoveFromPlaylist?.let { action ->
                        ActionLine(Icons.Rounded.DeleteOutline, "Remove from playlist") { onDismiss(); action() }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(13.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
                            .padding(horizontal = 8.dp),
                    ) {
                        ActionLine(Icons.Rounded.HelpOutline, "Why this song") {
                            onDismiss(); onWhy()
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.size(44.dp).testTag("track-action-${label.lowercase().replace(' ', '-')}"),
        shape = CircleShape,
        color = color.copy(alpha = if (highlighted) 0.22f else 0.11f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (highlighted) color.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.17f),
        ),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActionLine(
    icon: ImageVector,
    title: String,
    enabled: Boolean = true,
    trailing: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .height(44.dp)
            .padding(horizontal = 8.dp)
            .testTag("track-action-row-${title.lowercase().replace(' ', '-')}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            fontWeight = FontWeight.Medium)
        if (trailing) Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}

/** Centralized overlay hosted by each screen, not once per LazyColumn item. */
@Composable
internal fun TrackForwardOverlay(
    selection: TrackForwardSelection?,
    viewModel: NowPlayingViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.forwardState.collectAsStateWithLifecycle()
    if (selection != null && state.isOpen) {
        TelegramForwardSheet(
            trackId = selection.id,
            title = selection.title,
            artist = selection.artist,
            query = state.query,
            chats = state.chats,
            isSearching = state.isSearching,
            isSending = state.isSending,
            errorMessage = state.errorMessage,
            defaults = state.defaults,
            onQueryChange = viewModel::searchForwardChats,
            onForward = viewModel::forwardCurrentTrack,
            onDismiss = {
                viewModel.dismissForwardPicker()
                onDismiss()
            },
        )
    }
}
