package dev.behradhz.meowzix.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.LibraryTrackRow
import dev.behradhz.meowzix.domain.library.LibraryTrack
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LibraryAvailabilityFilter { ALL, OFFLINE, CLOUD }

/**
 * True database-backed paging for the main Tracks surface.
 *
 * The query projects only list fields and two availability booleans. It never materializes every
 * TrackSource just to decide whether a row is local/cloud, and sort/filter work stays in SQLite.
 */
class PagedLibraryTracks @Inject constructor(
    private val dao: LibraryDao,
) {
    fun flow(
        sortMode: LibrarySortMode,
        availability: LibraryAvailabilityFilter,
        favoritesOnly: Boolean = false,
        pageSize: Int = 80,
    ): Flow<PagingData<LibraryTrack>> = Pager(
        config = PagingConfig(
            pageSize = pageSize,
            initialLoadSize = pageSize * 2,
            prefetchDistance = pageSize / 2,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            dao.pagingLibraryRows(buildQuery(sortMode, availability, favoritesOnly))
        },
    ).flow.map { data -> data.map(LibraryTrackRow::toDomain) }

    fun count(): Flow<Int> = dao.observeAvailableTrackCount()

    private fun buildQuery(
        sortMode: LibrarySortMode,
        availability: LibraryAvailabilityFilter,
        favoritesOnly: Boolean,
    ): SimpleSQLiteQuery {
        val offlineExists = """
            EXISTS (
                SELECT 1 FROM track_sources offline
                WHERE offline.trackId = t.id
                  AND offline.availability = 'AVAILABLE_LOCAL'
            )
        """.trimIndent()
        val selectedCloudExists = """
            EXISTS (
                SELECT 1
                FROM telegram_track_sources tg
                INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                INNER JOIN telegram_selected_sources selected
                    ON selected.accountId = tg.accountId
                   AND selected.chatId = tg.chatId
                WHERE origin.trackId = t.id
                  AND origin.availability != 'MISSING'
            )
        """.trimIndent()
        val availableExists = "($offlineExists OR $selectedCloudExists)"

        val availabilityClause = when (availability) {
            LibraryAvailabilityFilter.ALL -> availableExists
            LibraryAvailabilityFilter.OFFLINE -> offlineExists
            LibraryAvailabilityFilter.CLOUD -> "(NOT $offlineExists AND $selectedCloudExists)"
        }
        val favoriteClause = if (favoritesOnly) "AND t.favorite = 1" else ""
        val orderBy = when (sortMode) {
            LibrarySortMode.RECENTLY_ADDED -> "t.createdAtEpochMs DESC, t.id ASC"
            LibrarySortMode.OLDEST_ADDED -> "t.createdAtEpochMs ASC, t.id ASC"
            LibrarySortMode.TITLE_ASC -> "t.normalizedTitle ASC, t.id ASC"
            LibrarySortMode.TITLE_DESC -> "t.normalizedTitle DESC, t.id ASC"
            LibrarySortMode.ARTIST_ASC -> "COALESCE(t.normalizedArtist, '') ASC, t.normalizedTitle ASC, t.id ASC"
        }

        return SimpleSQLiteQuery(
            """
            SELECT
                t.id,
                t.title,
                t.normalizedTitle,
                t.artist,
                t.normalizedArtist,
                t.album,
                t.durationMs,
                t.trackNumber,
                t.year,
                t.artworkRef,
                t.favorite,
                t.createdAtEpochMs,
                t.updatedAtEpochMs,
                CASE WHEN $offlineExists THEN 1 ELSE 0 END AS hasOfflineSource,
                CASE WHEN $selectedCloudExists THEN 1 ELSE 0 END AS hasCloudSource
            FROM tracks t
            WHERE t.hidden = 0
              AND $availabilityClause
              $favoriteClause
            ORDER BY $orderBy
            """.trimIndent(),
        )
    }
}

private fun LibraryTrackRow.toDomain(): LibraryTrack = LibraryTrack(
    track = Track(
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
        hidden = false,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    ),
    availability = when {
        hasOfflineSource -> LibraryTrackAvailability.OFFLINE
        hasCloudSource -> LibraryTrackAvailability.CLOUD
        else -> LibraryTrackAvailability.UNAVAILABLE
    },
)
