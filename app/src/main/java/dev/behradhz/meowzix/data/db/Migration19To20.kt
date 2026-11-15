package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing music is migrated as already seen. */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE telegram_track_sources ADD COLUMN isUnheardNew INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN newTrackHighlightEnabled INTEGER NOT NULL DEFAULT 1")
    }
}
