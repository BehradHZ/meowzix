package dev.behradhz.meowzix.data.db

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.recommendation.reduceRecommendationCandidateIds
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.domain.recommendation.ScoreBreakdown
import dev.behradhz.meowzix.domain.recommendation.SmartSelector
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Product-scale regression fixture from the system spec: 10k tracks, 1k artists, 100 playlists,
 * and 100k listening events. Budgets are intentionally generous for shared CI emulators; the main
 * regression protection is that hot paths stay indexed/bounded instead of materializing or sorting
 * the complete dataset.
 */
@RunWith(AndroidJUnit4::class)
class ScaleTest {
    private lateinit var database: MeowzixDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java)
            .addCallback(TRACK_SEARCH_DATABASE_CALLBACK)
            .build()
        seedScaleDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun syntheticScaleDatabaseKeepsSearchHistoryAndScoringBounded() = runBlocking {
        val sql = database.openHelper.writableDatabase
        assertEquals(TRACK_COUNT, scalarInt(sql, "SELECT COUNT(*) FROM tracks"))
        assertEquals(ARTIST_COUNT, scalarInt(sql, "SELECT COUNT(DISTINCT normalizedArtist) FROM tracks"))
        assertEquals(PLAYLIST_COUNT, scalarInt(sql, "SELECT COUNT(*) FROM playlists"))
        assertEquals(EVENT_COUNT, scalarInt(sql, "SELECT COUNT(*) FROM listening_events"))

        val historyPlan = buildString {
            sql.query(
                "EXPLAIN QUERY PLAN " +
                    "SELECT * FROM listening_events " +
                    "WHERE type IN ('PLAY_STARTED', 'SKIPPED_EARLY') " +
                    "ORDER BY occurredAtEpochMs DESC LIMIT 100",
            ).use { cursor ->
                val detailIndex = cursor.getColumnIndexOrThrow("detail")
                while (cursor.moveToNext()) append(cursor.getString(detailIndex)).append('\n')
            }
        }
        assertTrue(
            "Recent-history query stopped using the chronology index:\n$historyPlan",
            historyPlan.contains("index_listening_events_occurredAtEpochMs"),
        )

        val library = LibraryQueryRepository(
            database.libraryBrowseDao(),
            database.libraryDao(),
            dev.behradhz.meowzix.data.repository.EffectiveTrackSearchIndexer(
                database,
                database.libraryDao(),
                database.libraryToolsDao(),
            ),
        )
        library.search("needle", limit = 20) // warm statement/cache paths before timing
        val searchMs = measureMs {
            val results = library.search("needle", limit = 20)
            assertTrue(results.any { it.id == trackId(NEEDLE_TRACK_INDEX) })
        }
        assertTrue("10k-track FTS search took ${searchMs}ms", searchMs < QUERY_BUDGET_MS)

        database.historyDao().recentSelectionEvents(100) // warm
        val historyMs = measureMs {
            val recent = database.historyDao().recentSelectionEvents(100)
            assertEquals(100, recent.size)
        }
        assertTrue("100k-event recent-history query took ${historyMs}ms", historyMs < QUERY_BUDGET_MS)

        database.historyDao().observeRecentDisplayEvents(500).first() // warm
        val historyDisplayMs = measureMs {
            val recent = database.historyDao().observeRecentDisplayEvents(500).first()
            assertEquals(500, recent.size)
        }
        assertTrue("100k-event UI history projection took ${historyDisplayMs}ms", historyDisplayMs < QUERY_BUDGET_MS)

        database.libraryBrowseDao().browseTracks(limit = 100, offset = 9_900) // warm
        val browseMs = measureMs {
            val page = database.libraryBrowseDao().browseTracks(limit = 100, offset = 9_900)
            assertEquals(100, page.size)
        }
        assertTrue("10k-track bounded browse page took ${browseMs}ms", browseMs < QUERY_BUDGET_MS)

        database.playlistDao().browsePlaylists(limit = 100, offset = 0) // warm
        val playlistBrowseMs = measureMs {
            val playlists = database.playlistDao().browsePlaylists(limit = 100, offset = 0)
            assertEquals(PLAYLIST_COUNT, playlists.size)
        }
        assertTrue("100-playlist browse page took ${playlistBrowseMs}ms", playlistBrowseMs < QUERY_BUDGET_MS)

        Log.i(
            TAG,
            "scale-post searchMs=$searchMs historyMs=$historyMs historyDisplayMs=$historyDisplayMs " +
                "browseMs=$browseMs playlistBrowseMs=$playlistBrowseMs",
        )

        val allIds = (0 until TRACK_COUNT).map(::trackId)
        lateinit var reduced: List<UUID>
        val reduceMs = measureMs {
            reduced = reduceRecommendationCandidateIds(
                allowedTrackIds = allIds,
                priorityTrackIds = allIds.take(200),
                limit = RECOMMENDATION_CANDIDATE_LIMIT,
                seed = 42L,
            )
        }
        assertEquals(RECOMMENDATION_CANDIDATE_LIMIT, reduced.size)
        assertTrue("10k-track candidate reduction took ${reduceMs}ms", reduceMs < CPU_BUDGET_MS)

        val scores = reduced.associateWith { id ->
            val total = ((id.leastSignificantBits ushr 1) % 10_000L).toDouble() / 10_000.0
            ScoreBreakdown(
                total = total,
                globalAffinity = total,
                timeAffinity = total,
                exploration = 0.0,
                recencyPenalty = 0.0,
                artistPenalty = 0.0,
                sessionSkipPenalty = 0.0,
            )
        }
        val scoringMs = measureMs {
            assertEquals(reduced.size, SmartSelector.order(scores, seed = 42L).size)
        }
        assertTrue("800-candidate SmartSelector ordering took ${scoringMs}ms", scoringMs < CPU_BUDGET_MS)
    }

    private fun seedScaleDatabase() {
        val sql = database.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            val track = sql.compileStatement(
                """
                INSERT INTO tracks(
                    id, title, normalizedTitle, artist, normalizedArtist, album, durationMs,
                    trackNumber, year, artworkRef, favorite, hidden, createdAtEpochMs, updatedAtEpochMs
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, 0, 0, ?, ?)
                """.trimIndent(),
            )
            val source = sql.compileStatement(
                """
                INSERT INTO track_sources(
                    id, trackId, type, availability, contentUri, localPath, mimeType, fileSizeBytes,
                    contentHashSha256, trainingEligible, createdAtEpochMs, lastVerifiedAtEpochMs
                ) VALUES (?, ?, 'LOCAL_MEDIASTORE', 'AVAILABLE_LOCAL', ?, NULL, 'audio/mpeg',
                          1000000, NULL, 1, ?, ?)
                """.trimIndent(),
            )
            repeat(TRACK_COUNT) { index ->
                val id = trackId(index).toString()
                val title = if (index == NEEDLE_TRACK_INDEX) "Needle Track $index" else "Scale Track $index"
                val normalizedTitle = title.lowercase()
                val artist = "Artist ${index % ARTIST_COUNT}"
                val normalizedArtist = artist.lowercase()
                val now = index.toLong()

                track.clearBindings()
                track.bindString(1, id)
                track.bindString(2, title)
                track.bindString(3, normalizedTitle)
                track.bindString(4, artist)
                track.bindString(5, normalizedArtist)
                track.bindString(6, "Album ${index % 500}")
                track.bindLong(7, 180_000L + (index % 240) * 1_000L)
                track.bindLong(8, now)
                track.bindLong(9, now)
                track.executeInsert()

                source.clearBindings()
                source.bindString(1, "source-$index")
                source.bindString(2, id)
                source.bindString(3, "content://scale/$index")
                source.bindLong(4, now)
                source.bindLong(5, now)
                source.executeInsert()
            }

            val playlist = sql.compileStatement(
                """
                INSERT INTO playlists(id, title, description, artworkRef, createdAtEpochMs, updatedAtEpochMs)
                VALUES (?, ?, NULL, NULL, ?, ?)
                """.trimIndent(),
            )
            repeat(PLAYLIST_COUNT) { index ->
                playlist.clearBindings()
                playlist.bindString(1, "playlist-$index")
                playlist.bindString(2, "Scale Playlist $index")
                playlist.bindLong(3, index.toLong())
                playlist.bindLong(4, index.toLong())
                playlist.executeInsert()
            }

            sql.execSQL(
                "INSERT INTO listening_sessions(id, startedAtEpochMs, endedAtEpochMs, initialMode) " +
                    "VALUES ('scale-session', 0, NULL, 'ORDERED')",
            )
            val event = sql.compileStatement(
                """
                INSERT INTO listening_events(
                    id, playbackInstanceId, trackId, sessionId, type, occurredAtEpochMs,
                    localHour, dayOfWeek, timeBucket, isWeekend, positionMs, durationMs,
                    completionRatio, initiatedBy, playbackMode, eventSequence
                ) VALUES (?, ?, ?, 'scale-session', ?, ?, 12, 2, 'AFTERNOON', 0,
                          NULL, 180000, NULL, 'AUTOPLAY', 'ORDERED', ?)
                """.trimIndent(),
            )
            repeat(EVENT_COUNT) { index ->
                event.clearBindings()
                event.bindString(1, "event-$index")
                event.bindString(2, "playback-$index")
                event.bindString(3, trackId(index % TRACK_COUNT).toString())
                event.bindString(4, EVENT_TYPES[index % EVENT_TYPES.size])
                event.bindLong(5, index.toLong())
                event.bindLong(6, index.toLong() + 1L)
                event.executeInsert()
            }

            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
    }

    private fun scalarInt(sql: androidx.sqlite.db.SupportSQLiteDatabase, query: String): Int =
        sql.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun trackId(index: Int): UUID = UUID(0L, index.toLong() + 1L)

    private suspend fun measureMs(block: suspend () -> Unit): Long {
        val start = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L
    }

    private companion object {
        const val TAG = "MeowzixScaleTest"
        const val TRACK_COUNT = 10_000
        const val ARTIST_COUNT = 1_000
        const val PLAYLIST_COUNT = 100
        const val EVENT_COUNT = 100_000
        const val NEEDLE_TRACK_INDEX = 7_777
        const val RECOMMENDATION_CANDIDATE_LIMIT = 800
        const val QUERY_BUDGET_MS = 1_500L
        const val CPU_BUDGET_MS = 1_500L
        val EVENT_TYPES = arrayOf("PLAY_STARTED", "SKIPPED_EARLY", "PLAY_COMPLETED", "MANUAL_SELECTED")
    }
}
