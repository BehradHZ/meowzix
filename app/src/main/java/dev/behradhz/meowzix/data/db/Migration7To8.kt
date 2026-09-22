package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Repair migration for Increment 12 development builds.
 *
 * Audio feature vectors are derived, local, and fully recomputable from readable TrackSources. A
 * few development APKs could already have user_version=7 while this table was still evolving. Room
 * cannot repair a same-version schema mismatch, so v8 deliberately rebuilds only this derived table
 * while preserving the user's canonical library, sources, playlists, downloads, Telegram state,
 * listening history, preferences, and model state.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `audio_feature_vectors`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audio_feature_vectors` (" +
                "`id` TEXT NOT NULL, `trackId` TEXT NOT NULL, `sourceIdUsed` TEXT NOT NULL, " +
                "`extractorName` TEXT NOT NULL, `extractorVersion` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, " +
                "`vectorFormat` TEXT NOT NULL, `vectorBlob` BLOB NOT NULL, `generatedAtEpochMs` INTEGER NOT NULL, " +
                "`sourceContentHash` TEXT, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_feature_vectors_trackId` ON `audio_feature_vectors` (`trackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_feature_vectors_sourceIdUsed` ON `audio_feature_vectors` (`sourceIdUsed`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_feature_vectors_trackId_extractorName_extractorVersion_schemaVersion` " +
                "ON `audio_feature_vectors` (`trackId`, `extractorName`, `extractorVersion`, `schemaVersion`)",
        )
    }
}
