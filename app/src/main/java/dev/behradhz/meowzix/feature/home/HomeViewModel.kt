package dev.behradhz.meowzix.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.HomeHistoryReader
import dev.behradhz.meowzix.data.repository.HomeRecommendationSnapshot
import dev.behradhz.meowzix.data.repository.HomeRecommendationSnapshotStore
import dev.behradhz.meowzix.data.repository.ArtistSummary
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueRepository
import kotlinx.coroutines.withTimeoutOrNull
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SuggestedArtist(
    val name: String,
    val normalizedName: String,
    val trackCount: Int,
    val artworkRef: String?,
)

data class HomeRecommendationSection(
    val section: dev.behradhz.meowzix.domain.recommendation.RecommendationSection,
    val tracks: List<Track>,
)

data class HomeUiState(
    val recommended: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val artists: List<SuggestedArtist> = emptyList(),
    val isReady: Boolean = false,
    val libraryRevision: Long = 0L,
    val isRefreshing: Boolean = false,
    val sections: List<HomeRecommendationSection> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryQueryRepository,
    private val playlists: PlaylistRepository,
    private val history: HomeHistoryReader,
    private val recommendationSnapshotStore: HomeRecommendationSnapshotStore,
    private val recommendationEngine: RecommendationEngine,
    private val playback: PlaybackController,
    private val queueRepository: QueueRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(cachedState ?: HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var homePlayGeneration = 0L

    /** Explicit reload remains possible; routine updates happen once per foreground session. */
    fun refresh() = viewModelScope.launch {
        val sessionId = visibleSessionId
        if (sessionId > 0L) refreshForSession(sessionId, force = true)
    }

    /** Start the selected song without waiting for model inference. */
    fun playFromHome(trackId: UUID) {
        val generation = ++homePlayGeneration
        queueRepository.replaceAndPlay(listOf(trackId), trackId, PlaybackMode.ORDERED)
        viewModelScope.launch {
            val vibeIds = runCatching {
                recommendationEngine.continueTheVibe(trackId, HOME_VIBE_SIZE)
                    .map { it.trackId }
                    .distinct()
                    .filterNot { it == trackId }
            }.getOrDefault(emptyList())
            if (generation != homePlayGeneration || vibeIds.isEmpty()) return@launch
            // A new play request can supersede this request while ML is evaluating.
            val active = withTimeoutOrNull(5_000L) {
                queueRepository.queueState.first { queue ->
                    queue.items.getOrNull(queue.currentIndex)?.id == trackId
                }
            } ?: return@launch
            if (generation != homePlayGeneration ||
                active.items.getOrNull(active.currentIndex)?.id != trackId
            ) return@launch
            queueRepository.attachFutureToSingleTrack(
                expectedTrackId = trackId,
                fullOrderedTrackIds = listOf(trackId) + vibeIds,
                mode = PlaybackMode.ORDERED,
            )
        }
    }

    private var activeRefreshJob: Job? = null
    private var visibleSessionId: Long = 0L
    private var homeChangesJob: Job? = null
    private var homeSlotJob: Job? = null

    /** Refresh once for each app foreground session; switching tabs never starts a new session. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun onHomeVisible(sessionId: Long) {
        visibleSessionId = sessionId
        if (sessionId <= 0L) return
        if (activeRefreshJob?.isActive != true) {
            activeRefreshJob = viewModelScope.launch { refreshForSession(sessionId) }
        }
        // Room invalidations drive changes only while Home is visible. Group bursts
        // of imports so one Telegram sync does not recompute the feed per song.
        homeChangesJob?.cancel()
        homeChangesJob = viewModelScope.launch {
            combine(library.observeTrackRevision(), playlists.observePlaylists()) { revision, entries ->
                revision to entries.sortedByDescending(PlaylistSummary::updatedAt).take(10)
            }
                .distinctUntilChanged()
                .debounce(650)
                .collect { (revision, entries) ->
                    if (visibleSessionId == sessionId && _state.value.isReady &&
                        (revision != _state.value.libraryRevision || entries != _state.value.playlists)
                    ) {
                        refreshForSession(sessionId, force = true)
                    }
                }
        }
        // Wake up at the next time-bucket boundary rather than polling each second.
        homeSlotJob?.cancel()
        homeSlotJob = viewModelScope.launch {
            while (visibleSessionId == sessionId) {
                delay(HomeRecommendationRefreshPolicy.millisUntilNextSlot(Instant.now(), ZoneId.systemDefault()))
                if (visibleSessionId == sessionId) refreshForSession(sessionId, force = true)
            }
        }
    }

    fun onHomeHidden() {
        activeRefreshJob?.cancel()
        activeRefreshJob = null
        homeChangesJob?.cancel()
        homeChangesJob = null
        homeSlotJob?.cancel()
        homeSlotJob = null
        visibleSessionId = 0L
    }

    /** Load the entire catalog only when the listener actually selects an artist. */
    fun playArtist(normalizedName: String, onPlayCollection: (List<Track>) -> Unit) {
        viewModelScope.launch {
            val tracks = library.artistTracks(normalizedName).first()
            if (tracks.isNotEmpty()) onPlayCollection(tracks)
        }
    }

    private suspend fun refreshForSession(sessionId: Long, force: Boolean = false) {
        refreshMutex.withLock {
            val slot = HomeRecommendationRefreshPolicy.slot(Instant.now(), ZoneId.systemDefault())
            if (!force && !HomeRecommendationRefreshPolicy.needsRefresh(cachedRecommendationSlotKey, slot) &&
                !HomeSessionRefreshPolicy.needsRefresh(sessionId, cachedSessionId, cachedState?.isReady == true)
            ) {
                cachedState?.let { _state.value = it }
                return@withLock
            }
            try {
                // Read a tiny persisted snapshot only on Home refresh, never on playback events.
                val stored = recommendationSnapshotStore.read()
                val recommendationIds = if (stored?.slotKey == slot.key) {
                    stored.trackIds
                } else {
                    // The ONLY Home recommendation inference point. When the app was closed at
                    // the scheduled hour, the current slot is calculated on the next Home visit.
                    val rankedIds = recommendationEngine
                        .forYouNow(playback.state.value.currentTrack?.id, HomeRecommendationRefreshPolicy.seed(slot))
                        .map { it.trackId }.distinct().take(14)
                    val available = library.tracks(rankedIds)
                    val selectedIds = if (available.isNotEmpty()) {
                        available.map(Track::id)
                    } else {
                        library.homeFallback(14).map(Track::id)
                    }
                    recommendationSnapshotStore.save(HomeRecommendationSnapshot(slot.key, selectedIds))
                    selectedIds
                }
                // Only metadata and bounded rows are rehydrated during ordinary library refresh.
                val snapshot = buildHomeFeed(recommendationIds)
                cachedRecommendationSlotKey = slot.key
                cachedState = snapshot
                cachedSessionId = sessionId
                _state.value = snapshot
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Failed inference is not marked complete; retry on a later Home visit.
                if (cachedState == null) _state.value = HomeUiState(isReady = true)
            }
        }
    }

    private suspend fun buildHomeFeed(recommendationIds: List<UUID>): HomeUiState =
        withContext(Dispatchers.Default) {
        val playlistRows = playlists.observePlaylists().first()
        val recentTrackIds = history.recentTrackIds().first()

        val recent = library.tracks(recentTrackIds).take(12)
        val recentlyAdded = library.recentlyAdded(12)
        // No ranking or fallback here: the scheduled snapshot owns the selected IDs.
        val recommended = library.tracks(recommendationIds)
        val artistKeys = homeArtistKeys(recommended + recent + recentlyAdded)
        val artists = homeArtistCards(
            artistKeys,
            library.homeArtistSummaries(artistKeys.map { it.first }),
        )

        HomeUiState(
            libraryRevision = library.trackRevision(),
            recommended = recommended,
            recent = recent,
            recentlyAdded = recentlyAdded,
            playlists = playlistRows.sortedByDescending(PlaylistSummary::updatedAt).take(10),
            artists = artists,
            isReady = true,
            sections = emptyList(),
        )
    }

    private companion object {
        const val HOME_VIBE_SIZE = 15
        val refreshMutex = Mutex()
        @Volatile var cachedState: HomeUiState? = null
        @Volatile var cachedSessionId: Long = 0L
        @Volatile var cachedRecommendationSlotKey: String? = null
    }
}

internal object HomeSessionIds {
    private val sequence = AtomicLong(0L)
    fun next(): Long = sequence.incrementAndGet()
}

internal object HomeSessionRefreshPolicy {
    fun needsRefresh(requestedSessionId: Long, cachedSessionId: Long, hasReadySnapshot: Boolean): Boolean =
        requestedSessionId > 0L && (!hasReadySnapshot || requestedSessionId != cachedSessionId)
}

/** Preserve the existing Home artist order while limiting previews to eight distinct artists. */
internal fun homeArtistKeys(tracks: List<Track>): List<Pair<String, String>> =
    tracks.asSequence()
        .mapNotNull { track ->
            val display = track.artist?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val normalized = track.normalizedArtist
                ?: TextNormalizer.normalize(display)
                ?: return@mapNotNull null
            normalized to display
        }
        .distinctBy { it.first }
        .take(8)
        .toList()

internal fun homeArtistCards(
    artistKeys: List<Pair<String, String>>,
    summaries: List<ArtistSummary>,
): List<SuggestedArtist> {
    val byName = summaries.associateBy(ArtistSummary::normalizedName)
    return artistKeys.mapNotNull { (normalized, display) ->
        val summary = byName[normalized] ?: return@mapNotNull null
        SuggestedArtist(display, normalized, summary.trackCount, summary.artworkRef)
    }
}
