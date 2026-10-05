package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.LibraryToolsRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import dev.behradhz.meowzix.domain.library.RuleMatchMode
import dev.behradhz.meowzix.domain.library.RulePlaylistDefinition
import dev.behradhz.meowzix.domain.library.RulePlaylistRecord
import dev.behradhz.meowzix.domain.library.RulePlaylistSort
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RulePlaylistsUiState(
    val records: List<RulePlaylistRecord> = emptyList(),
    val selectedPlaylistId: UUID? = null,
    val editing: RulePlaylistDefinition? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

data class SmartPlaylistStarter(
    val title: String,
    val rules: List<PlaylistRule>,
    val matchMode: RuleMatchMode,
    val sort: RulePlaylistSort,
)

@HiltViewModel
class RulePlaylistsViewModel @Inject constructor(
    private val tools: LibraryToolsRepository,
    private val playlists: PlaylistRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RulePlaylistsUiState())
    val state: StateFlow<RulePlaylistsUiState> = _state.asStateFlow()

    val selectedTracks: StateFlow<List<Track>> = _state
        .map { it.selectedPlaylistId }
        .distinctUntilChanged()
        .flatMapLatest { playlistId ->
            playlistId?.let(tools::observeRulePlaylistTracks) ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            tools.observeRulePlaylistRecords().collect { records ->
                _state.update { state ->
                    val selected = state.selectedPlaylistId?.takeIf { id -> records.any { it.definition.playlistId == id } }
                    state.copy(
                        records = records,
                        selectedPlaylistId = selected,
                        editing = state.editing?.takeIf { edit -> records.any { it.definition.playlistId == edit.playlistId } },
                    )
                }
            }
        }
    }

    fun select(record: RulePlaylistRecord) {
        _state.update { it.copy(selectedPlaylistId = record.definition.playlistId, editing = null, error = null) }
    }

    fun backToList() {
        _state.update { it.copy(selectedPlaylistId = null, editing = null, error = null) }
    }

    fun editSelected() {
        val id = _state.value.selectedPlaylistId ?: return
        val definition = _state.value.records.firstOrNull { it.definition.playlistId == id }?.definition ?: return
        _state.update { it.copy(editing = definition, error = null) }
    }

    fun cancelEdit() {
        _state.update { it.copy(editing = null, error = null) }
    }

    fun setMatchMode(mode: RuleMatchMode) {
        _state.update { state -> state.copy(editing = state.editing?.copy(matchMode = mode)) }
    }

    fun setSort(sort: RulePlaylistSort) {
        _state.update { state -> state.copy(editing = state.editing?.copy(sort = sort)) }
    }

    fun removeRule(index: Int) {
        _state.update { state ->
            val edit = state.editing ?: return@update state
            if (index !in edit.rules.indices || edit.rules.size <= 1) state
            else state.copy(editing = edit.copy(rules = edit.rules.toMutableList().also { it.removeAt(index) }))
        }
    }

    fun addRule(kind: RuleKind, value: String?) {
        _state.update { state ->
            val edit = state.editing ?: return@update state
            val normalizedValue = when (kind) {
                RuleKind.ADDED_WITHIN_DAYS, RuleKind.NOT_LISTENED_WITHIN_DAYS -> value?.toLongOrNull()?.coerceIn(1, 3650)?.toString() ?: return@update state.copy(error = "Enter a valid number of days")
                RuleKind.ARTIST_IS, RuleKind.ALBUM_IS -> value?.trim()?.takeIf(String::isNotBlank) ?: return@update state.copy(error = "Enter a value for this rule")
                RuleKind.FAVORITE, RuleKind.OFFLINE -> null
            }
            if (edit.rules.size >= 32) return@update state.copy(error = "A smart playlist can contain at most 32 rules")
            state.copy(editing = edit.copy(rules = edit.rules + PlaylistRule(kind, normalizedValue)), error = null)
        }
    }

    fun saveEdit() {
        val edit = _state.value.editing ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { tools.saveRulePlaylist(edit.copy(updatedAtEpochMs = System.currentTimeMillis())) }
                .onSuccess { _state.update { it.copy(editing = null, busy = false) } }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.message ?: "Unable to save smart playlist") } }
        }
    }

    fun createStarter(starter: SmartPlaylistStarter) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            var createdId: UUID? = null
            runCatching {
                createdId = playlists.create(starter.title)
                tools.saveRulePlaylist(
                    RulePlaylistDefinition(
                        playlistId = requireNotNull(createdId),
                        matchMode = starter.matchMode,
                        rules = starter.rules,
                        sort = starter.sort,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                requireNotNull(createdId)
            }.onSuccess { id ->
                _state.update { it.copy(selectedPlaylistId = id, busy = false) }
            }.onFailure { error ->
                createdId?.let { runCatching { playlists.delete(it) } }
                _state.update { it.copy(busy = false, error = error.message ?: "Unable to create smart playlist") }
            }
        }
    }

    fun deleteSelected() {
        val id = _state.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                tools.deleteRulePlaylist(id)
                playlists.delete(id)
            }.onSuccess { _state.update { it.copy(selectedPlaylistId = null, editing = null, busy = false) } }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.message ?: "Unable to delete smart playlist") } }
        }
    }

    suspend fun playbackSnapshot(playlistId: UUID): List<Track> = tools.evaluateRulePlaylist(playlistId)

    companion object {
        val STARTERS = listOf(
            SmartPlaylistStarter(
                title = "Offline Favorites",
                rules = listOf(PlaylistRule(RuleKind.FAVORITE), PlaylistRule(RuleKind.OFFLINE)),
                matchMode = RuleMatchMode.ALL,
                sort = RulePlaylistSort.TITLE,
            ),
            SmartPlaylistStarter(
                title = "Recently Added",
                rules = listOf(PlaylistRule(RuleKind.ADDED_WITHIN_DAYS, "30")),
                matchMode = RuleMatchMode.ALL,
                sort = RulePlaylistSort.RECENTLY_ADDED,
            ),
            SmartPlaylistStarter(
                title = "Haven’t Listened in a While",
                rules = listOf(PlaylistRule(RuleKind.NOT_LISTENED_WITHIN_DAYS, "30")),
                matchMode = RuleMatchMode.ALL,
                sort = RulePlaylistSort.LAST_PLAYED,
            ),
        )
    }
}