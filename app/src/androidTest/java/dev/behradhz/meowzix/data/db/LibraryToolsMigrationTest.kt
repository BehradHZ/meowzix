package dev.behradhz.meowzix.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryToolsMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeowzixDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateFourteenToFifteenPreservesLibraryAndAddsDailyTools() {
        val db = helper.createDatabase(DB_NAME, 10)
        MIGRATION_10_11.migrate(db)
        MIGRATION_11_12.migrate(db)
        MIGRATION_12_13.migrate(db)
        MIGRATION_13_14.migrate(db)
        db.execSQL("INSERT INTO tracks (id, title, normalizedTitle, durationMs, favorite, hidden, createdAtEpochMs, updatedAtEpochMs) VALUES ('kept-v15', 'Kept', 'kept', 1000, 1, 0, 1, 1)")
        db.execSQL("INSERT INTO playlists (id, title, createdAtEpochMs, updatedAtEpochMs) VALUES ('playlist-v15', 'Rules', 1, 1)")
        db.version = 14
        db.close()

        // Validate the production 14 -> 15 migration against Room's generated schema-15
        // contract, not only against hand-picked table names.
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 15, true, MIGRATION_14_15)

        migrated.query("SELECT favorite FROM tracks WHERE id='kept-v15'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        listOf("track_metadata_overrides", "track_merge_journal", "rule_playlists", "backup_unresolved_references").forEach { table ->
            migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { assertTrue(it.moveToFirst()) }
        }
        migrated.query("PRAGMA foreign_key_list(track_metadata_overrides)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("tracks", cursor.getString(cursor.getColumnIndexOrThrow("table")))
        }
        migrated.query("PRAGMA foreign_key_list(rule_playlists)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("playlists", cursor.getString(cursor.getColumnIndexOrThrow("table")))
        }
        listOf(
            "index_track_merge_journal_survivorTrackId",
            "index_track_merge_journal_mergedTrackId",
            "index_backup_unresolved_references_backupId",
            "index_backup_unresolved_references_ownerType_ownerId",
        ).forEach { index ->
            migrated.query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(index)).use { assertTrue(it.moveToFirst()) }
        }

        migrated.execSQL(
            "INSERT INTO track_metadata_overrides(trackId, title, artist, album, artworkRef, updatedAtEpochMs) " +
                "VALUES ('kept-v15', 'Edited', NULL, NULL, NULL, 2)",
        )
        migrated.execSQL(
            "INSERT INTO track_merge_journal(id, survivorTrackId, mergedTrackId, snapshotJson, createdAtEpochMs, reversedAtEpochMs) " +
                "VALUES ('merge-v15', 'kept-v15', 'merged-v15', 'v\\t2', 3, NULL)",
        )
        migrated.execSQL(
            "INSERT INTO rule_playlists(playlistId, matchMode, rulesJson, sortMode, updatedAtEpochMs) " +
                "VALUES ('playlist-v15', 'ALL', 'v1', 'TITLE', 4)",
        )
        migrated.query("SELECT title FROM track_metadata_overrides WHERE trackId='kept-v15'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Edited", cursor.getString(0))
        }
        migrated.query("SELECT survivorTrackId FROM track_merge_journal WHERE id='merge-v15'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("kept-v15", cursor.getString(0))
        }
        migrated.query("SELECT matchMode FROM rule_playlists WHERE playlistId='playlist-v15'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ALL", cursor.getString(0))
        }
        migrated.close()
    }

    private companion object { const val DB_NAME = "migration-test-14-15" }
}
