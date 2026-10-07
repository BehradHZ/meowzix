package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `loudness_analyses` (
                `id` TEXT NOT NULL,
                `trackId` TEXT NOT NULL,
                `sourceIdUsed` TEXT,
                `analysisVersion` INTEGER NOT NULL,
                `algorithm` TEXT NOT NULL,
                `algorithmVersion` TEXT NOT NULL,
                `measuredValueDb` REAL,
                `suggestedGainDb` REAL NOT NULL,
                `confidence` REAL NOT NULL,
                `sourceContentHash` TEXT,
                `sourceProvenance` TEXT NOT NULL,
                `generatedAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loudness_analyses_trackId` ON `loudness_analyses` (`trackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loudness_analyses_sourceIdUsed` ON `loudness_analyses` (`sourceIdUsed`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loudness_analyses_sourceContentHash` ON `loudness_analyses` (`sourceContentHash`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loudness_analyses_trackId_analysisVersion_algorithmVersion` ON `loudness_analyses` (`trackId`, `analysisVersion`, `algorithmVersion`)")
    }
}
