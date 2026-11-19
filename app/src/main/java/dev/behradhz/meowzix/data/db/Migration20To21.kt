package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing tracks remain seen; earlier Telegram unread flags carry forward. */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN isUnheardNew INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE tracks SET isUnheardNew = 1
            WHERE id IN (
                SELECT s.trackId FROM track_sources s
                JOIN telegram_track_sources tg ON tg.trackSourceId = s.id
                WHERE tg.isUnheardNew = 1
            )
            """.trimIndent(),
        )
    }
}
