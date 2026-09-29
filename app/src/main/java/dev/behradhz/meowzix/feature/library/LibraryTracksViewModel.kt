package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
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

    val displaySettings: StateFlow<LibraryDisplaySettings> = settingsRepository.libraryDisplaySettings
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryDisplaySettings())

    val tracks: Flow<PagingData<LibraryTrack>> = combine(
        displaySettings,
        availability,
    ) { settings, filter -> settings to filter }
        .distinctUntilChanged()
        .flatMapLatest { (settings, filter) ->
            pagedLibrary.flow(settings.sortMode, settings.groupMode, filter)
        }
        .cachedIn(viewModelScope)

    val trackCount: StateFlow<Int> = pagedLibrary.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val artists: StateFlow<List<ArtistSummary>> = queryRepository.artists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = queryRepository.albums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedAggregate: StateFlow<LibraryAggregateSelection?> = aggregateSelection

    val aggregateTracks: StateFlow<List<Track>> = aggregateSelection
        .flatMapLatest { selected ->
            when (selected) {
                is LibraryAggregateSelection.Artist -> queryRepository.artistTracks(selected.normalizedName)
                is LibraryAggregateSelection.Album -> queryRepository.albumTracks(selected.name, selected.normalizedArtist)
                LibraryAggregateSelection.Favorites -> queryRepository.favoriteTracks()
                null -> flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAvailability(filter: LibraryAvailabilityFilter) {
        availability.value = filter
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
                display.sortMode,
                display.groupMode,
                filter,
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
