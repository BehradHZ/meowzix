package dev.behradhz.meowzix.feature.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.ui.components.TrackArtwork

@Composable
fun QueueInlineSearchRoute(
    query: String,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val normalized = TextNormalizer.normalize(query)
    val filtered = remember(state.items, normalized) {
        if (normalized == null) {
            state.items
        } else {
            state.items.filter { item ->
                val title = TextNormalizer.normalize(item.title).orEmpty()
                val artist = TextNormalizer.normalize(item.artist.orEmpty()).orEmpty()
                title.contains(normalized) || artist.contains(normalized)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "queue-search-header") {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Queue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = if (filtered.isEmpty()) "No matches" else "${filtered.size} of ${state.items.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                )
            }
        }

        if (filtered.isEmpty()) {
            item(key = "queue-search-empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No queued tracks match “${query.trim()}”",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    )
                }
            }
        } else {
            items(filtered, key = QueueItem::id) { item ->
                QueueSearchRow(
                    item = item,
                    isCurrent = item.id == state.items.getOrNull(state.currentIndex)?.id,
                    onPlay = {
                        val index = state.items.indexOfFirst { it.id == item.id }
                        if (index >= 0) viewModel.play(index)
                    },
                    onPlayNext = { viewModel.playNext(item.id) },
                    onAddToQueue = { viewModel.addToQueue(item.id) },
                    onRemove = {
                        val index = state.items.indexOfFirst { it.id == item.id }
                        if (index >= 0) viewModel.remove(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueSearchRow(
    item: QueueItem,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(
                artworkRef = item.artworkRef,
                description = item.title,
                size = 52.dp,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isCurrent) "Playing now · ${item.artist ?: "Unknown artist"}" else item.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                        onClick = { menuExpanded = false; onPlayNext() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = { menuExpanded = false; onAddToQueue() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete from queue") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onRemove() },
                    )
                }
            }
        }
    }
}
