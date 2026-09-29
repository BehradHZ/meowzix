package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
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
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryTracksViewModel @Inject constructor(
    private val pagedLibrary: PagedLibraryTracks,
    private val queryRepository: LibraryQueryRepository,
    private val settingsRepository: SettingsRepository,
    private val queueRepository: QueueRepository,
    private val playbackContextPolicyStore: PlaybackContextPolicyStore,
) : ViewModel() {
    private val availability = MutableStateFlow(LibraryAvailabilityFilter.ALL)

    private val sortMode: StateFlow<LibrarySortMode> = settingsRepository.libraryDisplaySettings
        .map { it.sortMode }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LibrarySortMode.RECENTLY_ADDED,
        )

    val tracks: Flow<PagingData<LibraryTrack>> = combine(sortMode, availability) { sort, filter ->
        sort to filter
    }
        .distinctUntilChanged()
        .flatMapLatest { (sort, filter) -> pagedLibrary.flow(sort, filter) }
        .cachedIn(viewModelScope)

    val trackCount: StateFlow<Int> = pagedLibrary.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val artists: StateFlow<List<ArtistSummary>> = queryRepository.artists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = queryRepository.albums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAvailability(filter: LibraryAvailabilityFilter) {
        availability.value = filter
    }

    /**
     * Materializes only UUIDs, and only after an explicit play action. The library screen itself
     * remains page-backed while queue semantics continue to cover the full selected sort/filter.
     */
    fun playTrack(trackId: UUID) {
        viewModelScope.launch {
            val filter = availability.value
            val sort = sortMode.value
            val ids = pagedLibrary.orderedTrackIds(sort, filter).distinct()
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
