package dev.behradhz.meowzix.feature.home

import android.os.SystemClock
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
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

    fun refresh() = viewModelScope.launch {
        cachedAtElapsedRealtime = 0L
        refreshIfDue(forceWhenEmpty = true)
    }

    /**
     * Home is recommendation-first: tapping any track starts that track and immediately
     * rebuilds the queue around its local Continue-the-Vibe recommendations.
     */
    fun playFromHome(trackId: UUID) {
        viewModelScope.launch {
            val vibeIds = runCatching {
                recommendationEngine.continueTheVibe(trackId, HOME_VIBE_SIZE)
                    .map { it.trackId }
            }.getOrDefault(emptyList())

            val queueIds = buildList {
                add(trackId)
                addAll(vibeIds.filterNot { it == trackId })
            }.distinct()

            queueRepository.replaceAndPlay(
                trackIds = queueIds,
                startTrackId = trackId,
                mode = PlaybackMode.ORDERED,
            )
        }
    }

    private var activeRefreshJob: Job? = null

    /**
     * Home data is only observed while Home is visible. Back-stack ViewModels must not
     * keep polling or running recommendation queries while another screen is open.
     */
    @OptIn(FlowPreview::class)
    fun onHomeVisible() {
        if (activeRefreshJob?.isActive == true) return
        activeRefreshJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    // A one-track history projection is enough to detect a new play.
                    // Avoid observing and materializing every track's preference statistics.
                    history.recentTrackIds(eventLimit = 4, trackLimit = 1)
                        .distinctUntilChanged()
                        .drop(1)
                        .debounce(HOME_HISTORY_DEBOUNCE_MS)
                        .collect {
                            cachedAtElapsedRealtime = 0L
                            refreshIfDue(forceWhenEmpty = true)
                        }
                }
                launch {
                    refreshIfDue(forceWhenEmpty = true)
                    while (isActive) {
                        val age = cacheAgeMs()
                        val waitMs = if (age == Long.MAX_VALUE) {
                            HOME_REFRESH_INTERVAL_MS
                        } else {
                            (HOME_REFRESH_INTERVAL_MS - age).coerceAtLeast(MIN_REFRESH_CHECK_MS)
                        }
                        delay(waitMs)
                        refreshIfDue(forceWhenEmpty = false)
                    }
                }
            }
        }
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

    private suspend fun refreshIfDue(forceWhenEmpty: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val hasRenderableSnapshot = cachedState?.isReady == true
        if (
            hasRenderableSnapshot &&
            now - cachedAtElapsedRealtime < HOME_REFRESH_INTERVAL_MS
        ) {
            cachedState?.let { snapshot ->
                if (_state.value !== snapshot) _state.value = snapshot
            }
            return
        }
        if (!forceWhenEmpty && hasRenderableSnapshot && cacheAgeMs() < HOME_REFRESH_INTERVAL_MS) return

        refreshMutex.withLock {
            val lockedNow = SystemClock.elapsedRealtime()
            val lockedSnapshot = cachedState
            if (
                lockedSnapshot?.isReady == true &&
                lockedNow - cachedAtElapsedRealtime < HOME_REFRESH_INTERVAL_MS
            ) {
                _state.value = lockedSnapshot
                return@withLock
            }

            runCatching { buildHomeFeed() }
                .onSuccess { snapshot ->
                    cachedState = snapshot
                    cachedAtElapsedRealtime = SystemClock.elapsedRealtime()
                    _state.value = snapshot
                }
                .onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    if (cachedState == null) {
                        _state.update { current -> current.copy(isReady = true) }
                    }
                }
        }
    }

    private suspend fun buildHomeFeed(): HomeUiState = withContext(Dispatchers.Default) {
        val playlistRows = playlists.observePlaylists().first()
        val recentTrackIds = history.recentTrackIds().first()

        // The bounded track lookup already enforces source availability.
        // Do not enumerate the entire music library merely to show 12 recent rows.
        val recent = library.tracks(recentTrackIds).take(12)

        val sectionRows = recommendationEngine.sections(playback.state.value.currentTrack?.id)
        val recommendedIds = runCatching {
            sectionRows.firstOrNull {
                it.kind == dev.behradhz.meowzix.domain.recommendation.RecommendationSectionKind.FOR_YOU_NOW
            }?.items?.map { it.trackId }.orEmpty()
        }.getOrDefault(emptyList())

        val recommended = library.tracks(recommendedIds.take(14))
            .ifEmpty { library.homeFallback(14) }
        val recentlyAdded = library.recentlyAdded(12)

        val artistKeys = homeArtistKeys(recommended + recent + recentlyAdded)
        val artists = homeArtistCards(
            artistKeys,
            library.homeArtistSummaries(artistKeys.map { it.first }),
        )

        return HomeUiState(
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

    private fun cacheAgeMs(): Long {
        if (cachedState == null || cachedAtElapsedRealtime == 0L) return Long.MAX_VALUE
        return (SystemClock.elapsedRealtime() - cachedAtElapsedRealtime).coerceAtLeast(0L)
    }

    private companion object {
        const val HOME_REFRESH_INTERVAL_MS = 5L * 60L * 1_000L
        const val MIN_REFRESH_CHECK_MS = 30_000L
        const val HOME_HISTORY_DEBOUNCE_MS = 1_500L
        const val HOME_VIBE_SIZE = 15
        val refreshMutex = Mutex()

        @Volatile
        var cachedState: HomeUiState? = null

        @Volatile
        var cachedAtElapsedRealtime: Long = 0L
    }
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
