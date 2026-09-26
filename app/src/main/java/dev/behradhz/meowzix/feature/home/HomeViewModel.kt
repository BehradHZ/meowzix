package dev.behradhz.meowzix.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.ListeningHistoryRepository
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    library: MusicLibraryRepository,
    playlists: PlaylistRepository,
    history: ListeningHistoryRepository,
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
                library.observeTracks(),
                playlists.observePlaylists(),
                history.observeEvents(),
            ) { tracks, playlistRows, events -> Triple(tracks, playlistRows, events) }
                .collect { (tracks, playlistRows, events) ->
                    val byId = tracks.associateBy(Track::id)
                    val recent = events
                        .asSequence()
                        .filter { it.type == ListeningEventType.PLAY_STARTED || it.type == ListeningEventType.MANUAL_SELECTED }
                        .sortedByDescending { it.occurredAt }
                        .mapNotNull { byId[it.trackId] }
                        .distinctBy(Track::id)
                        .take(12)
                        .toList()

                    val bucket = ListeningEventSemantics.timeContext(
                        Instant.now(),
                        ZoneId.systemDefault(),
                    ).bucket
                    val recommendedIds = runCatching {
                        recommendationEngine.generate(
                            allowedTrackIds = tracks.map(Track::id),
                            currentTrackId = recent.firstOrNull()?.id,
                            timeBucket = bucket,
                        ).trackIds
                    }.getOrDefault(emptyList())
                    val recommended = recommendedIds
                        .mapNotNull(byId::get)
                        .distinctBy(Track::id)
                        .take(14)
                        .ifEmpty {
                            tracks.sortedWith(
                                compareByDescending<Track> { it.favorite }
                                    .thenByDescending { it.updatedAt },
                            ).take(14)
                        }

                    val artists = (recommended + recent + tracks)
                        .asSequence()
                        .mapNotNull { it.artist?.trim()?.takeIf(String::isNotBlank) }
                        .distinct()
                        .take(8)
                        .map { artist ->
                            SuggestedArtist(
                                name = artist,
                                tracks = tracks.filter { it.artist.equals(artist, ignoreCase = true) },
                            )
                        }
                        .filter { it.tracks.isNotEmpty() }
                        .toList()

                    _state.update {
                        HomeUiState(
                            recommended = recommended,
                            recent = recent,
                            recentlyAdded = tracks.sortedByDescending(Track::createdAt).take(12),
                            playlists = playlistRows.sortedByDescending(PlaylistSummary::updatedAt).take(10),
                            artists = artists,
                            isReady = true,
                        )
                    }
                }
        }
    }
}
