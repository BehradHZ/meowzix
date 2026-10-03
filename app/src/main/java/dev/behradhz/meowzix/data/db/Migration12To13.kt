package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `listening_events` ADD COLUMN `outcomeKey` TEXT")
        db.execSQL("DROP INDEX `index_listening_events_playbackInstanceId_type`")
        db.execSQL("CREATE INDEX `index_listening_events_playbackInstanceId_type` ON `listening_events` (`playbackInstanceId`, `type`)")
        db.execSQL("UPDATE `listening_events` SET `outcomeKey` = `playbackInstanceId` WHERE rowid IN (SELECT MIN(rowid) FROM listening_events WHERE type IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_EARLY', 'SKIPPED_LATE', 'QUEUE_REMOVED') GROUP BY playbackInstanceId)")
        db.execSQL("CREATE UNIQUE INDEX `index_listening_events_outcomeKey` ON `listening_events` (`outcomeKey`)")
        db.execSQL("ALTER TABLE `listening_events` ADD COLUMN `eventSequence` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `listening_events` SET `eventSequence` = rowid")
        db.execSQL("CREATE UNIQUE INDEX `index_listening_events_eventSequence` ON `listening_events` (`eventSequence`)")
        db.execSQL("CREATE TABLE `recommendation_event_clock` (`id` INTEGER NOT NULL, `lastSequence` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `recommendation_event_clock` (`id`, `lastSequence`) SELECT 1, COALESCE(MAX(eventSequence), 0) FROM listening_events")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `training_samples` (
            `playbackInstanceId` TEXT NOT NULL, `trackId` TEXT NOT NULL, `features` BLOB NOT NULL,
            `reward` REAL NOT NULL, `weight` REAL NOT NULL, `dataVersion` INTEGER NOT NULL,
            `featureSchemaVersion` INTEGER NOT NULL, `rewardSchemaVersion` INTEGER NOT NULL,
            `generatedAtEpochMs` INTEGER NOT NULL, `eventIds` TEXT NOT NULL,
            `sourceIdUsedForAudio` TEXT, `audioExtractorVersion` TEXT,
            PRIMARY KEY(`playbackInstanceId`),
            FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_samples_trackId` ON `training_samples` (`trackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_samples_dataVersion` ON `training_samples` (`dataVersion`)")
    }
}
