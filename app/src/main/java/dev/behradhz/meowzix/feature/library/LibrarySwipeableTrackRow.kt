package dev.behradhz.meowzix.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistAddCircle
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.ui.components.DownloadableTrackArtwork
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import java.util.UUID
import kotlin.math.abs

@Composable
internal fun SwipeableLibraryTrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    availability: LibraryTrackAvailability,
    download: OfflineDownload?,
    playlists: List<PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
    onGoToArtist: () -> Unit,
    onEnsureArtwork: () -> Unit,
) {
    LaunchedEffect(track.id, track.artworkRef) {
        onEnsureArtwork()
    }

    val density = LocalDensity.current
    val haptics = rememberMeowzixHaptics()
    val actionThreshold = with(density) { 75.dp.toPx() }
    val maximumSwipe = with(density) { 300.dp.toPx() }
    var swipeOffsetX by remember(track.id) { mutableFloatStateOf(0f) }
    var swipeStage by remember(track.id) { mutableIntStateOf(0) }
    var horizontalDragActive by remember(track.id) { mutableStateOf(false) }
    val visualOffsetX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = if (horizontalDragActive) snap() else tween(190),
        label = "library-swipe-action",
    )
    val swipeMagnitude = abs(visualOffsetX)
    val swipingRight = visualOffsetX >= 0f

    Box(modifier = Modifier.fillMaxWidth()) {
        if (swipeMagnitude > 0.5f) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(66.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (swipingRight) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    horizontalArrangement = if (swipingRight) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (swipingRight) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Play next", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Add to queue", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = visualOffsetX }
                .pointerInput(track.id, actionThreshold) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDragActive = true
                            swipeStage = if (abs(swipeOffsetX) >= actionThreshold) 1 else 0
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val nextOffset = (swipeOffsetX + amount).coerceIn(-maximumSwipe, maximumSwipe)
                            val nextStage = if (abs(nextOffset) >= actionThreshold) 1 else 0
                            if (nextStage > swipeStage) {
                                haptics.perform(MeowzixHapticCue.Threshold)
                            }
                            swipeStage = nextStage
                            swipeOffsetX = nextOffset
                        },
                        onDragCancel = {
                            horizontalDragActive = false
                            swipeStage = 0
                            swipeOffsetX = 0f
                        },
                        onDragEnd = {
                            val releasedOffset = swipeOffsetX
                            horizontalDragActive = false
                            swipeStage = 0
                            swipeOffsetX = 0f
                            when {
                                releasedOffset >= actionThreshold -> onPlayNext()
                                releasedOffset <= -actionThreshold -> onAddToQueue()
                            }
                        },
                    )
                },
        ) {
            LibraryTrackRowContent(
                track = track,
                isCurrent = isCurrent,
                onClick = onClick,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPinOffline = onPinOffline,
                availability = availability,
                download = download,
                playlists = playlists,
                onFavorite = onFavorite,
                onAddToPlaylist = onAddToPlaylist,
                onGoToArtist = onGoToArtist,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTrackRowContent(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    availability: LibraryTrackAvailability,
    download: OfflineDownload?,
    playlists: List<PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
    onGoToArtist: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadableTrackArtwork(
                artworkRef = track.artworkRef,
                description = track.title,
                size = 52.dp,
                isOffline = availability == LibraryTrackAvailability.OFFLINE,
                download = download,
                onDownload = onPinOffline,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = "Currently playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.size(5.dp))
                    }
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${track.artist ?: "Unknown artist"} · ${libraryAvailabilityLabel(availability)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Text(
                formatLibraryDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Go to artist") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onGoToArtist()
                        },
                    )
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to playlist · ${playlist.title}") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAddCircle, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist(playlist.id)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (track.favorite) "Remove favorite" else "Favorite") },
                        leadingIcon = {
                            Icon(
                                if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onFavorite()
                        },
                    )
                }
            }
        }
    }
}

private fun libraryAvailabilityLabel(availability: LibraryTrackAvailability): String = when (availability) {
    LibraryTrackAvailability.OFFLINE -> "Offline"
    LibraryTrackAvailability.CLOUD -> "Cloud"
    LibraryTrackAvailability.UNAVAILABLE -> "Unavailable"
}

private fun formatLibraryDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
