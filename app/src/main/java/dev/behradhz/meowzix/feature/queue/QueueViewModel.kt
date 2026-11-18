package dev.behradhz.meowzix.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.domain.library.RemoveTrackFromLocations
import dev.behradhz.meowzix.domain.library.QueueTrackStatus
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import dev.behradhz.meowzix.domain.playback.QueueRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueAuxState(
    val statuses: Map<UUID, QueueTrackStatus> = emptyMap(),
    val downloads: Map<UUID, OfflineDownload> = emptyMap(),
    val playlists: List<PlaylistSummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
    private val libraryRepository: MusicLibraryRepository,
    private val downloads: DownloadRepository,
    private val trackRemoval: RemoveTrackFromLocations,
    private val playlists: PlaylistRepository,
) : ViewModel() {
    val state = queueRepository.queueState
    private val _aux = MutableStateFlow(QueueAuxState())
    val aux: StateFlow<QueueAuxState> = _aux.asStateFlow()
    private val _removalErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val removalErrors = _removalErrors.asSharedFlow()

    init {
        viewModelScope.launch {
            state
                .map { queue -> queue.items.map { it.id }.distinct() }
                .distinctUntilChanged()
                .flatMapLatest(libraryRepository::observeQueueTrackStatuses)
                .collect { statuses ->
                    _aux.update { current -> current.copy(statuses = statuses) }
                }
        }
        viewModelScope.launch {
            downloads.observeDownloads().collect { rows ->
                _aux.update { it.copy(downloads = rows.associateBy(OfflineDownload::trackId)) }
            }
        }
        viewModelScope.launch {
            playlists.observePlaylists().collect { rows -> _aux.update { it.copy(playlists = rows) } }
        }
    }

    fun play(index: Int) = queueRepository.playAt(index)
    fun move(fromIndex: Int, toIndex: Int) = queueRepository.move(fromIndex, toIndex)
    fun move(fromIndex: Int, toIndex: Int, destinationOrigin: QueueItemOrigin) =
        queueRepository.move(fromIndex, toIndex, destinationOrigin)
    fun remove(index: Int) = queueRepository.removeAt(index)
    fun removeOccurrence(occurrenceId: UUID) = queueRepository.removeOccurrence(occurrenceId)
    fun clear() = queueRepository.clear()
    fun removeGroup(id: UUID) = queueRepository.removeGroup(id)
    fun moveGroup(id: UUID, toIndex: Int) = queueRepository.moveGroup(id, toIndex)
    fun toggleShuffle() {
        val nextMode = if (state.value.playbackMode == PlaybackMode.PURE_SHUFFLE) {
            PlaybackMode.ORDERED
        } else {
            PlaybackMode.PURE_SHUFFLE
        }
        queueRepository.setPlaybackMode(nextMode)
    }
    fun playNext(trackId: UUID) = queueRepository.playNext(trackId)
    fun addToQueue(trackId: UUID) = queueRepository.addToQueue(trackId)
    fun pinOffline(trackId: UUID) = downloads.pinOffline(trackId)

    fun removeTrack(trackId: UUID, offline: Boolean, fromLibrary: Boolean) {
        viewModelScope.launch {
            try {
                trackRemoval(trackId, offline, fromLibrary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _removalErrors.emit(error.message ?: "Unable to remove song")
            }
        }
    }
    fun toggleFavorite(trackId: UUID) {
        val status = _aux.value.statuses[trackId] ?: return
        viewModelScope.launch { libraryRepository.setFavorite(trackId, !status.favorite) }
    }
    fun addToPlaylist(trackId: UUID, playlistId: UUID) = viewModelScope.launch {
        playlists.addTrack(playlistId, trackId)
    }

    /** Remove queue occurrences, not distinct tracks: repeated songs remain individually selectable. */
    fun performSelectionAction(action: LibrarySelectionAction, items: List<QueueItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            try {
                val trackIds = items.map(QueueItem::id)
                when (action) {
                    LibrarySelectionAction.DELETE ->
                        items.map(QueueItem::occurrenceId).forEach(queueRepository::removeOccurrence)
                    LibrarySelectionAction.PLAY_NEXT ->
                        trackIds.asReversed().forEach(queueRepository::playNext)
                    LibrarySelectionAction.ADD_TO_QUEUE ->
                        trackIds.forEach(queueRepository::addToQueue)
                    LibrarySelectionAction.DOWNLOAD ->
                        trackIds.distinct().forEach(downloads::pinOffline)
                    LibrarySelectionAction.LIKE, LibrarySelectionAction.UNLIKE -> {
                        val favorite = action == LibrarySelectionAction.LIKE
                        trackIds.distinct().forEach { libraryRepository.setFavorite(it, favorite) }
                    }
                    LibrarySelectionAction.PLAY_SELECTED ->
                        queueRepository.replaceAndPlay(trackIds, PlaybackMode.ORDERED)
                    LibrarySelectionAction.SHUFFLE ->
                        queueRepository.replaceAndPlay(trackIds, PlaybackMode.PURE_SHUFFLE)
                    LibrarySelectionAction.SHARE, LibrarySelectionAction.ADD_TO_PLAYLIST -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _removalErrors.emit(error.message ?: "Unable to apply selected action")
            }
        }
    }

    fun addSelectedToPlaylist(items: List<QueueItem>, playlistId: UUID) = viewModelScope.launch {
        try {
            items.map(QueueItem::id).distinct().forEach { playlists.addTrack(playlistId, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _removalErrors.emit(error.message ?: "Unable to add selected tracks")
        }
    }

    fun resolveSelectionTracks(ids: List<UUID>, onResolved: (List<Track>) -> Unit) =
        viewModelScope.launch {
            try {
                onResolved(ids.mapNotNull { libraryRepository.trackById(it) })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _removalErrors.emit(error.message ?: "Unable to forward selected tracks")
            }
        }
}
