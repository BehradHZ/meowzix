package dev.behradhz.meowzix.feature.queue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.components.TrackArtwork

@Composable
fun QueueRoute(
    onBack: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QueueScreen(
        state = state,
        onBack = onBack,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onRemove = viewModel::remove,
        onClear = viewModel::clear,
    )
}

@Composable
private fun QueueScreen(
    state: QueueState,
    onBack: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        QueueHeader(
            state = state,
            onBack = onBack,
            onClear = onClear,
        )

        if (state.items.isEmpty()) {
            EmptyQueue()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 10.dp,
                    end = 10.dp,
                    top = 4.dp,
                    bottom = 182.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = state.items,
                    key = { _, item -> item.id.toString() },
                ) { index, item ->
                    SwipeableQueueItem(
                        item = item,
                        isCurrent = index == state.currentIndex,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.items.lastIndex,
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onRemove = { onRemove(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueHeader(
    state: QueueState,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.items.size == 1) "1 song" else "${state.items.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    modifier = Modifier.padding(top = 1.dp),
                )
            }

            Surface(
                onClick = onClear,
                enabled = state.items.isNotEmpty(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(start = 58.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QueueStatusPill(
                text = state.playbackMode.name
                    .replace('_', ' ')
                    .lowercase()
                    .replaceFirstChar(Char::uppercase),
            )
            QueueStatusPill(
                text = "Repeat ${state.repeatMode.name.lowercase()}",
            )
        }
    }
}

@Composable
private fun QueueStatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SwipeableQueueItem(
    item: QueueItem,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                value == SwipeToDismissBoxValue.Settled
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                Color.Transparent
            },
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
                        text = if (isCurrent) {
                            "Playing now · ${item.artist ?: "Unknown artist"}"
                        } else {
                            item.artist ?: "Unknown artist"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (canMoveUp) 0.54f else 0.20f,
                        ),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (canMoveDown) 0.54f else 0.20f,
                        ),
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Remove from queue",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyQueue() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, bottom = 150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Text(
                    text = "Your queue is empty",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = "Use Play next or Add to queue from your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}
