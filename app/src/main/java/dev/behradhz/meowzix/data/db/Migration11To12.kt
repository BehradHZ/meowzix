package dev.behradhz.meowzix.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * FTS is intentionally an auxiliary table rather than a canonical entity. Track remains the source
 * of truth; triggers keep the index synchronized and make the search structure rebuildable.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureTrackSearchInfrastructure(db)
    }
}

val TRACK_SEARCH_DATABASE_CALLBACK = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        ensureTrackSearchInfrastructure(db)
    }
}

private fun ensureTrackSearchInfrastructure(db: SupportSQLiteDatabase) {
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

    // Handles both migration backfill and a fresh DB where the callback is installed after tables
    // are created. rowid is stable for a Track row and avoids an expensive text-key delete path.
    db.execSQL(
        """
        INSERT OR REPLACE INTO `track_search_fts`(`rowid`, `trackId`, `normalizedTitle`, `normalizedArtist`, `album`)
        SELECT t.rowid, t.id, t.normalizedTitle, COALESCE(t.normalizedArtist, ''), COALESCE(t.album, '')
        FROM `tracks` t
        WHERE NOT EXISTS (
            SELECT 1 FROM `track_search_fts` f WHERE f.rowid = t.rowid
        )
        """.trimIndent(),
    )
}
