package dev.behradhz.meowzix.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.QueueState

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
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onBack) { Text("Back") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Queue", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${state.playbackMode.name.replace('_', ' ')} · Repeat ${state.repeatMode.name}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(onClick = onClear, enabled = state.items.isNotEmpty()) { Text("Clear") }
            }
            if (state.items.isEmpty()) {
                Text("The queue is empty.", modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.items, key = { index, item -> "$index:${item.id}" }) { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (index == state.currentIndex) "▶ ${item.title}" else item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                                Text(item.artist ?: "Unknown artist", maxLines = 1)
                            }
                            TextButton(onClick = { onMoveUp(index) }, enabled = index > 0) { Text("Up") }
                            TextButton(
                                onClick = { onMoveDown(index) },
                                enabled = index < state.items.lastIndex,
                            ) { Text("Down") }
                            TextButton(onClick = { onRemove(index) }) { Text("Remove") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
