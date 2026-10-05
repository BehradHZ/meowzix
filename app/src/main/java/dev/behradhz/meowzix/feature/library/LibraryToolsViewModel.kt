package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.domain.library.DuplicateCandidate
import dev.behradhz.meowzix.domain.library.DuplicateEvidence
import dev.behradhz.meowzix.domain.library.LibraryToolsRepository
import dev.behradhz.meowzix.domain.library.TrackMergeJournal
import dev.behradhz.meowzix.domain.library.TrackMetadataOverride
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryToolsPanel { NONE, METADATA, DUPLICATES }

data class LibraryToolsUiState(
    val panel: LibraryToolsPanel = LibraryToolsPanel.NONE,
    val track: Track? = null,
    val metadataOverride: TrackMetadataOverride? = null,
    val duplicates: List<DuplicateCandidate> = emptyList(),
    val activeMerges: List<TrackMergeJournal> = emptyList(),
    val pendingMetadataMerge: DuplicateCandidate? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LibraryToolsViewModel @Inject constructor(
    private val tools: LibraryToolsRepository,
    private val queryRepository: LibraryQueryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryToolsUiState())
    val state: StateFlow<LibraryToolsUiState> = _state.asStateFlow()

    fun openMetadata(trackId: UUID) = load(trackId, LibraryToolsPanel.METADATA)
    fun openDuplicates(trackId: UUID) = load(trackId, LibraryToolsPanel.DUPLICATES)

    fun dismiss() {
        _state.update { LibraryToolsUiState() }
    }

    fun saveMetadata(title: String?, artist: String?, album: String?, artworkRef: String?) {
        val track = _state.value.track ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                tools.setMetadataOverride(
                    TrackMetadataOverride(
                        trackId = track.id,
                        title = title.cleanOverride(),
                        artist = artist.cleanOverride(),
                        album = album.cleanOverride(),
                        artworkRef = artworkRef.cleanOverride(4_096),
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess { dismiss() }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.message ?: "Unable to save metadata") } }
        }
    }

    fun resetMetadata() {
        val track = _state.value.track ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                tools.setMetadataOverride(
                    TrackMetadataOverride(trackId = track.id, updatedAtEpochMs = System.currentTimeMillis()),
                )
            }.onSuccess { dismiss() }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.message ?: "Unable to reset metadata") } }
        }
    }

    fun requestMerge(candidate: DuplicateCandidate) {
        if (candidate.evidence == DuplicateEvidence.METADATA_AND_DURATION) {
            _state.update { it.copy(pendingMetadataMerge = candidate) }
        } else {
            merge(candidate, confirmMetadataOnly = false)
        }
    }

    fun cancelMergeConfirmation() {
        _state.update { it.copy(pendingMetadataMerge = null) }
    }

    fun confirmMetadataMerge() {
        val candidate = _state.value.pendingMetadataMerge ?: return
        _state.update { it.copy(pendingMetadataMerge = null) }
        merge(candidate, confirmMetadataOnly = true)
    }

    fun undoMerge(journalId: UUID) {
        val trackId = _state.value.track?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { tools.unmerge(journalId) }
                .onSuccess { refreshDuplicates(trackId) }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.message ?: "Unable to undo merge") } }
        }
    }

    private fun merge(candidate: DuplicateCandidate, confirmMetadataOnly: Boolean) {
        val survivor = _state.value.track ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { tools.mergeTracks(survivor.id, candidate.track.id, confirmMetadataOnly) }
                .onSuccess { refreshDuplicates(survivor.id) }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.message ?: "Unable to merge tracks") } }
        }
    }

    private fun load(trackId: UUID, panel: LibraryToolsPanel) {
        viewModelScope.launch {
            _state.update { LibraryToolsUiState(panel = panel, busy = true) }
            runCatching {
                val track = requireNotNull(queryRepository.track(trackId)) { "Track is no longer available" }
                val override = tools.metadataOverride(trackId)
                val duplicates = if (panel == LibraryToolsPanel.DUPLICATES) tools.duplicateCandidates(trackId) else emptyList()
                val merges = if (panel == LibraryToolsPanel.DUPLICATES) tools.activeMerges() else emptyList()
                LibraryToolsUiState(panel, track, override, duplicates, merges, busy = false)
            }.onSuccess { _state.value = it }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.message ?: "Unable to load track tools") } }
        }
    }

    private suspend fun refreshDuplicates(trackId: UUID) {
        val track = queryRepository.track(trackId)
        _state.update {
            it.copy(
                track = track ?: it.track,
                duplicates = tools.duplicateCandidates(trackId),
                activeMerges = tools.activeMerges(),
                busy = false,
                pendingMetadataMerge = null,
            )
        }
    }
}

private fun String?.cleanOverride(max: Int = 512): String? = this?.trim()?.take(max)?.takeIf(String::isNotBlank)