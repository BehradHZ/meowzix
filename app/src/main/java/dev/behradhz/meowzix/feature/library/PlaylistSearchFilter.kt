package dev.behradhz.meowzix.feature.library

import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.feature.search.matchesGlobalSearch

/** Filters visible songs; playback and saved playlist ordering stay independent. */
internal fun filterPlaylistTracks(tracks: List<Track>, query: String): List<Track> {
    if (query.isBlank()) return tracks
    return tracks.filter { matchesGlobalSearch(query, it.title, it.artist, it.album) }
}
