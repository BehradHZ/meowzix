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
