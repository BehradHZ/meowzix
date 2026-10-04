package dev.behradhz.meowzix.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.chrisbanes.haze.HazeState

@Composable
fun HomeRoute(
    hazeState: HazeState,
    currentTrack: Track?,
    onPlayCollection: (List<Track>) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        HomeAmbientBackdrop()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 22.dp,
                bottom = 198.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item("header") {
                Column {
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Your listening, shaped for this moment.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            currentTrack?.let { track ->
                item("continue") {
                    ContinueGlassHero(
                        hazeState = hazeState,
                        track = track,
                        onClick = { viewModel.playFromHome(track.id) },
                    )
                }
            }

            if (state.recommended.isNotEmpty()) {
                item("recommended") {
                    FeaturedRecommendations(
                        hazeState = hazeState,
                        tracks = state.recommended,
                        onPlay = { track -> viewModel.playFromHome(track.id) },
                    )
                }
            }

            if (state.recent.isNotEmpty()) {
                item("recent") {
                    RecentGlassList(
                        hazeState = hazeState,
                        tracks = state.recent,
                        onPlay = { track -> viewModel.playFromHome(track.id) },
                    )
                }
            }

            if (state.artists.isNotEmpty()) {
                item("artists") {
                    ArtistOrbit(
                        hazeState = hazeState,
                        artists = state.artists,
                        onPlayCollection = onPlayCollection,
                    )
                }
            }

            if (state.playlists.isNotEmpty()) {
                item("playlists") {
                    PlaylistGlassRail(
                        hazeState = hazeState,
                        playlists = state.playlists,
                        onOpenLibrary = onOpenLibrary,
                    )
                }
            }

            if (state.recentlyAdded.isNotEmpty()) {
                item("recently-added") {
                    RecentlyAddedGlassPanel(
                        hazeState = hazeState,
                        tracks = state.recentlyAdded,
                        onPlay = { track -> viewModel.playFromHome(track.id) },
                        onOpenLibrary = onOpenLibrary,
                    )
                }
            } else if (state.isReady) {
                item("empty") {
                    EmptyLibraryGlassCard(hazeState, onOpenLibrary)
                }
            }
        }
    }
}

/**
 * Keeps the quiet abstract atmosphere from 0.4, with a little more depth behind glass.
 * These shapes are deliberately non-interactive and low-contrast so the music remains primary.
 */
@Composable
private fun HomeAmbientBackdrop() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset(x = (-92).dp, y = 84.dp)
                .size(300.dp)
                .blur(92.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 108.dp, y = 210.dp)
                .size(286.dp)
                .blur(96.dp)
                .background(
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-128).dp, y = 210.dp)
                .width(360.dp)
                .height(118.dp)
                .rotate(-18f)
                .blur(74.dp)
                .background(
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.07f),
                    RoundedCornerShape(90.dp),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 88.dp, y = 72.dp)
                .size(250.dp)
                .blur(90.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun ContinueGlassHero(
    hazeState: HazeState,
    track: Track,
    onClick: () -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(34.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track.artworkRef, track.title, 92.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "CONTINUE LISTENING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = track.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier.size(48.dp),
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
private fun FeaturedRecommendations(
    hazeState: HazeState,
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
) {
    Column {
        HomeSectionHeading(
            title = "Made for you",
            subtitle = "Picked from your listening",
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
        )
        LazyRow(
            contentPadding = PaddingValues(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = { it.id.toString() }) { track ->
                GlassSurface(
                    hazeState = hazeState,
                    modifier = Modifier
                        .width(172.dp)
                        .clickable { onPlay(track) },
                    shape = RoundedCornerShape(28.dp),
                    fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                    tint = Color.White.copy(alpha = 0.08f),
                ) {
                    Column(Modifier.padding(11.dp)) {
                        TrackArtwork(track.artworkRef, track.title, 150.dp)
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(
                            text = track.artist ?: "Unknown artist",
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
private fun RecentGlassList(
    hazeState: HazeState,
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
) {
    Column {
        HomeSectionHeading(
            title = "Recently played",
            subtitle = "Jump back in",
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
        )
        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(30.dp),
            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
            tint = Color.White.copy(alpha = 0.07f),
        ) {
            Column(Modifier.padding(vertical = 5.dp)) {
                tracks.take(5).forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(track) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork(track.artworkRef, track.title, 54.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist ?: "Unknown artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.53f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistOrbit(
    hazeState: HazeState,
    artists: List<SuggestedArtist>,
    onPlayCollection: (List<Track>) -> Unit,
) {
    Column {
        HomeSectionHeading(
            title = "Artists for you",
            subtitle = "Based on what you play",
        )
        LazyRow(
            contentPadding = PaddingValues(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(artists, key = { it.name }) { artist ->
                GlassSurface(
                    hazeState = hazeState,
                    modifier = Modifier
                        .width(136.dp)
                        .clickable { onPlayCollection(artist.tracks) },
                    shape = RoundedCornerShape(68.dp),
                    fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val artwork = artist.tracks.firstOrNull()?.artworkRef
                        TrackArtwork(artwork, artist.name, 78.dp)
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 9.dp),
                        )
                        Text(
                            "${artist.tracks.size} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGlassRail(
    hazeState: HazeState,
    playlists: List<PlaylistSummary>,
    onOpenLibrary: () -> Unit,
) {
    Column {
        HomeSectionHeading(title = "Playlists", subtitle = "Your collections")
        LazyRow(
            contentPadding = PaddingValues(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(playlists, key = { it.id.toString() }) { playlist ->
                GlassSurface(
                    hazeState = hazeState,
                    modifier = Modifier
                        .width(196.dp)
                        .height(126.dp)
                        .clickable(onClick = onOpenLibrary),
                    shape = RoundedCornerShape(28.dp),
                    fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                            }
                        }
                        Column {
                            Text(
                                playlist.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedGlassPanel(
    hazeState: HazeState,
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column {
        HomeSectionHeading(title = "Recently added", subtitle = "Fresh in your library")
        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(30.dp),
            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
            tint = Color.White.copy(alpha = 0.07f),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                tracks.take(4).forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(track) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork(track.artworkRef, track.title, 48.dp)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist ?: "Unknown artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenLibrary)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Open library",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryGlassCard(
    hazeState: HazeState,
    onOpenLibrary: () -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLibrary),
        shape = RoundedCornerShape(30.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Build your library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Scan local music or connect Telegram from Profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(
    title: String,
    subtitle: String,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.width(9.dp))
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
            )
        }
    }
}
