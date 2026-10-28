package dev.behradhz.meowzix.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.AlbumSummary
import dev.behradhz.meowzix.data.repository.ArtistSummary
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val queries: LibraryQueryRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    val results = query
        .map(String::trim)
        .distinctUntilChanged()
        .debounce { value -> if (value.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { value ->
            flow {
                emit(if (value.isBlank()) emptyList() else queries.search(value))
            }.catch { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList<Track>(),
        )

    // Each entity category is queried independently: a matching playlist or artist must not
    // require a matching song in the bounded track-search result set.
    val artists = combine(query, queries.artists()) { value, all ->
        all.filter { matchesGlobalSearch(value, it.name) }.take(20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ArtistSummary>())

    val albums = combine(query, queries.albums()) { value, all ->
        all.filter { matchesGlobalSearch(value, it.name, it.artist) }.take(20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<AlbumSummary>())

    val playlists = combine(query, playlistRepository.observePlaylists()) { value, all ->
        all.filter { matchesGlobalSearch(value, it.title, it.description) }.take(20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<PlaylistSummary>())

    fun setQuery(value: String) {
        query.value = value
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 120L
    }
}
