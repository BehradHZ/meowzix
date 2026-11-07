package dev.behradhz.meowzix.data.downloads

import dev.behradhz.meowzix.data.db.DownloadRecordEntity
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import java.io.File
import java.util.UUID

/** A fully received playback copy is as durable as an explicit offline download. */
internal object PermanentAudioRecordFactory {
    fun completed(
        trackId: String,
        sourceId: String,
        tdFileId: Int?,
        file: File,
        timestampMs: Long,
        existing: DownloadRecordEntity?,
    ): DownloadRecordEntity {
        require(file.isFile && file.length() > 0L) { "Cannot retain incomplete or missing audio." }
        return DownloadRecordEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            trackId = trackId,
            trackSourceId = sourceId,
            tdFileId = tdFileId ?: existing?.tdFileId,
            status = DownloadStatus.COMPLETED.name,
            downloadedBytes = file.length(),
            totalBytes = file.length(),
            localPath = file.absolutePath,
            pinned = true,
            failureReason = null,
            createdAtEpochMs = existing?.createdAtEpochMs ?: timestampMs,
            updatedAtEpochMs = timestampMs,
        )
    }
}
