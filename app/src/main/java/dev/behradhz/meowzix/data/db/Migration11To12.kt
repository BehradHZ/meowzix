package dev.behradhz.meowzix.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds only indexes backed by concrete hot query patterns and installs the Room-declared FTS search
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
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_listening_events_occurredAtEpochMs` " +
                "ON `listening_events` (`occurredAtEpochMs`)",
        )
        ensureTrackSearchInfrastructure(db, backfill = true)
    }
}

val TRACK_SEARCH_DATABASE_CALLBACK = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // Room creates the declared FTS table for fresh databases. Migrated databases are backfilled
        // exactly once in MIGRATION_11_12. The callback only makes the sync triggers idempotent and
        // keeps recovery safe if an older development database is missing the auxiliary table.
        ensureTrackSearchInfrastructure(db, backfill = false)
    }
}

private fun ensureTrackSearchInfrastructure(
    db: SupportSQLiteDatabase,
    backfill: Boolean,
) {
    // Keep this DDL aligned with TrackSearchFtsEntity. Room validates FTS columns/options as part of
    // the v12 schema contract, so the migration must create the same virtual-table definition.
    db.execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS `track_search_fts`
        USING FTS4(
            `trackId` TEXT NOT NULL,
            `normalizedTitle` TEXT NOT NULL,
            `normalizedArtist` TEXT NOT NULL,
            `album` TEXT NOT NULL,
            tokenize=unicode61,
            notindexed=`trackId`,
            prefix=`2,3`
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
