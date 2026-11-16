package dev.behradhz.meowzix.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.HomeHistoryReader
import dev.behradhz.meowzix.data.repository.ArtistSummary
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueRepository
import kotlinx.coroutines.withTimeoutOrNull
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val sections: List<HomeRecommendationSection> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryQueryRepository,
    private val playlists: PlaylistRepository,
    private val history: HomeHistoryReader,
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

    /** Refresh once for each app foreground session; switching tabs never starts a new session. */
    fun onHomeVisible(sessionId: Long) {
        visibleSessionId = sessionId
        if (sessionId <= 0L || activeRefreshJob?.isActive == true) return
        activeRefreshJob = viewModelScope.launch { refreshForSession(sessionId) }
    }

    fun onHomeHidden() {
        activeRefreshJob?.cancel()
        activeRefreshJob = null
    }

    /** Load the entire catalog only when the listener actually selects an artist. */
    fun playArtist(normalizedName: String, onPlayCollection: (List<Track>) -> Unit) {
        viewModelScope.launch {
            val tracks = library.artistTracks(normalizedName).first()
            if (tracks.isNotEmpty()) onPlayCollection(tracks)
        }
    }

    private suspend fun refreshForSession(sessionId: Long, force: Boolean = false) {
        if (!force && !HomeSessionRefreshPolicy.needsRefresh(
                sessionId, cachedSessionId, cachedState?.isReady == true
            )) {
            cachedState?.let { _state.value = it }
            return
        }

        refreshMutex.withLock {
            if (!force && !HomeSessionRefreshPolicy.needsRefresh(
                    sessionId, cachedSessionId, cachedState?.isReady == true
                )) {
                cachedState?.let { _state.value = it }
                return@withLock
            }

            try {
                // Keep the previous snapshot visible until every section is ready.
                val snapshot = buildHomeFeed()
                cachedState = snapshot
                cachedSessionId = sessionId
                _state.value = snapshot
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed attempt is not cached as a completed session.
                if (cachedState == null) _state.value = HomeUiState(isReady = true)
            }
        }
    }

    private suspend fun buildHomeFeed(): HomeUiState = withContext(Dispatchers.Default) {
        val playlistRows = playlists.observePlaylists().first()
        val recentTrackIds = history.recentTrackIds().first()

        // The bounded track lookup already enforces source availability.
        // Do not enumerate the entire music library merely to show 12 recent rows.
        val recent = library.tracks(recentTrackIds).take(12)

        // Select only the content Home actually displays; the other recommendation
        // sections are computed on demand by their respective screens.
        val recommendedIds = recommendationEngine
            .forYouNow(playback.state.value.currentTrack?.id)
            .map { it.trackId }

        val recommended = library.tracks(recommendedIds.take(14))
            .ifEmpty { library.homeFallback(14) }
        val artistKeys = homeArtistKeys(recommended + recent + recentlyAdded)
        val artists = homeArtistCards(
            artistKeys,
            library.homeArtistSummaries(artistKeys.map { it.first }),
        )

        HomeUiState(
            recommended = recommended,
            recent = recent,
            recentlyAdded = recentlyAdded,
            playlists = playlistRows.sortedByDescending(PlaylistSummary::updatedAt).take(10),
            artists = artists,
            isReady = true,
            // Other recommendation sections are not rendered by Home. Their full
            // Track rows should only be hydrated if/when a visible surface needs them.
            sections = emptyList(),
        )
    }

    private companion object {
        const val HOME_VIBE_SIZE = 15
        val refreshMutex = Mutex()
        @Volatile var cachedState: HomeUiState? = null
        @Volatile var cachedSessionId: Long = 0L
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
