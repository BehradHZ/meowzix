package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lyrics_detached_tracks` (" +
                "`trackId` TEXT NOT NULL, PRIMARY KEY(`trackId`), " +
                "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
    }
}
