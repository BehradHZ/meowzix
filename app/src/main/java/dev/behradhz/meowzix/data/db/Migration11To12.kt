package dev.behradhz.meowzix.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds only indexes backed by concrete hot query patterns and installs the auxiliary FTS search
 * index. Track remains the canonical source of truth; triggers keep FTS synchronized.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tracks_normalizedTitle_normalizedArtist_durationMs` " +
                "ON `tracks` (`normalizedTitle`, `normalizedArtist`, `durationMs`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_track_sources_contentHashSha256` " +
                "ON `track_sources` (`contentHashSha256`)",
        )
        ensureTrackSearchInfrastructure(db, backfill = true)
    }
}

val TRACK_SEARCH_DATABASE_CALLBACK = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // Fresh databases are empty when Room creates them, and migrated databases were backfilled
        // exactly once in MIGRATION_11_12. Never scan the full Track table during ordinary startup.
        ensureTrackSearchInfrastructure(db, backfill = false)
    }
}

private fun ensureTrackSearchInfrastructure(
    db: SupportSQLiteDatabase,
    backfill: Boolean,
) {
    db.execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS `track_search_fts`
        USING fts4(
            `trackId`,
            `normalizedTitle`,
            `normalizedArtist`,
            `album`,
            tokenize=unicode61,
            prefix='2,3',
            notindexed=`trackId`
        )
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `tracks_search_ai`
        AFTER INSERT ON `tracks`
        BEGIN
            INSERT INTO `track_search_fts`(`rowid`, `trackId`, `normalizedTitle`, `normalizedArtist`, `album`)
            VALUES (new.rowid, new.id, new.normalizedTitle, COALESCE(new.normalizedArtist, ''), COALESCE(new.album, ''));
        END
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `tracks_search_ad`
        AFTER DELETE ON `tracks`
        BEGIN
            DELETE FROM `track_search_fts` WHERE rowid = old.rowid;
        END
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `tracks_search_au`
        AFTER UPDATE OF `normalizedTitle`, `normalizedArtist`, `album` ON `tracks`
        BEGIN
            DELETE FROM `track_search_fts` WHERE rowid = old.rowid;
            INSERT INTO `track_search_fts`(`rowid`, `trackId`, `normalizedTitle`, `normalizedArtist`, `album`)
            VALUES (new.rowid, new.id, new.normalizedTitle, COALESCE(new.normalizedArtist, ''), COALESCE(new.album, ''));
        END
        """.trimIndent(),
    )

    if (backfill) {
        db.execSQL(
            """
            INSERT OR REPLACE INTO `track_search_fts`(`rowid`, `trackId`, `normalizedTitle`, `normalizedArtist`, `album`)
            SELECT t.rowid, t.id, t.normalizedTitle, COALESCE(t.normalizedArtist, ''), COALESCE(t.album, '')
            FROM `tracks` t
            """.trimIndent(),
        )
    }
}
