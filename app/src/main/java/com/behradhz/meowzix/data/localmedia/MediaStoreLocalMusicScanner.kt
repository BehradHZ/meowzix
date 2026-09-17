package com.behradhz.meowzix.data.localmedia

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.behradhz.meowzix.core.common.MetadataNormalizer
import com.behradhz.meowzix.core.permissions.AudioPermission
import com.behradhz.meowzix.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaStoreLocalMusicScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LocalMusicScanner {

    override suspend fun scan(): List<ScannedLocalTrack> = withContext(ioDispatcher) {
        if (!AudioPermission.isGranted(context)) {
            throw@withContext MissingAudioPermissionException()
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val items = mutableListOf<ScannedLocalTrack>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.DURATION} > ?",
            arrayOf("0"),
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getStringOrNull(displayNameColumn)
                val title = MetadataNormalizer.title(
                    cursor.getStringOrNull(titleColumn),
                    displayName,
                )
                val artist = MetadataNormalizer.display(cursor.getStringOrNull(artistColumn))
                val albumId = cursor.getLongOrNull(albumIdColumn)
                val contentUri = ContentUris.withAppendedId(collection, id).toString()

                items += ScannedLocalTrack(
                    mediaStoreId = id,
                    contentUri = contentUri,
                    title = title,
                    normalizedTitle = MetadataNormalizer.comparison(title).orEmpty(),
                    artist = artist,
                    normalizedArtist = MetadataNormalizer.comparison(artist),
                    album = MetadataNormalizer.display(cursor.getStringOrNull(albumColumn)),
                    durationMs = cursor.getLong(durationColumn),
                    trackNumber = normalizeTrackNumber(cursor.getIntOrNull(trackColumn)),
                    year = cursor.getIntOrNull(yearColumn)?.takeIf { it > 0 },
                    artworkRef = albumId
                        ?.takeIf { it > 0L }
                        ?.let { ContentUris.withAppendedId(ALBUM_ART_URI, it).toString() },
                    displayName = displayName,
                    mimeType = cursor.getStringOrNull(mimeTypeColumn),
                    fileSizeBytes = cursor.getLongOrNull(sizeColumn)?.takeIf { it >= 0L },
                    relativePath = relativePathColumn
                        .takeIf { it >= 0 }
                        ?.let(cursor::getStringOrNull),
                    dateModifiedEpochSeconds = cursor.getLongOrNull(modifiedColumn),
                )
            }
        }

        items
    }

    private fun normalizeTrackNumber(raw: Int?): Int? {
        val value = raw?.takeIf { it > 0 } ?: return null
        if (value < 1_000) return value
        return (value % 1_000).takeIf { it > 0 }
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? =
        if (isNull(columnIndex)) null else getString(columnIndex)

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? =
        if (isNull(columnIndex)) null else getLong(columnIndex)

    private fun android.database.Cursor.getIntOrNull(columnIndex: Int): Int? =
        if (isNull(columnIndex)) null else getInt(columnIndex)

    private companion object {
        val ALBUM_ART_URI = android.net.Uri.parse("content://media/external/audio/albumart")
    }
}
