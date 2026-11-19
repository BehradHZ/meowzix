package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 3 adds only nullable coarse context snapshots. Existing history is untouched;
 * null must NOT be interpreted as speaker/online or other fabricated past context.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE listening_events ADD COLUMN outputClass TEXT")
        db.execSQL("ALTER TABLE listening_events ADD COLUMN offlineOnly INTEGER")
    }
}
