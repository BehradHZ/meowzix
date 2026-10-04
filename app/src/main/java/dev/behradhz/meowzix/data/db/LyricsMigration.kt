package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lyrics_versions` (" +
                "`id` TEXT NOT NULL, `trackId` TEXT NOT NULL, `sourceType` TEXT NOT NULL, " +
                "`sourceLabel` TEXT, `rawText` TEXT NOT NULL, `contentType` TEXT NOT NULL, " +
                "`selected` INTEGER NOT NULL, `userSelected` INTEGER NOT NULL, `userDelayMs` INTEGER NOT NULL, " +
                "`createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_versions_trackId` ON `lyrics_versions` (`trackId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_lyrics_versions_trackId_selected` " +
                "ON `lyrics_versions` (`trackId`, `selected`)",
        )
    }
}
