package dev.behradhz.meowzix.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.AlbumSummaryRow
import dev.behradhz.meowzix.data.db.ArtistSummaryRow
import dev.behradhz.meowzix.data.db.LibraryBrowseDao
import dev.behradhz.meowzix.data.db.LibraryDao
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
    private val libraryDao: LibraryDao,
) {
    suspend fun search(query: String, limit: Int = 80): List<Track> {
        val normalized = TextNormalizer.normalize(query) ?: return emptyList()
        val matchExpression = buildTrackFtsMatchExpression(normalized)
        if (matchExpression.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)

        val ftsSql = """
            ${TRACK_PROJECTION.trimIndent()}
            FROM track_search_fts
            INNER JOIN tracks t ON t.rowid = track_search_fts.rowid
            WHERE track_search_fts MATCH ?
              AND $AVAILABLE_TRACK_WHERE
            ORDER BY
                CASE WHEN t.normalizedTitle = ? THEN 0 ELSE 1 END,
                t.normalizedTitle ASC
            LIMIT ?
        """.trimIndent()

        val ftsResults = runCatching {
            dao.searchTracks(
                SimpleSQLiteQuery(
                    ftsSql,
                    arrayOf<Any>(matchExpression, normalized, safeLimit),
                ),
            ).map(SearchTrackRow::toDomain)
        }.getOrDefault(emptyList())
        if (ftsResults.isNotEmpty()) return ftsResults

        // Keep typo matching as a final fallback: exact/prefix FTS and token-AND contains results
        // are deterministic and should always outrank approximate matches.
        val containsResults = fallbackContainsSearch(normalized, safeLimit)
        if (containsResults.isNotEmpty()) return containsResults
        return fuzzySearch(normalized, safeLimit)
    }

    private suspend fun fallbackContainsSearch(normalized: String, limit: Int): List<Track> {
        val tokens = normalized.split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) return emptyList()
        val tokenClause = tokens.joinToString(" AND ") {
            """
            (
                instr(t.normalizedTitle, ?) > 0
                OR instr(COALESCE(t.normalizedArtist, ''), ?) > 0
                OR instr(LOWER(COALESCE(t.album, '')), ?) > 0
            )
            """.trimIndent()
        }
        val args = buildList<Any> {
            tokens.forEach { token ->
                add(token)
                add(token)
                add(token)
            }
            add(normalized)
            add(limit)
        }.toTypedArray()

        val sql = """
            $TRACK_PROJECTION
            FROM tracks t
            WHERE $AVAILABLE_TRACK_WHERE
              AND $tokenClause
            ORDER BY
                CASE WHEN t.normalizedTitle = ? THEN 0 ELSE 1 END,
                t.normalizedTitle ASC
            LIMIT ?
        """.trimIndent()
        return dao.searchTracks(SimpleSQLiteQuery(sql, args)).map(SearchTrackRow::toDomain)
    }

    private suspend fun fuzzySearch(normalized: String, limit: Int): List<Track> {
        val anchor = FuzzyTrackSearch.anchor(normalized) ?: return emptyList()
        val candidateLimit = maxOf(80, limit * FUZZY_CANDIDATE_MULTIPLIER).coerceAtMost(MAX_FUZZY_CANDIDATES)
        val sql = """
            $TRACK_PROJECTION
            FROM tracks t
            WHERE $AVAILABLE_TRACK_WHERE
              AND (
                  instr(t.normalizedTitle, ?) > 0
                  OR instr(COALESCE(t.normalizedArtist, ''), ?) > 0
                  OR instr(LOWER(COALESCE(t.album, '')), ?) > 0
              )
            ORDER BY t.updatedAtEpochMs DESC, t.id ASC
            LIMIT ?
        """.trimIndent()
        val candidates = dao.searchTracks(
            SimpleSQLiteQuery(sql, arrayOf<Any>(anchor, anchor, anchor, candidateLimit)),
        ).map(SearchTrackRow::toDomain)

        return candidates.asSequence()
            .mapNotNull { track ->
                FuzzyTrackSearch.score(normalized, track.title, track.artist, track.album)?.let { score -> score to track }
            }
            .sortedWith(compareBy<Pair<Int, Track>>({ it.first }, { it.second.normalizedTitle }, { it.second.id.toString() }))
            .take(limit)
            .map { it.second }
            .toList()
    }

    /** Single-row lookup for surfaces that already know the canonical track UUID. */
    suspend fun track(trackId: UUID): Track? =
        libraryDao.trackById(trackId.toString())?.toDomainTrack()

    /**
     * Full eligibility set as UUIDs only. Recommendation can consider the complete library without
     * Home retaining thousands of Track objects and their strings/artwork metadata.
     */
    suspend fun availableTrackIds(): List<UUID> = dao.libraryTrackIds(
        SimpleSQLiteQuery(
            """
            SELECT t.id
            FROM tracks t
            WHERE $AVAILABLE_TRACK_WHERE
            ORDER BY t.id ASC
            """.trimIndent(),
        ),
    ).map { UUID.fromString(it.id) }

    /** Fetch full rows only for the small set a surface is actually going to render. */
    suspend fun tracks(trackIds: List<UUID>): List<Track> {
        val distinctIds = trackIds.distinct()
        if (distinctIds.isEmpty()) return emptyList()
        val placeholders = distinctIds.joinToString(",") { "?" }
        val rows = dao.searchTracks(
            SimpleSQLiteQuery(
                """
                $TRACK_PROJECTION
                FROM tracks t
                WHERE t.id IN ($placeholders) AND t.hidden = 0
                """.trimIndent(),
                distinctIds.map(UUID::toString).toTypedArray(),
            ),
        ).map(SearchTrackRow::toDomain)
        val byId = rows.associateBy(Track::id)
        return distinctIds.mapNotNull(byId::get)
    }

    suspend fun recentlyAdded(limit: Int = 12): List<Track> = boundedAvailableTracks(
        orderBy = "t.createdAtEpochMs DESC, t.id ASC",
        limit = limit,
    )

    suspend fun homeFallback(limit: Int = 14): List<Track> = boundedAvailableTracks(
        orderBy = "t.favorite DESC, t.updatedAtEpochMs DESC, t.id ASC",
        limit = limit,
    )

    /** Cheap Room invalidation signal for legacy consumers. */
    fun invalidations(): Flow<Unit> = dao.observeFavoriteCount().map { Unit }

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

    private suspend fun boundedAvailableTracks(orderBy: String, limit: Int): List<Track> =
        dao.searchTracks(
            SimpleSQLiteQuery(
                """
                $TRACK_PROJECTION
                FROM tracks t
                WHERE $AVAILABLE_TRACK_WHERE
                ORDER BY $orderBy
                LIMIT ?
                """.trimIndent(),
                arrayOf<Any>(limit.coerceIn(1, 200)),
            ),
        ).map(SearchTrackRow::toDomain)

    private companion object {
        const val FUZZY_CANDIDATE_MULTIPLIER = 4
        const val MAX_FUZZY_CANDIDATES = 200

        val TRACK_PROJECTION = """
            SELECT
                t.id, t.title, t.normalizedTitle, t.artist, t.normalizedArtist, t.album,
                t.durationMs, t.trackNumber, t.year, t.artworkRef, t.favorite, t.hidden,
                t.createdAtEpochMs, t.updatedAtEpochMs
        """.trimIndent()

        // Selecting a Telegram chat controls future sync only. Tracks that were already imported
        // remain part of the library even after that chat is unchecked.
        val AVAILABLE_TRACK_WHERE = """
            t.hidden = 0
            AND (
                EXISTS (
                    SELECT 1 FROM track_sources local
                    WHERE local.trackId = t.id AND local.availability = 'AVAILABLE_LOCAL'
                )
                OR EXISTS (
                    SELECT 1
                    FROM telegram_track_sources tg
                    INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                    WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
                )
            )
        """.trimIndent()
    }
}

internal fun buildTrackFtsMatchExpression(normalizedQuery: String): String = normalizedQuery
    .split(' ')
    .asSequence()
    .filter(String::isNotBlank)
    .map(::ftsPrefixToken)
    .joinToString(" AND ")

private fun ftsPrefixToken(token: String): String {
    val escaped = token.replace("\"", "\"\"")
    return "\"$escaped*\""
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
