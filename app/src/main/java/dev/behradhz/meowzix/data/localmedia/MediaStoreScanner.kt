package dev.behradhz.meowzix.data.localmedia

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface LocalMediaScanner {
    suspend fun scan(): List<ScannedLocalTrack>
}

@Singleton
class MediaStoreScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocalMediaScanner {
    override suspend fun scan(): List<ScannedLocalTrack> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()

        val result = mutableListOf<ScannedLocalTrack>()
        val cursor = context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: error("MediaStore audio query returned no cursor")

        cursor.use {
            fun index(column: String) = cursor.getColumnIndex(column)
            fun string(column: String): String? = index(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
            fun long(column: String): Long? = index(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            fun int(column: String): Int? = index(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getInt)

            while (it.moveToNext()) {
                val id = long(MediaStore.Audio.Media._ID) ?: continue
                val duration = long(MediaStore.Audio.Media.DURATION) ?: 0L
                if (duration <= 0L) continue
                val title = string(MediaStore.Audio.Media.TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: string(MediaStore.Audio.Media.DISPLAY_NAME)
                    ?.substringBeforeLast('.')
                    ?.takeIf { it.isNotBlank() }
                    ?: "Unknown Track"
                val albumId = long(MediaStore.Audio.Media.ALBUM_ID)
                result += ScannedLocalTrack(
                    mediaStoreId = id,
                    contentUri = ContentUris.withAppendedId(collection, id).toString(),
                    title = title,
                    artist = string(MediaStore.Audio.Media.ARTIST)?.takeUnless { it == "<unknown>" },
                    album = string(MediaStore.Audio.Media.ALBUM)?.takeUnless { it == "<unknown>" },
                    durationMs = duration,
                    trackNumber = int(MediaStore.Audio.Media.TRACK),
                    year = int(MediaStore.Audio.Media.YEAR),
                    artworkRef = albumId?.let { "content://media/external/audio/albumart/$it" },
                    mimeType = string(MediaStore.Audio.Media.MIME_TYPE),
                    fileSizeBytes = long(MediaStore.Audio.Media.SIZE),
                    relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) string(MediaStore.Audio.Media.RELATIVE_PATH) else null,
                    displayName = string(MediaStore.Audio.Media.DISPLAY_NAME),
                    dateModifiedSeconds = long(MediaStore.Audio.Media.DATE_MODIFIED),
                )
            }
        }
        result
    }
}
