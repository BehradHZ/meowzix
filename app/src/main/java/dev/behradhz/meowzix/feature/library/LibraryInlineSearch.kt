package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.feature.search.SearchViewModel

@Composable
internal fun LibraryInlineSearch(
    query: String,
    onOpenNowPlaying: () -> Unit,
    viewModel: LibraryViewModel,
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(query) { searchViewModel.setQuery(query) }
    val results by searchViewModel.results.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp)) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (results.isEmpty()) "No matches" else "${results.size} matching tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            )
        }

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No tracks match “${query.trim()}”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 188.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(results, key = Track::id) { track ->
                    SwipeableLibraryTrackRow(
                        track = track,
                        isCurrent = state.playback.currentTrack?.id == track.id,
                        onClick = {
                            viewModel.playTrack(track, results)
                            onOpenNowPlaying()
                        },
                        onPlayNext = { viewModel.playNext(track) },
                        onAddToQueue = { viewModel.addToQueue(track) },
                        onPinOffline = { viewModel.pinOffline(track) },
                        availability = state.availability[track.id] ?: LibraryTrackAvailability.UNAVAILABLE,
                        download = state.downloads[track.id],
                        playlists = state.playlists,
                        onFavorite = { viewModel.setFavorite(track) },
                        onAddToPlaylist = { playlistId -> viewModel.addToPlaylist(track, playlistId) },
                        onGoToArtist = {},
                        onEnsureArtwork = { viewModel.ensureArtwork(track) },
                    )
                }
            }
        }
    }
}
