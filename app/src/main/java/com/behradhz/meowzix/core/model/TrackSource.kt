package com.behradhz.meowzix.core.model

import java.time.Instant
import java.util.UUID

data class TrackSource(
    val id: UUID,
    val trackId: UUID,
    val type: TrackSourceType,
    val availability: SourceAvailability,
    val contentUri: String?,
    val localPath: String?,
    val mimeType: String?,
    val fileSizeBytes: Long?,
    val contentHashSha256: String?,
    val trainingEligible: Boolean,
    val createdAt: Instant,
    val lastVerifiedAt: Instant?,
)

enum class TrackSourceType {
    LOCAL_MEDIASTORE,
    TELEGRAM_REMOTE,
    TDLIB_LOCAL,
    APP_OFFLINE_COPY,
}

enum class SourceAvailability {
    AVAILABLE_LOCAL,
    REMOTE_ONLY,
    DOWNLOADING,
    PARTIAL,
    MISSING,
    ERROR,
}
