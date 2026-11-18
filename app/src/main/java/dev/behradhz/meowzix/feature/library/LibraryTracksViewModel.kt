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
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.library.LOCAL_PLAYLIST_ID
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.settings.LibraryDisplaySettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import dev.behradhz.meowzix.domain.playback.QueueGroupKind
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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
    private val queueFeedbackBus: dev.behradhz.meowzix.domain.playback.QueueActionFeedbackBus,
    private val playbackContextPolicyStore: PlaybackContextPolicyStore,
    private val musicLibrary: MusicLibraryRepository,
    private val playlists: PlaylistRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {
    private val availability = MutableStateFlow(LibraryAvailabilityFilter.ALL)
    private val aggregateSelection = MutableStateFlow<LibraryAggregateSelection?>(null)
    private val searchQuery = MutableStateFlow("")
    /** One canonical Track per ID. It survives browsing, filters and search within Library. */
    private val _selectedTracks = MutableStateFlow<Map<UUID, Track>>(emptyMap())
    val selectedTracks = _selectedTracks.asStateFlow()
    private val _selectionActive = MutableStateFlow(false)
    val selectionActive = _selectionActive.asStateFlow()
    private val _selectionAction = MutableStateFlow<LibrarySelectionAction?>(null)
    val selectionAction = _selectionAction.asStateFlow()
    private val _selectionError = MutableStateFlow<String?>(null)
    val selectionError = _selectionError.asStateFlow()
    val queueFeedback = queueFeedbackBus.events

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

    fun toggleTrackSelection(track: Track) {
        _selectedTracks.update { current ->
            toggleSelectedEntries(current, mapOf(track.id to track))
        }
        finishSelectionWhenEmpty()
    }

    /** A collection checkbox toggles the complete collection, not just the filtered screen. */
    private fun toggleCollectionSelection(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        _selectedTracks.update { current ->
            toggleSelectedEntries(current, tracks.associateBy(Track::id))
        }
        finishSelectionWhenEmpty()
    }

    private fun finishSelectionWhenEmpty() {
        if (_selectedTracks.value.isEmpty()) cancelSelection()
        else _selectionActive.value = true
    }

    fun toggleArtistSelection(artist: ArtistSummary) = viewModelScope.launch {
        try {
            toggleCollectionSelection(queryRepository.artistTracks(artist.normalizedName).first())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _selectionError.value = error.message ?: "Unable to select artist"
        }
    }

    fun toggleAlbumSelection(album: AlbumSummary) = viewModelScope.launch {
        try {
            toggleCollectionSelection(queryRepository.albumTracks(album.name, album.normalizedArtist).first())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _selectionError.value = error.message ?: "Unable to select album"
        }
    }

    fun toggleEntireOpenCollection() = viewModelScope.launch {
        val tracks = try {
            when (val collection = aggregateSelection.value) {
                is LibraryAggregateSelection.Artist -> queryRepository.artistTracks(collection.normalizedName).first()
                is LibraryAggregateSelection.Album -> queryRepository.albumTracks(collection.name, collection.normalizedArtist).first()
                LibraryAggregateSelection.Favorites -> queryRepository.favoriteTracks().first()
                null -> emptyList()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _selectionError.value = error.message ?: "Unable to select collection"
            return@launch
        }
        toggleCollectionSelection(tracks)
    }

    fun cancelSelection() {
        _selectedTracks.value = emptyMap()
        _selectionActive.value = false
        _selectionAction.value = null
    }

    fun requestSelectionAction(action: LibrarySelectionAction) {
        if (_selectionActive.value && _selectedTracks.value.isNotEmpty()) _selectionAction.value = action
    }

    fun consumeSelectionAction() { _selectionAction.value = null }
    fun consumeSelectionError() { _selectionError.value = null }

    /** All bulk operations use canonical IDs and never delete physical files or Telegram messages. */
    fun performSelectionAction(action: LibrarySelectionAction, playlistId: UUID? = null) {
        val ids = _selectedTracks.value.keys.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                when (action) {
                    LibrarySelectionAction.DELETE -> musicLibrary.removeTracksFromLibrary(ids)
                    LibrarySelectionAction.PLAY_NEXT -> ids.asReversed().forEach(queueRepository::playNext)
                    LibrarySelectionAction.ADD_TO_PLAYLIST -> {
                        val target = playlistId?.takeUnless { it == LOCAL_PLAYLIST_ID }
                            ?: error("Choose a playlist")
                        ids.forEach { playlists.addTrack(target, it) }
                    }
                    LibrarySelectionAction.ADD_TO_QUEUE -> ids.forEach(queueRepository::addToQueue)
                    LibrarySelectionAction.DOWNLOAD -> ids.forEach(downloads::pinOffline)
                    LibrarySelectionAction.LIKE -> ids.forEach { musicLibrary.setFavorite(it, true) }
                    LibrarySelectionAction.UNLIKE -> ids.forEach { musicLibrary.setFavorite(it, false) }
                    LibrarySelectionAction.PLAY_SELECTED ->
                        queueRepository.replaceAndPlay(ids, PlaybackMode.ORDERED)
                    LibrarySelectionAction.SHUFFLE ->
                        queueRepository.replaceAndPlay(ids, PlaybackMode.PURE_SHUFFLE)
                    LibrarySelectionAction.SHARE -> return@launch // Handled via Android Sharesheet.
                }
                cancelSelection()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _selectionError.value = error.message ?: "Unable to complete selection action"
            }
        }
    }

    fun setAvailability(filter: LibraryAvailabilityFilter) {
        availability.value = filter
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = TextNormalizer.normalize(query).orEmpty()
    }

    fun queueArtist(summary: ArtistSummary, next: Boolean) = viewModelScope.launch {
        val tracks = queryRepository.artistTracks(summary.normalizedName).first()
        val ids = tracks.map { it.id }.distinct()
        if (next) queueRepository.playNextGroup(ids, QueueGroupKind.ARTIST, summary.name)
        else queueRepository.addGroupToQueue(ids, QueueGroupKind.ARTIST, summary.name)
    }

    fun queueAlbum(summary: AlbumSummary, next: Boolean) = viewModelScope.launch {
        val tracks = queryRepository.albumTracks(summary.name, summary.normalizedArtist).first()
        val ids = tracks.map { it.id }.distinct()
        if (next) queueRepository.playNextGroup(ids, QueueGroupKind.ALBUM, summary.name)
        else queueRepository.addGroupToQueue(ids, QueueGroupKind.ALBUM, summary.name)
    }

    fun openArtist(summary: ArtistSummary) {
        aggregateSelection.value = LibraryAggregateSelection.Artist(summary.name, summary.normalizedName)
    }

    /** Open from a track row without querying the full artist catalog. */
    fun openArtistForTrack(track: Track) {
        aggregateSelection.value = LibraryAggregateSelection.Artist(
            track.artist?.takeIf(String::isNotBlank) ?: "Unknown artist",
            track.normalizedArtist ?: "unknown artist",
        )
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

    /** The player owns both immediate playback and the async 99-song tail. */
    fun playTrack(trackId: UUID) {
        val fromSearch = searchQuery.value.isNotBlank()
        val filter = availability.value
        val display = displaySettings.value
        val contextKey = PlaybackContextKeys.LIBRARY
        playbackContextPolicyStore.activate(contextKey)
        val policy = playbackContextPolicyStore.policy(contextKey)
        queueRepository.playLibrarySelection(
            trackId = trackId,
            mode = policy.playbackMode,
            repeat = policy.repeatMode,
            fromSearch = fromSearch,
            loadOrderedTrackIds = {
                pagedLibrary.orderedTrackIds(
                    sortMode = display.sortMode,
                    groupMode = display.groupMode,
                    availability = filter,
                    searchQuery = "",
                )
            },
        )
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
