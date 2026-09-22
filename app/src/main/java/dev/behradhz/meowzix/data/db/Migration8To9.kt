package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds query-path indexes used by large libraries without rewriting user data. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tracks_hidden_normalizedTitle` " +
                "ON `tracks` (`hidden`, `normalizedTitle`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tracks_normalizedArtist` " +
                "ON `tracks` (`normalizedArtist`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tracks_favorite` ON `tracks` (`favorite`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_track_sources_trackId_availability` " +
                "ON `track_sources` (`trackId`, `availability`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_track_sources_type_availability_trackId` " +
                "ON `track_sources` (`type`, `availability`, `trackId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_download_records_updatedAtEpochMs` " +
                "ON `download_records` (`updatedAtEpochMs`)",
        )
    }
}
