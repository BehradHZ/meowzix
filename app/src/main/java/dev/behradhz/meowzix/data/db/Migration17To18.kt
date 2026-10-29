package dev.behradhz.meowzix.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.behradhz.meowzix.domain.telegram.telegramPlaylistId

/** Preserve existing Telegram playlist customizations while introducing per-field sync flags. */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN telegramManaged INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN titleOverridden INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN descriptionOverridden INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN artworkOverridden INTEGER NOT NULL DEFAULT 0")

        db.query("SELECT accountId, chatId, title FROM telegram_selected_sources").use { sources ->
            while (sources.moveToNext()) {
                val id = telegramPlaylistId(sources.getString(0), sources.getLong(1)).toString()
                val sourceTitle = sources.getString(2)
                db.query(
                    "SELECT title, description, artworkRef FROM playlists WHERE id = ?",
                    arrayOf(id),
                ).use { rows ->
                    if (rows.moveToFirst()) {
                        val customizedTitle = rows.getString(0) != sourceTitle
                        val customizedDescription = rows.getString(1) != null
                        val customizedArtwork = rows.getString(2) != null
                        db.execSQL(
                            "UPDATE playlists SET telegramManaged = 1, titleOverridden = ?, descriptionOverridden = ?, artworkOverridden = ? WHERE id = ?",
                            arrayOf(
                                if (customizedTitle) 1 else 0,
                                if (customizedDescription) 1 else 0,
                                if (customizedArtwork) 1 else 0,
                                id,
                            ),
                        )
                    }
                }
            }
        }
    }
}
