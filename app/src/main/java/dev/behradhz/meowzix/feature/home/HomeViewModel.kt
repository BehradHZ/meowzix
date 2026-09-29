package dev.behradhz.meowzix.feature.home

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SuggestedArtist(
    val name: String,
    val tracks: List<Track>,
)

data class HomeUiState(
    val recommended: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val artists: List<SuggestedArtist> = emptyList(),
    val isReady: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryQueryRepository,
    playlists: PlaylistRepository,
    history: HomeHistoryReader,
    private val recommendationEngine: RecommendationEngine,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(),
    )

    init {
        viewModelScope.launch {
            combine(
                library.invalidations(),
                playlists.observePlaylists(),
                history.recentTrackIds(),
            ) { _, playlistRows, recentTrackIds -> playlistRows to recentTrackIds }
                .collectLatest { (playlistRows, recentTrackIds) ->
                    rebuildHomeFeed(playlistRows, recentTrackIds)
                }
        }
    }

    private suspend fun rebuildHomeFeed(
        playlistRows: List<PlaylistSummary>,
        recentTrackIds: List<UUID>,
    ) {
        // Full-library eligibility stays as compact UUIDs. Full Track objects are fetched only for
        // the bounded sets that Home will actually render.
        val allowedTrackIds = library.availableTrackIds()
        val allowedSet = allowedTrackIds.toHashSet()
        val recent = library.tracks(recentTrackIds.filter(allowedSet::contains)).take(12)

        val bucket = ListeningEventSemantics.timeContext(
            Instant.now(),
            ZoneId.systemDefault(),
        ).bucket
        val recommendedIds = runCatching {
            recommendationEngine.generate(
                allowedTrackIds = allowedTrackIds,
                currentTrackId = recent.firstOrNull()?.id,
                timeBucket = bucket,
            ).trackIds
        }.getOrDefault(emptyList())

        val recommended = library.tracks(recommendedIds.take(14))
            .ifEmpty { library.homeFallback(14) }
        val recentlyAdded = library.recentlyAdded(12)

        // Suggested artist identities are selected from the already bounded Home rows. Their full
        // playable collection is materialized only for at most eight visible artist cards.
        val artistKeys = (recommended + recent + recentlyAdded)
            .asSequence()
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
        val artists = artistKeys.mapNotNull { (normalized, display) ->
            val artistTracks = library.artistTracks(normalized).first()
            if (artistTracks.isEmpty()) null else SuggestedArtist(display, artistTracks)
        }

        _state.update {
            HomeUiState(
                recommended = recommended,
                recent = recent,
                recentlyAdded = recentlyAdded,
                playlists = playlistRows.sortedByDescending(PlaylistSummary::updatedAt).take(10),
                artists = artists,
                isReady = true,
            )
        }
    }
}
