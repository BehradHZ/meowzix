package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Both branches shipped distinct v22 schemas. Reconcile either without losing
 * listening history or pending Telegram sends; already-present columns stay intact.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfMissing(db, "listening_events", "outputClass", "TEXT")
        addColumnIfMissing(db, "listening_events", "offlineOnly", "INTEGER")
        addColumnIfMissing(db, "telegram_send_jobs", "timestampSeconds", "INTEGER")
    }

    private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, type: String) {
        val exists = db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                    break
                }
            }
            found
        }
        if (!exists) db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $type")
    }
}
