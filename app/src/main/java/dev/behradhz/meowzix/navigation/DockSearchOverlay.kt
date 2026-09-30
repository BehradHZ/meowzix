package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.chrisbanes.haze.HazeState
import java.util.UUID

internal enum class DockSearchScope(val placeholder: String) {
    GLOBAL("Search anything"),
    LIBRARY("Search in library"),
    QUEUE("Search in queue"),
}

@Composable
internal fun DockSearchOverlay(
    hazeState: HazeState,
    scope: DockSearchScope,
    query: String,
    libraryResults: List<Track>,
    queueState: QueueState,
    currentTrackId: UUID?,
    onPlayLibraryTrack: (Track, List<Track>) -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = TextNormalizer.normalize(query)
    if (normalized == null) return

    val queueMatches = remember(queueState.items, normalized) {
        val tokens = normalized.split(' ').filter(String::isNotBlank)
        queueState.items.withIndex().filter { indexed ->
            val item = indexed.value
            val haystack = buildString {
                append(TextNormalizer.normalize(item.title).orEmpty())
                append(' ')
                append(TextNormalizer.normalize(item.artist).orEmpty())
            }
            tokens.all(haystack::contains)
        }
    }

    GlassSurface(
        hazeState = hazeState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 286.dp),
        shape = RoundedCornerShape(28.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tint = Color.White.copy(alpha = 0.12f),
    ) {
        when (scope) {
            DockSearchScope.QUEUE -> {
                if (queueMatches.isEmpty()) {
                    EmptySearchResult("No queued tracks match “${query.trim()}”")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(
                            items = queueMatches,
                            key = { _, indexed -> "queue-search-${indexed.index}-${indexed.value.id}" },
                        ) { _, indexed ->
                            QueueSearchResultRow(
                                item = indexed.value,
                                isCurrent = indexed.index == queueState.currentIndex,
                                onClick = { onPlayQueueIndex(indexed.index) },
                            )
                        }
                    }
                }
            }

            DockSearchScope.GLOBAL,
            DockSearchScope.LIBRARY,
            -> {
                if (libraryResults.isEmpty()) {
                    EmptySearchResult("No matches for “${query.trim()}”")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(libraryResults, key = { it.id.toString() }) { track ->
                            LibrarySearchResultRow(
                                track = track,
                                isCurrent = currentTrackId == track.id,
                                onClick = { onPlayLibraryTrack(track, libraryResults) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchResult(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibrarySearchResultRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(track.artworkRef, track.title, 46.dp)
        Spacer(Modifier.size(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(track.artist, track.album).joinToString(" · ").ifBlank { "Unknown artist" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QueueSearchResultRow(
    item: QueueItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(item.artworkRef, item.title, 46.dp)
        Spacer(Modifier.size(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist ?: "Unknown artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
