package dev.behradhz.meowzix.data.repository

import androidx.room.withTransaction
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.LibraryToolsDao
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Maintains the standalone FTS row as the effective, user-visible searchable metadata.
 *
 * Track/provider metadata remains immutable here; user overrides live in track_metadata_overrides.
 * The FTS row is derived state and can be rebuilt deterministically.
 */
@Singleton
class EffectiveTrackSearchIndexer @Inject constructor(
    private val database: MeowzixDatabase,
    private val libraryDao: LibraryDao,
    private val toolsDao: LibraryToolsDao,
) {
    private val initializationMutex = Mutex()
    @Volatile private var fullyNormalized = false

    suspend fun ensureReady() {
        if (fullyNormalized) return
        initializationMutex.withLock {
            if (!fullyNormalized) {
                refreshAllInternal()
                fullyNormalized = true
            }
        }
    }

    suspend fun refreshAll() {
        initializationMutex.withLock {
            refreshAllInternal()
            fullyNormalized = true
        }
    }

    suspend fun refreshTrack(trackId: String) {
        withContext(Dispatchers.IO) {
            val track = libraryDao.trackById(trackId)
            val override = toolsDao.metadataOverride(trackId)
            database.withTransaction {
                writeEffectiveRow(track, override)
            }
        }
    }

    suspend fun refreshTracks(trackIds: Collection<String>) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            val overrides = toolsDao.allMetadataOverrides().associateBy { it.trackId }
            database.withTransaction {
                ids.forEach { id -> writeEffectiveRow(libraryDao.trackById(id), overrides[id]) }
            }
        }
    }

    private suspend fun refreshAllInternal() = withContext(Dispatchers.IO) {
        val tracks = libraryDao.allTracks()
        val overrides = toolsDao.allMetadataOverrides().associateBy { it.trackId }
        database.withTransaction {
            tracks.forEach { track -> writeEffectiveRow(track, overrides[track.id]) }
        }
    }

    private fun writeEffectiveRow(track: TrackEntity?, override: TrackMetadataOverrideEntity?) {
        val sqlite = database.openHelper.writableDatabase
        val trackId = track?.id ?: override?.trackId ?: return
        sqlite.execSQL("DELETE FROM track_search_fts WHERE trackId = ?", arrayOf(trackId))
        if (track == null) return

        val title = override?.title ?: track.title
        val artist = override?.artist ?: track.artist
        val album = override?.album ?: track.album
        val normalizedTitle = TextNormalizer.normalize(title) ?: "unknown track"
        val normalizedArtist = TextNormalizer.normalize(artist).orEmpty()
        val normalizedAlbum = TextNormalizer.normalize(album).orEmpty()

        sqlite.execSQL(
            """
            INSERT INTO track_search_fts(rowid, trackId, normalizedTitle, normalizedArtist, album)
            SELECT rowid, id, ?, ?, ? FROM tracks WHERE id = ?
            """.trimIndent(),
            arrayOf(normalizedTitle, normalizedArtist, normalizedAlbum, track.id),
        )
    }
}
