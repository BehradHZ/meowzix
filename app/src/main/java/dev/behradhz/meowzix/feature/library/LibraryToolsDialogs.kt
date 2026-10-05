package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.library.DuplicateEvidence
import java.util.UUID

internal data class LibraryTrackToolsActions(
    val editMetadata: (UUID) -> Unit,
    val manageDuplicates: (UUID) -> Unit,
)

internal val LocalLibraryTrackToolsActions = staticCompositionLocalOf<LibraryTrackToolsActions?> { null }

@Composable
internal fun LibraryToolsDialogs(viewModel: LibraryToolsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingMerge = state.pendingMetadataMerge
    if (pendingMerge != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelMergeConfirmation,
            title = { Text("Merge possible duplicates?") },
            text = {
                Text(
                    "This match is based on title, artist, and duration rather than identical audio content. " +
                        "${pendingMerge.track.title} will be merged into the selected track. Audio files are not deleted.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmMetadataMerge) { Text("Merge") } },
            dismissButton = { TextButton(onClick = viewModel::cancelMergeConfirmation) { Text("Cancel") } },
        )
        return
    }

    when (state.panel) {
        LibraryToolsPanel.NONE -> Unit
        LibraryToolsPanel.METADATA -> MetadataOverrideDialog(state, viewModel)
        LibraryToolsPanel.DUPLICATES -> DuplicateManagementDialog(state, viewModel)
    }
}

@Composable
private fun MetadataOverrideDialog(state: LibraryToolsUiState, viewModel: LibraryToolsViewModel) {
    val track = state.track
    var title by remember(track?.id, state.metadataOverride) { mutableStateOf(state.metadataOverride?.title.orEmpty()) }
    var artist by remember(track?.id, state.metadataOverride) { mutableStateOf(state.metadataOverride?.artist.orEmpty()) }
    var album by remember(track?.id, state.metadataOverride) { mutableStateOf(state.metadataOverride?.album.orEmpty()) }
    var artwork by remember(track?.id, state.metadataOverride) { mutableStateOf(state.metadataOverride?.artworkRef.orEmpty()) }

    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text("Edit local metadata") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Overrides stay inside Meowzix. Source files and Telegram messages are not rewritten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.busy && track == null) CircularProgressIndicator()
                track?.let {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, placeholder = { Text(it.title) }, singleLine = true)
                    OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, placeholder = { Text(it.artist ?: "Unknown artist") }, singleLine = true)
                    OutlinedTextField(album, { album = it }, label = { Text("Album") }, placeholder = { Text(it.album ?: "No album") }, singleLine = true)
                    OutlinedTextField(artwork, { artwork = it }, label = { Text("Artwork reference") }, singleLine = true)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.busy && track != null,
                onClick = { viewModel.saveMetadata(title, artist, album, artwork) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (state.metadataOverride != null) TextButton(enabled = !state.busy, onClick = viewModel::resetMetadata) { Text("Reset") }
                TextButton(onClick = viewModel::dismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun DuplicateManagementDialog(state: LibraryToolsUiState, viewModel: LibraryToolsViewModel) {
    val track = state.track
    val relevantMerges = remember(state.activeMerges, track?.id) {
        val id = track?.id
        state.activeMerges.filter { id != null && (it.survivorTrackId == id || it.mergedTrackId == id) }
    }
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text("Duplicate management") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${track?.title ?: "Track"} · exact content matches can merge directly; metadata-only matches always require confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.busy && track == null) CircularProgressIndicator()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.duplicates, key = { it.track.id }) { candidate ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(candidate.track.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${candidate.track.artist ?: "Unknown artist"} · ${formatDuration(candidate.track.durationMs)} · ${formatEvidence(candidate.evidence)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (candidate.sourceLabels.isEmpty()) "Sources: unavailable" else "Sources: ${candidate.sourceLabels.joinToString()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(enabled = !state.busy, onClick = { viewModel.requestMerge(candidate) }) {
                                Text(if (candidate.evidence == DuplicateEvidence.EXACT_CONTENT) "Merge" else "Review merge")
                            }
                        }
                    }
                    items(relevantMerges, key = { "merge-${it.id}" }) { merge ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Merge from this version", style = MaterialTheme.typography.bodyMedium)
                            TextButton(enabled = !state.busy, onClick = { viewModel.undoMerge(merge.id) }) { Text("Undo") }
                        }
                    }
                }
                if (!state.busy && state.duplicates.isEmpty() && relevantMerges.isEmpty()) {
                    Text("No duplicate candidates found.", style = MaterialTheme.typography.bodyMedium)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("Done") } },
    )
}

private fun formatEvidence(evidence: DuplicateEvidence): String = when (evidence) {
    DuplicateEvidence.EXACT_CONTENT -> "identical audio content"
    DuplicateEvidence.METADATA_AND_DURATION -> "matching metadata + duration"
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}