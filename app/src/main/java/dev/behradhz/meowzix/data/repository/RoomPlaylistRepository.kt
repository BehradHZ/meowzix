package dev.behradhz.meowzix.data.repository

import androidx.room.withTransaction
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.PlaylistDao
import dev.behradhz.meowzix.data.db.PlaylistEntity
import dev.behradhz.meowzix.data.db.PlaylistTrackEntity
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.domain.library.LOCAL_PLAYLIST_ID
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.telegram.telegramPlaylistId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class RoomPlaylistRepository @Inject constructor(
    private val database: MeowzixDatabase,
    private val dao: PlaylistDao,
) : PlaylistRepository {
    override fun observePlaylists(): Flow<List<PlaylistSummary>> = dao.observePlaylists().map { rows ->
        rows.map { row ->
            PlaylistSummary(
                id = UUID.fromString(row.id),
                title = row.title,
                trackCount = row.trackCount,
                description = row.description,
                artworkRef = row.artworkRef,
                updatedAt = Instant.ofEpochMilli(row.updatedAtEpochMs),
                telegramManaged = row.telegramManaged,
                titleOverridden = row.titleOverridden,
                descriptionOverridden = row.descriptionOverridden,
                artworkOverridden = row.artworkOverridden,
                newTrackHighlightEnabled = row.newTrackHighlightEnabled,
            )
        }
    }

    override fun observeUnheardNewTracks(): Flow<Map<UUID, Set<UUID>>> =
        combine(
            database.telegramDao().observeUnheardTrackRows(),
            database.libraryDao().observeUnheardTrackIds(),
        ) { telegramRows, localUnheard ->
            val byPlaylist = telegramRows.groupBy { telegramPlaylistId(it.accountId, it.chatId) }
                .mapValues { (_, rows) -> rows.mapTo(mutableSetOf()) { UUID.fromString(it.trackId) } }
                .toMutableMap()
            if (localUnheard.isNotEmpty()) {
                byPlaylist[LOCAL_PLAYLIST_ID] = localUnheard.mapTo(mutableSetOf(), UUID::fromString)
            }
            byPlaylist
        }

    override suspend fun setNewTrackHighlightEnabled(playlistId: UUID, enabled: Boolean) =
        dao.setNewTrackHighlightEnabled(playlistId.toString(), enabled)

    override suspend fun markNewTrackSeen(trackId: UUID) {
        database.withTransaction {
            database.libraryDao().markNewTrackPlayed(trackId.toString())
            database.telegramDao().markTrackSeen(trackId.toString())
        }
    }

    override fun observeTracks(playlistId: UUID): Flow<List<Track>> =
        dao.observeTracks(playlistId.toString()).map { rows -> rows.map(TrackEntity::toDomain) }

    override suspend fun create(title: String, description: String?, artworkRef: String?): UUID {
        val normalized = title.trim().takeIf(String::isNotEmpty) ?: "New playlist"
        val now = Instant.now().toEpochMilli()
        val id = UUID.randomUUID()
        dao.upsertPlaylist(
            PlaylistEntity(
                id = id.toString(),
                title = normalized,
                description = description?.trim()?.takeIf(String::isNotEmpty),
                artworkRef = artworkRef?.trim()?.takeIf(String::isNotEmpty),
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        return id
    }

    override suspend fun rename(playlistId: UUID, title: String) {
        val normalized = title.trim().takeIf(String::isNotEmpty) ?: return
        dao.renamePlaylist(
            playlistId = playlistId.toString(),
            title = normalized,
            updatedAt = Instant.now().toEpochMilli(),
        )
    }

    override suspend fun updateMetadata(
        playlistId: UUID,
        title: String,
        description: String?,
        artworkRef: String?,
    ) {
        val normalizedTitle = title.trim().takeIf(String::isNotEmpty) ?: return
        val normalizedDescription = description?.trim()?.takeIf(String::isNotEmpty)
        val normalizedArtwork = artworkRef?.trim()?.takeIf(String::isNotEmpty)
        database.withTransaction {
            val existing = dao.playlist(playlistId.toString()) ?: return@withTransaction
            if (existing.telegramManaged) {
                dao.updateCustomizedMetadata(
                    playlistId = playlistId.toString(),
                    title = normalizedTitle,
                    description = normalizedDescription,
                    artworkRef = normalizedArtwork,
                    titleOverride = existing.titleOverridden || normalizedTitle != existing.title,
                    descriptionOverride = existing.descriptionOverridden || normalizedDescription != existing.description,
                    // Clearing an explicitly selected cover returns to the live Telegram photo.
                    artworkOverride = normalizedArtwork != null,
                    updatedAt = Instant.now().toEpochMilli(),
                )
            } else {
                dao.updatePlaylistMetadata(
                    playlistId = playlistId.toString(),
                    title = normalizedTitle,
                    description = normalizedDescription,
                    artworkRef = normalizedArtwork,
                    updatedAt = Instant.now().toEpochMilli(),
                )
            }
        }
    }

    override suspend fun restoreTelegramMetadata(playlistId: UUID, sourceTitle: String) {
        database.withTransaction {
            dao.restoreTelegramMetadata(playlistId.toString(), sourceTitle, Instant.now().toEpochMilli())
        }
    }

    override suspend fun delete(playlistId: UUID) = dao.deletePlaylist(playlistId.toString())

    override suspend fun addTrack(playlistId: UUID, trackId: UUID) {
        val playlistKey = playlistId.toString()
        val inserted = dao.insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistKey,
                trackId = trackId.toString(),
                position = dao.nextPosition(playlistKey),
                addedAtEpochMs = Instant.now().toEpochMilli(),
            ),
        )
        if (inserted != -1L) {
            dao.touchPlaylist(playlistId.toString(), Instant.now().toEpochMilli())
        }
    }

    override suspend fun removeTrack(playlistId: UUID, trackId: UUID) = database.withTransaction {
        dao.removeTrack(playlistId.toString(), trackId.toString())
        rewrite(playlistId, dao.entries(playlistId.toString()).map { UUID.fromString(it.trackId) })
    }

    override suspend fun moveTrack(playlistId: UUID, fromIndex: Int, toIndex: Int) = database.withTransaction {
        val ids = dao.entries(playlistId.toString()).map { UUID.fromString(it.trackId) }.toMutableList()
        if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return@withTransaction
        ids.add(toIndex, ids.removeAt(fromIndex))
        rewrite(playlistId, ids)
    }

    override suspend fun replaceTracks(playlistId: UUID, trackIds: List<UUID>) = database.withTransaction {
        rewrite(playlistId, trackIds.distinct())
    }

    private suspend fun rewrite(playlistId: UUID, trackIds: List<UUID>) {
        dao.clearTracks(playlistId.toString())
        val now = Instant.now().toEpochMilli()
        dao.upsertTracks(
            trackIds.mapIndexed { index, trackId ->
                PlaylistTrackEntity(playlistId.toString(), trackId.toString(), index, now)
            },
        )
        dao.touchPlaylist(playlistId.toString(), now)
    }
}

