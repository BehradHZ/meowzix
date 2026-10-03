package dev.behradhz.meowzix.feature.home

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.HomeHistoryReader
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SuggestedArtist(
    val name: String,
    val tracks: List<Track>,
)

data class HomeRecommendationSection(val section: dev.behradhz.meowzix.domain.recommendation.RecommendationSection, val tracks: List<Track>)

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
    private val playback: dev.behradhz.meowzix.domain.playback.PlaybackController,
    private val listeningHistory: dev.behradhz.meowzix.domain.history.ListeningHistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(cachedState ?: HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        cachedAtElapsedRealtime = 0L
        refreshIfDue(forceWhenEmpty = true)
    }

    init {
        viewModelScope.launch {
            listeningHistory.observeTrackStats().collect {
                cachedAtElapsedRealtime = 0L
                refreshIfDue(forceWhenEmpty = true)
            }
        }
        viewModelScope.launch {
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

    private suspend fun buildHomeFeed(): HomeUiState {
        val playlistRows = playlists.observePlaylists().first()
        val recentTrackIds = history.recentTrackIds().first()

        val allowedTrackIds = library.availableTrackIds()
        val allowedSet = allowedTrackIds.toHashSet()
        val recent = library.tracks(recentTrackIds.filter(allowedSet::contains)).take(12)

        val sectionRows = recommendationEngine.sections(playback.state.value.currentTrack?.id)
        val recommendedIds = runCatching {
            sectionRows.firstOrNull { it.kind == dev.behradhz.meowzix.domain.recommendation.RecommendationSectionKind.FOR_YOU_NOW }?.items?.map { it.trackId }.orEmpty()
        }.getOrDefault(emptyList())

        val recommended = library.tracks(recommendedIds.take(14))
            .ifEmpty { library.homeFallback(14) }
        val recentlyAdded = library.recentlyAdded(12)

        val artistKeys = (recommended + recent + recentlyAdded)
            .asSequence()
            .mapNotNull { track ->
                val display = track.artist?.trim()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val normalized = track.normalizedArtist
                    ?: TextNormalizer.normalize(display)
                    ?: return@mapNotNull null
                normalized to display
            }
            .distinctBy { it.first }
            .take(8)
            .toList()
        val artists = artistKeys.mapNotNull { (normalized, display) ->
            val artistTracks = library.artistTracks(normalized).first()
            if (artistTracks.isEmpty()) null else SuggestedArtist(display, artistTracks)
        }

        return HomeUiState(
            recommended = recommended,
            recent = recent,
            recentlyAdded = recentlyAdded,
            playlists = playlistRows.sortedByDescending(PlaylistSummary::updatedAt).take(10),
            artists = artists,
            isReady = true,
            sections = sectionRows.map { section -> HomeRecommendationSection(section, library.tracks(section.items.map { it.trackId })) },
        )
    }

    private fun cacheAgeMs(): Long {
        if (cachedState == null || cachedAtElapsedRealtime == 0L) return Long.MAX_VALUE
        return (SystemClock.elapsedRealtime() - cachedAtElapsedRealtime).coerceAtLeast(0L)
    }

    private companion object {
        const val HOME_REFRESH_INTERVAL_MS = 5L * 60L * 1_000L
        const val MIN_REFRESH_CHECK_MS = 30_000L
        val refreshMutex = Mutex()

        @Volatile
        var cachedState: HomeUiState? = null

        @Volatile
        var cachedAtElapsedRealtime: Long = 0L
    }
}
