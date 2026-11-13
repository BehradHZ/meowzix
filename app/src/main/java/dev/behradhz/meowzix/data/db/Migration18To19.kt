package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Previous selected chats must remain in Library after upgrading. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE telegram_selected_sources ADD COLUMN includeInLibrary INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE tracks ADD COLUMN addedToLibrary INTEGER NOT NULL DEFAULT 0")
    }
}
