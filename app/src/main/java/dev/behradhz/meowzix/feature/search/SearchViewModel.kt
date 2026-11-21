package dev.behradhz.meowzix.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.AlbumSummary
import dev.behradhz.meowzix.data.repository.ArtistSummary
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val queries: LibraryQueryRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val settledQuery = query
        .map(String::trim)
        .distinctUntilChanged()
        .debounce { value -> if (value.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // Execute the categories in display order. Songs are emitted first; artist and
    // album queries begin only after the preceding database query has completed.
    private val categoryResults = settledQuery
        .flatMapLatest { value ->
            flow {
                emit(CategoryResults())
                if (value.isBlank()) return@flow

                val songs = searchOrEmpty { queries.search(value) }
                emit(CategoryResults(songs = songs))

                val artists = searchOrEmpty { queries.searchArtists(value) }
                emit(CategoryResults(songs = songs, artists = artists))

                val albums = searchOrEmpty { queries.searchAlbums(value) }
                emit(CategoryResults(songs = songs, artists = artists, albums = albums))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryResults())

    val results = categoryResults.map { it.songs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Track>())

    val artists = categoryResults.map { it.artists }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ArtistSummary>())

    val albums = categoryResults.map { it.albums }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<AlbumSummary>())

    val playlists = combine(settledQuery, playlistRepository.observePlaylists()) { value, all ->
        all.filter { matchesGlobalSearch(value, it.title, it.description) }.take(20)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<PlaylistSummary>())

    fun setQuery(value: String) {
        query.value = value
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 100L
    }
}

private data class CategoryResults(
    val songs: List<Track> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
)

private suspend fun <T> searchOrEmpty(query: suspend () -> List<T>): List<T> =
    try {
        query()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }
