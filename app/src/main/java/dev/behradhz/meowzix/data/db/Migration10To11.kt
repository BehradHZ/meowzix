package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `telegram_send_jobs` (
                `id` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `trackId` TEXT NOT NULL,
                `targetChatId` INTEGER NOT NULL,
                `targetTitle` TEXT,
                `state` TEXT NOT NULL,
                `progressPercent` INTEGER NOT NULL,
                `attemptCount` INTEGER NOT NULL,
                `includeSourceAttribution` INTEGER NOT NULL,
                `keepCaption` INTEGER NOT NULL,
                `activeDedupeKey` TEXT,
                `sentMessageId` INTEGER,
                `errorMessage` TEXT,
                `createdAtEpochMs` INTEGER NOT NULL,
                `updatedAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_telegram_send_jobs_trackId` ON `telegram_send_jobs` (`trackId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_telegram_send_jobs_state` ON `telegram_send_jobs` (`state`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_telegram_send_jobs_activeDedupeKey` ON `telegram_send_jobs` (`activeDedupeKey`)",
        )
    }
}
