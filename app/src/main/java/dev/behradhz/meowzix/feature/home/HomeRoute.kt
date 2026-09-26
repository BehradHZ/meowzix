package dev.behradhz.meowzix.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.ui.components.TrackArtwork

@Composable
fun HomeRoute(
    currentTrack: Track?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayCollection: (List<Track>) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 188.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item("hero") {
            Column {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Pick something fast or let Meowzix choose from your listening history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (currentTrack != null) {
            item("continue") {
                ContinueListeningCard(
                    track = currentTrack,
                    onClick = { onPlayTrack(currentTrack, listOf(currentTrack)) },
                )
            }
        }

        if (state.recommended.isNotEmpty()) {
            item("recommended") {
                HomeTrackSection(
                    title = "Made for you",
                    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    tracks = state.recommended,
                    onPlay = { track -> onPlayTrack(track, state.recommended) },
                )
            }
        }

        if (state.recent.isNotEmpty()) {
            item("recent") {
                HomeTrackSection(
                    title = "Recently played",
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    tracks = state.recent,
                    onPlay = { track -> onPlayTrack(track, state.recent) },
                )
            }
        }

        if (state.artists.isNotEmpty()) {
            item("artists") {
                Column {
                    SectionTitle("Suggested artists")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.artists.forEach { artist ->
                            Surface(
                                modifier = Modifier
                                    .width(156.dp)
                                    .clickable { onPlayCollection(artist.tracks) },
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Surface(
                                        modifier = Modifier.size(52.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Person, contentDescription = null)
                                        }
                                    }
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                    Text(
                                        text = "${artist.tracks.size} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.playlists.isNotEmpty()) {
            item("playlists") {
                PlaylistSection(state.playlists, onOpenLibrary)
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item("new") {
                HomeTrackSection(
                    title = "Recently added",
                    icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                    tracks = state.recentlyAdded,
                    onPlay = { track -> onPlayTrack(track, state.recentlyAdded) },
                )
            }
        }

        if (state.isReady && state.recentlyAdded.isEmpty()) {
            item("empty") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenLibrary),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Build your library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Open Library to scan local music or connect Telegram from Profile.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningCard(track: Track, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track.artworkRef, track.title, 72.dp)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Continue listening", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f), maxLines = 1)
            }
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                }
            }
        }
    }
}

@Composable
private fun HomeTrackSection(
    title: String,
    icon: @Composable () -> Unit,
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) { Box(contentAlignment = Alignment.Center) { icon() } }
            Spacer(Modifier.size(8.dp))
            SectionTitle(title)
        }
        LazyRow(
            contentPadding = PaddingValues(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = { it.id.toString() }) { track ->
                Surface(
                    modifier = Modifier
                        .width(148.dp)
                        .clickable { onPlay(track) },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        TrackArtwork(track.artworkRef, track.title, 128.dp)
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 9.dp),
                        )
                        Text(
                            track.artist ?: "Unknown artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSection(playlists: List<PlaylistSummary>, onOpenLibrary: () -> Unit) {
    Column {
        SectionTitle("Playlists")
        LazyRow(
            contentPadding = PaddingValues(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(playlists, key = { it.id.toString() }) { playlist ->
                Surface(
                    modifier = Modifier
                        .width(168.dp)
                        .height(112.dp)
                        .clickable(onClick = onOpenLibrary),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, modifier = Modifier.size(30.dp))
                        Column {
                            Text(playlist.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.trackCount} tracks", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}
