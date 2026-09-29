package dev.behradhz.meowzix.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.AlbumSummaryRow
import dev.behradhz.meowzix.data.db.ArtistSummaryRow
import dev.behradhz.meowzix.data.db.LibraryBrowseDao
import dev.behradhz.meowzix.data.db.SearchTrackRow
import dev.behradhz.meowzix.data.db.TrackAvailabilityRow
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ArtistSummary(
    val name: String,
    val normalizedName: String,
    val trackCount: Int,
    val artworkRef: String?,
)

data class AlbumSummary(
    val name: String,
    val artist: String,
    val normalizedArtist: String,
    val trackCount: Int,
    val artworkRef: String?,
)

@Singleton
class LibraryQueryRepository @Inject constructor(
    private val dao: LibraryBrowseDao,
) {
    suspend fun search(query: String, limit: Int = 80): List<Track> {
        val normalized = TextNormalizer.normalize(query) ?: return emptyList()
        val matchExpression = buildTrackFtsMatchExpression(normalized)
        if (matchExpression.isBlank()) return emptyList()

        val sql = """
            SELECT
                t.id, t.title, t.normalizedTitle, t.artist, t.normalizedArtist, t.album,
                t.durationMs, t.trackNumber, t.year, t.artworkRef, t.favorite, t.hidden,
                t.createdAtEpochMs, t.updatedAtEpochMs
            FROM track_search_fts
            INNER JOIN tracks t ON t.rowid = track_search_fts.rowid
            WHERE track_search_fts MATCH ?
              AND t.hidden = 0
              AND (
                  EXISTS (
                      SELECT 1 FROM track_sources local
                      WHERE local.trackId = t.id AND local.availability = 'AVAILABLE_LOCAL'
                  )
                  OR EXISTS (
                      SELECT 1
                      FROM telegram_track_sources tg
                      INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                      INNER JOIN telegram_selected_sources selected
                        ON selected.accountId = tg.accountId AND selected.chatId = tg.chatId
                      WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
                  )
              )
            ORDER BY
                CASE WHEN t.normalizedTitle = ? THEN 0 ELSE 1 END,
                t.normalizedTitle ASC
            LIMIT ?
        """.trimIndent()
        return dao.searchTracks(
            SimpleSQLiteQuery(
                sql,
                arrayOf<Any>(matchExpression, normalized, limit.coerceIn(1, 200)),
            ),
        ).map(SearchTrackRow::toDomain)
    }

    fun availability(): Flow<Map<UUID, LibraryTrackAvailability>> =
        dao.observeAvailabilityRows().map { rows ->
            rows.associate { row -> UUID.fromString(row.trackId) to row.toAvailability() }
        }

    fun favoriteCount(): Flow<Int> = dao.observeFavoriteCount()

    fun artists(): Flow<List<ArtistSummary>> = dao.observeArtistSummaries().map { rows ->
        rows.map(ArtistSummaryRow::toSummary)
    }

    fun albums(): Flow<List<AlbumSummary>> = dao.observeAlbumSummaries().map { rows ->
        rows.map(AlbumSummaryRow::toSummary)
    }

    fun artistTracks(normalizedArtist: String): Flow<List<Track>> =
        dao.observeTracksByArtist(normalizedArtist).map { rows -> rows.map(TrackEntity::toDomainTrack) }

    fun albumTracks(album: String, normalizedArtist: String): Flow<List<Track>> =
        dao.observeTracksByAlbum(album, normalizedArtist).map { rows -> rows.map(TrackEntity::toDomainTrack) }

    fun favoriteTracks(): Flow<List<Track>> =
        dao.observeFavoriteTracks().map { rows -> rows.map(TrackEntity::toDomainTrack) }
}

internal fun buildTrackFtsMatchExpression(normalizedQuery: String): String = normalizedQuery
    .split(' ')
    .asSequence()
    .filter(String::isNotBlank)
    .map(::ftsPrefixToken)
    .joinToString(" AND ")

private fun ftsPrefixToken(token: String): String {
    val escaped = token.replace("\"", "\"\"")
    return "\"$escaped\"*"
}

private fun TrackAvailabilityRow.toAvailability(): LibraryTrackAvailability = when {
    hasOfflineSource -> LibraryTrackAvailability.OFFLINE
    hasCloudSource -> LibraryTrackAvailability.CLOUD
    else -> LibraryTrackAvailability.UNAVAILABLE
}

private fun ArtistSummaryRow.toSummary() = ArtistSummary(name, normalizedName, trackCount, artworkRef)

private fun AlbumSummaryRow.toSummary() = AlbumSummary(name, artist, normalizedArtist, trackCount, artworkRef)

private fun SearchTrackRow.toDomain() = Track(
    id = UUID.fromString(id),
    title = title,
    normalizedTitle = normalizedTitle,
    artist = artist,
    normalizedArtist = normalizedArtist,
    album = album,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    artworkRef = artworkRef,
    favorite = favorite,
    hidden = hidden,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

private fun TrackEntity.toDomainTrack() = Track(
    id = UUID.fromString(id),
    title = title,
    normalizedTitle = normalizedTitle,
    artist = artist,
    normalizedArtist = normalizedArtist,
    album = album,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    artworkRef = artworkRef,
    favorite = favorite,
    hidden = hidden,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)
