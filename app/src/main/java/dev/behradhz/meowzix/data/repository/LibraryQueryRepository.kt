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
    private val searchIndexer: EffectiveTrackSearchIndexer,
) {
    suspend fun search(query: String, limit: Int = 80): List<Track> {
        val normalized = TextNormalizer.normalize(query) ?: return emptyList()
        searchIndexer.ensureReady()
        val matchExpression = buildTrackFtsMatchExpression(normalized)
        if (matchExpression.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)

        val candidateLimit = maxOf(safeLimit, safeLimit * SEARCH_CANDIDATE_MULTIPLIER)
            .coerceAtMost(MAX_SEARCH_CANDIDATES)
        val ftsSql = """
            ${SEARCH_TRACK_PROJECTION.trimIndent()}
            FROM track_search_fts
            INNER JOIN tracks t ON t.rowid = track_search_fts.rowid
            LEFT JOIN track_metadata_overrides o ON o.trackId = t.id
            WHERE track_search_fts MATCH ?
              AND $AVAILABLE_TRACK_WHERE
            ORDER BY CASE
                WHEN track_search_fts.normalizedTitle = ? THEN 0
                WHEN substr(track_search_fts.normalizedTitle, 1, length(?)) = ? THEN 1
                WHEN instr(track_search_fts.normalizedTitle, ?) > 0 THEN 2
                WHEN instr(track_search_fts.normalizedArtist, ?) > 0 THEN 3
                ELSE 4
            END, t.id ASC
            LIMIT ?
        """.trimIndent()

        val ftsResults = runCatching {
            dao.searchTracks(
                SimpleSQLiteQuery(
                    ftsSql,
                    arrayOf<Any>(
                        matchExpression,
                        normalized,
                        normalized,
                        normalized,
                        normalized,
                        normalized,
                        candidateLimit,
                    ),
                ),
            ).map(SearchTrackRow::toDomain)
        }.getOrDefault(emptyList())
        if (ftsResults.isNotEmpty()) return rankSearchResults(normalized, ftsResults, safeLimit)

        // Keep contains and typo matching bounded. They operate on normalized effective FTS fields,
        // so Persian/Arabic variants and user metadata overrides follow the same normalization path.
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
                instr(track_search_fts.normalizedTitle, ?) > 0
                OR instr(track_search_fts.normalizedArtist, ?) > 0
                OR instr(track_search_fts.album, ?) > 0
            )
            """.trimIndent()
        }
        val candidateLimit = maxOf(limit, limit * SEARCH_CANDIDATE_MULTIPLIER).coerceAtMost(MAX_SEARCH_CANDIDATES)
        val args = buildList<Any> {
            tokens.forEach { token ->
                add(token)
                add(token)
                add(token)
            }
            add(normalized)
            add(normalized)
            add(normalized)
            add(normalized)
            add(normalized)
            add(candidateLimit)
        }.toTypedArray()

        val sql = """
            $SEARCH_TRACK_PROJECTION
            FROM track_search_fts
            INNER JOIN tracks t ON t.rowid = track_search_fts.rowid
            LEFT JOIN track_metadata_overrides o ON o.trackId = t.id
            WHERE $AVAILABLE_TRACK_WHERE
              AND $tokenClause
            ORDER BY CASE
                WHEN track_search_fts.normalizedTitle = ? THEN 0
                WHEN substr(track_search_fts.normalizedTitle, 1, length(?)) = ? THEN 1
                WHEN instr(track_search_fts.normalizedTitle, ?) > 0 THEN 2
                WHEN instr(track_search_fts.normalizedArtist, ?) > 0 THEN 3
                ELSE 4
            END, t.id ASC
            LIMIT ?
        """.trimIndent()
        val candidates = dao.searchTracks(SimpleSQLiteQuery(sql, args)).map(SearchTrackRow::toDomain)
        return rankSearchResults(normalized, candidates, limit)
    }

    private suspend fun fuzzySearch(normalized: String, limit: Int): List<Track> {
        val anchor = FuzzyTrackSearch.anchor(normalized) ?: return emptyList()
        val candidateLimit = maxOf(80, limit * FUZZY_CANDIDATE_MULTIPLIER).coerceAtMost(MAX_FUZZY_CANDIDATES)
        val sql = """
            $SEARCH_TRACK_PROJECTION
            FROM track_search_fts
            INNER JOIN tracks t ON t.rowid = track_search_fts.rowid
            LEFT JOIN track_metadata_overrides o ON o.trackId = t.id
            WHERE $AVAILABLE_TRACK_WHERE
              AND (
                  instr(track_search_fts.normalizedTitle, ?) > 0
                  OR instr(track_search_fts.normalizedArtist, ?) > 0
                  OR instr(track_search_fts.album, ?) > 0
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
            .sortedWith(compareBy<Pair<Int, Track>>({ it.first }, { searchEvidenceRank(normalized, it.second) }, { it.second.normalizedTitle }, { it.second.id.toString() }))
            .take(limit)
            .map { it.second }
            .toList()
    }

    private fun rankSearchResults(normalized: String, candidates: List<Track>, limit: Int): List<Track> =
        candidates.sortedWith(
            compareBy<Track>(
                { searchEvidenceRank(normalized, it) },
                { it.normalizedTitle },
                { it.id.toString() },
            ),
        ).take(limit)

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
        const val SEARCH_CANDIDATE_MULTIPLIER = 3
        const val MAX_SEARCH_CANDIDATES = 200

        val SEARCH_TRACK_PROJECTION = """
            SELECT
                t.id,
                COALESCE(o.title, t.title) AS title,
                track_search_fts.normalizedTitle AS normalizedTitle,
                COALESCE(o.artist, t.artist) AS artist,
                NULLIF(track_search_fts.normalizedArtist, '') AS normalizedArtist,
                COALESCE(o.album, t.album) AS album,
                t.durationMs, t.trackNumber, t.year,
                COALESCE(o.artworkRef, t.artworkRef) AS artworkRef,
                t.favorite, t.hidden, t.createdAtEpochMs, t.updatedAtEpochMs
        """.trimIndent()

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

internal fun searchEvidenceRank(normalizedQuery: String, track: Track): Int {
    val query = TextNormalizer.normalize(normalizedQuery) ?: return Int.MAX_VALUE
    val title = TextNormalizer.normalize(track.title).orEmpty()
    val artist = TextNormalizer.normalize(track.artist).orEmpty()
    val album = TextNormalizer.normalize(track.album).orEmpty()
    val tokens = query.split(' ').filter(String::isNotBlank)
    if (title == query) return 0
    if (title.startsWith(query)) return 10
    val titleHits = tokens.count { it in title }
    val artistHits = tokens.count { it in artist }
    val albumHits = tokens.count { it in album }
    val titleArtistCoverage = tokens.count { it in title || it in artist }
    if (titleHits == tokens.size) return 20
    if (titleHits > 0 && titleArtistCoverage == tokens.size) return 30 + (tokens.size - titleHits)
    if (titleHits > 0) return 40 + (tokens.size - titleHits)
    if (artistHits == tokens.size) return 60
    if (artistHits > 0) return 70 + (tokens.size - artistHits)
    if (albumHits > 0) return 90 + (tokens.size - albumHits)
    return 120
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
