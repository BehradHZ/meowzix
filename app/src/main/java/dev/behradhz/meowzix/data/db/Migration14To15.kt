package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `track_metadata_overrides` (`trackId` TEXT NOT NULL, `title` TEXT, `artist` TEXT, `album` TEXT, `artworkRef` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`trackId`), FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `track_merge_journal` (`id` TEXT NOT NULL, `survivorTrackId` TEXT NOT NULL, `mergedTrackId` TEXT NOT NULL, `snapshotJson` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `reversedAtEpochMs` INTEGER, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_merge_journal_survivorTrackId` ON `track_merge_journal` (`survivorTrackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_merge_journal_mergedTrackId` ON `track_merge_journal` (`mergedTrackId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `rule_playlists` (`playlistId` TEXT NOT NULL, `matchMode` TEXT NOT NULL, `rulesJson` TEXT NOT NULL, `sortMode` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`playlistId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `backup_unresolved_references` (`id` TEXT NOT NULL, `backupId` TEXT NOT NULL, `ownerType` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `portableTrackRef` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `resolvedTrackId` TEXT, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_unresolved_references_backupId` ON `backup_unresolved_references` (`backupId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_unresolved_references_ownerType_ownerId` ON `backup_unresolved_references` (`ownerType`, `ownerId`)")
    }
}
