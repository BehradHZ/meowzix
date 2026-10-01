package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.AlbumSummary
import dev.behradhz.meowzix.data.repository.ArtistSummary
import dev.behradhz.meowzix.data.repository.LibraryAvailabilityFilter
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.data.repository.PagedLibraryTracks
import dev.behradhz.meowzix.data.settings.PlaybackContextKeys
import dev.behradhz.meowzix.data.settings.PlaybackContextPolicyStore
import dev.behradhz.meowzix.domain.library.LibraryTrack
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.settings.LibraryDisplaySettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryAggregateSelection {
    data class Artist(val name: String, val normalizedName: String) : LibraryAggregateSelection
    data class Album(val name: String, val artist: String, val normalizedArtist: String) : LibraryAggregateSelection
    data object Favorites : LibraryAggregateSelection
}

@HiltViewModel
class LibraryTracksViewModel @Inject constructor(
    private val pagedLibrary: PagedLibraryTracks,
    private val queryRepository: LibraryQueryRepository,
    private val settingsRepository: SettingsRepository,
    private val queueRepository: QueueRepository,
    private val playbackContextPolicyStore: PlaybackContextPolicyStore,
) : ViewModel() {
    private val availability = MutableStateFlow(LibraryAvailabilityFilter.ALL)
    private val aggregateSelection = MutableStateFlow<LibraryAggregateSelection?>(null)
    private val searchQuery = MutableStateFlow("")

    val displaySettings: StateFlow<LibraryDisplaySettings> = settingsRepository.libraryDisplaySettings
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryDisplaySettings())

    val tracks: Flow<PagingData<LibraryTrack>> = combine(
        displaySettings,
        availability,
        searchQuery,
    ) { settings, filter, query -> Triple(settings, filter, query) }
        .distinctUntilChanged()
        .flatMapLatest { (settings, filter, query) ->
            pagedLibrary.flow(
                sortMode = settings.sortMode,
                groupMode = settings.groupMode,
                availability = filter,
                searchQuery = query,
            )
        }
        .cachedIn(viewModelScope)

    val trackCount: StateFlow<Int> = pagedLibrary.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val artists: StateFlow<List<ArtistSummary>> = combine(
        queryRepository.artists(),
        searchQuery,
    ) { rows, query ->
        if (query.isBlank()) rows else rows.filter { row -> matchesQuery(query, row.name) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = combine(
        queryRepository.albums(),
        searchQuery,
    ) { rows, query ->
        if (query.isBlank()) rows else rows.filter { row -> matchesQuery(query, row.name, row.artist) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedAggregate: StateFlow<LibraryAggregateSelection?> = aggregateSelection

    val aggregateTracks: StateFlow<List<Track>> = combine(
        aggregateSelection,
        searchQuery,
    ) { selected, query -> selected to query }
        .flatMapLatest { (selected, query) ->
            val source = when (selected) {
                is LibraryAggregateSelection.Artist -> queryRepository.artistTracks(selected.normalizedName)
                is LibraryAggregateSelection.Album -> queryRepository.albumTracks(selected.name, selected.normalizedArtist)
                LibraryAggregateSelection.Favorites -> queryRepository.favoriteTracks()
                null -> flowOf(emptyList())
            }
            if (query.isBlank()) source else source.map { tracks -> tracks.filter { matchesTrack(query, it) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAvailability(filter: LibraryAvailabilityFilter) {
        availability.value = filter
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = TextNormalizer.normalize(query).orEmpty()
    }

    fun openArtist(summary: ArtistSummary) {
        aggregateSelection.value = LibraryAggregateSelection.Artist(summary.name, summary.normalizedName)
    }

    fun openAlbum(summary: AlbumSummary) {
        aggregateSelection.value = LibraryAggregateSelection.Album(summary.name, summary.artist, summary.normalizedArtist)
    }

    fun openFavorites() {
        aggregateSelection.value = LibraryAggregateSelection.Favorites
    }

    fun closeAggregate() {
        aggregateSelection.value = null
    }

    /** Materialize only UUIDs, and only after an explicit play action. */
    fun playTrack(trackId: UUID) {
        viewModelScope.launch {
            val filter = availability.value
            val display = displaySettings.value
            val ids = pagedLibrary.orderedTrackIds(
                sortMode = display.sortMode,
                groupMode = display.groupMode,
                availability = filter,
                searchQuery = searchQuery.value,
            ).distinct()
            if (ids.isEmpty()) return@launch

            val contextKey = PlaybackContextKeys.LIBRARY
            playbackContextPolicyStore.activate(contextKey)
            val policy = playbackContextPolicyStore.policy(contextKey)
            val selectedIndex = ids.indexOf(trackId)
            val queue = when {
                selectedIndex < 0 -> listOf(trackId)
                policy.playbackMode == PlaybackMode.ORDERED -> ids.drop(selectedIndex)
                else -> ids
            }
            queueRepository.replaceAndPlay(queue, trackId, policy.playbackMode)
            if (policy.repeatMode != dev.behradhz.meowzix.domain.playback.RepeatMode.OFF) {
                queueRepository.setRepeatMode(policy.repeatMode)
            }
        }
    }
}

private fun matchesTrack(query: String, track: Track): Boolean = matchesQuery(
    query,
    track.title,
    track.artist.orEmpty(),
    track.album.orEmpty(),
)

private fun matchesQuery(query: String, vararg values: String): Boolean {
    val tokens = TextNormalizer.normalize(query)
        ?.split(' ')
        ?.filter(String::isNotBlank)
        .orEmpty()
    if (tokens.isEmpty()) return true
    val haystack = values.joinToString(" ") { TextNormalizer.normalize(it).orEmpty() }
    return tokens.all(haystack::contains)
}
