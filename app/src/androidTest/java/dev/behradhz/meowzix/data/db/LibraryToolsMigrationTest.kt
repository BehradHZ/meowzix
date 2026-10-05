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

        MIGRATION_14_15.migrate(db)
        db.version = 15

        db.query("SELECT favorite FROM tracks WHERE id='kept-v15'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        listOf("track_metadata_overrides", "track_merge_journal", "rule_playlists", "backup_unresolved_references").forEach { table ->
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { assertTrue(it.moveToFirst()) }
        }
        db.query("PRAGMA foreign_key_list(track_metadata_overrides)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("tracks", cursor.getString(cursor.getColumnIndexOrThrow("table")))
        }
        db.query("PRAGMA foreign_key_list(rule_playlists)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("playlists", cursor.getString(cursor.getColumnIndexOrThrow("table")))
        }
        listOf(
            "index_track_merge_journal_survivorTrackId",
            "index_track_merge_journal_mergedTrackId",
            "index_backup_unresolved_references_backupId",
            "index_backup_unresolved_references_ownerType_ownerId",
        ).forEach { index ->
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(index)).use { assertTrue(it.moveToFirst()) }
        }
        db.close()
    }

    private companion object { const val DB_NAME = "migration-test-14-15" }
}
