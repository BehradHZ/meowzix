package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_local_media_sources_contentUri` " +
                "ON `local_media_sources` (`contentUri`)",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `telegram_track_sources` (" +
                "`trackSourceId` TEXT NOT NULL, `accountId` TEXT NOT NULL, `chatId` INTEGER NOT NULL, " +
                "`messageId` INTEGER NOT NULL, `tdFileId` INTEGER, `tdPersistentFileId` TEXT, " +
                "`fileName` TEXT, `telegramTitle` TEXT, `telegramPerformer` TEXT, `remoteRevisionKey` TEXT, " +
                "PRIMARY KEY(`trackSourceId`), " +
                "FOREIGN KEY(`trackSourceId`) REFERENCES `track_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_telegram_track_sources_trackSourceId` ON `telegram_track_sources` (`trackSourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_telegram_track_sources_chatId` ON `telegram_track_sources` (`chatId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_telegram_track_sources_accountId_chatId_messageId` " +
                "ON `telegram_track_sources` (`accountId`, `chatId`, `messageId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `telegram_selected_sources` (" +
                "`accountId` TEXT NOT NULL, `chatId` INTEGER NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`newestMessageId` INTEGER, `initialScanComplete` INTEGER NOT NULL, `lastSyncedAtEpochMs` INTEGER, " +
                "PRIMARY KEY(`accountId`, `chatId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_telegram_selected_sources_accountId` ON `telegram_selected_sources` (`accountId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `download_records` (" +
                "`id` TEXT NOT NULL, `trackId` TEXT NOT NULL, `trackSourceId` TEXT NOT NULL, `tdFileId` INTEGER, " +
                "`status` TEXT NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER, `localPath` TEXT, " +
                "`pinned` INTEGER NOT NULL, `failureReason` TEXT, `createdAtEpochMs` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_download_records_trackId` ON `download_records` (`trackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_records_tdFileId` ON `download_records` (`tdFileId`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_tracks` (`playlistId` TEXT NOT NULL, `trackId` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `addedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `trackId`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` (`trackId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_position` ON `playlist_tracks` (`playlistId`, `position`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `listening_sessions` (`id` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `endedAtEpochMs` INTEGER, `initialMode` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `listening_events` (" +
                "`id` TEXT NOT NULL, `playbackInstanceId` TEXT NOT NULL, `trackId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `occurredAtEpochMs` INTEGER NOT NULL, `localHour` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, " +
                "`timeBucket` TEXT NOT NULL, `isWeekend` INTEGER NOT NULL, `positionMs` INTEGER, `durationMs` INTEGER, " +
                "`completionRatio` REAL, `initiatedBy` TEXT NOT NULL, `playbackMode` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `listening_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_trackId` ON `listening_events` (`trackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_sessionId` ON `listening_events` (`sessionId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_listening_events_playbackInstanceId_type` ON `listening_events` (`playbackInstanceId`, `type`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `track_preference_stats` (`trackId` TEXT NOT NULL, `totalStarts` INTEGER NOT NULL, `totalCompletions` INTEGER NOT NULL, `earlySkips` INTEGER NOT NULL, `lateSkips` INTEGER NOT NULL, `manualSelections` INTEGER NOT NULL, `replays` INTEGER NOT NULL, `lastPlayedAtEpochMs` INTEGER, PRIMARY KEY(`trackId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `track_time_preferences` (`trackId` TEXT NOT NULL, `timeBucket` TEXT NOT NULL, `starts` INTEGER NOT NULL, `completions` INTEGER NOT NULL, `earlySkips` INTEGER NOT NULL, `manualSelections` INTEGER NOT NULL, `lastInteractionAtEpochMs` INTEGER, PRIMARY KEY(`trackId`, `timeBucket`))")
    }
}
