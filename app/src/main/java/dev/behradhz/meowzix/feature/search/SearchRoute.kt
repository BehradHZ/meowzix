package dev.behradhz.meowzix.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.ui.components.TrackArtwork

@Composable
fun SearchRoute(
    query: String,
    currentTrackId: java.util.UUID?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayArtist: (List<Track>) -> Unit,
    onPlayAlbum: (List<Track>) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    LaunchedEffect(query) { viewModel.setQuery(query) }
    val matchingTracks by viewModel.results.collectAsStateWithLifecycle()
    val normalizedQuery = TextNormalizer.normalize(query)

    // These groups are intentionally derived only from the bounded FTS result set (<=80), never
    // from the complete library.
    val artists = remember(matchingTracks) {
        matchingTracks
            .filter { !it.artist.isNullOrBlank() }
            .groupBy { it.artist!!.trim() }
            .entries
            .sortedByDescending { it.value.size }
            .take(8)
    }
    val albums = remember(matchingTracks) {
        matchingTracks
            .filter { !it.album.isNullOrBlank() }
            .groupBy { (it.album ?: "Unknown album") to (it.artist ?: "Unknown artist") }
            .entries
            .sortedByDescending { it.value.size }
            .take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 18.dp),
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Text(
            text = when {
                normalizedQuery == null -> "The dock is your search field."
                matchingTracks.isEmpty() -> "No matches for “${query.trim()}”"
                else -> "${matchingTracks.size} matching tracks"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )

        if (normalizedQuery == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(24.dp)
                            .size(48.dp),
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 188.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (artists.isNotEmpty()) {
                item(key = "artists-title", contentType = "header") { SearchSectionTitle("Artists") }
                items(
                    items = artists,
                    key = { "artist:${it.key}" },
                    contentType = { "artist" },
                ) { (artist, artistTracks) ->
                    SearchGroupRow(
                        title = artist,
                        subtitle = "${artistTracks.size} matching tracks",
                        icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        onClick = { onPlayArtist(artistTracks) },
                    )
                }
            }
            if (albums.isNotEmpty()) {
                item(key = "albums-title", contentType = "header") { SearchSectionTitle("Albums") }
                items(
                    items = albums,
                    key = { "album:${it.key.first}:${it.key.second}" },
                    contentType = { "album" },
                ) { entry ->
                    SearchGroupRow(
                        title = entry.key.first,
                        subtitle = entry.key.second,
                        icon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                        onClick = { onPlayAlbum(entry.value) },
                    )
                }
            }
            if (matchingTracks.isNotEmpty()) {
                item(key = "songs-title", contentType = "header") { SearchSectionTitle("Songs") }
                items(
                    items = matchingTracks,
                    key = { it.id.toString() },
                    contentType = { "track" },
                ) { track ->
                    SearchTrackRow(
                        track = track,
                        isCurrent = currentTrackId == track.id,
                        onClick = { onPlayTrack(track, matchingTracks) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SearchGroupRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.primary,
        ) { Box(contentAlignment = Alignment.Center) { icon() } }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f))
        }
    }
}

@Composable
private fun SearchTrackRow(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(
            artworkRef = track.artworkRef,
            description = track.title,
            size = 50.dp,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(track.artist, track.album).joinToString(" · ").ifBlank { "Unknown artist" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
