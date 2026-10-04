package dev.behradhz.meowzix.domain.playback

import java.util.UUID

/**
 * One-shot in-process bridge between playback-session decoding and ProgressiveQueue restoration.
 * PlaybackService historically restores only logical IDs. Keeping the hint here preserves v3 manual
 * provenance without changing the service/controller contract; mismatched IDs consume nothing.
 */
object QueueProvenanceRestoreHints {
    private var ids: List<UUID> = emptyList()
    private var origins: List<QueueItemOrigin> = emptyList()

    @Synchronized
    fun publish(trackIds: List<UUID>, itemOrigins: List<QueueItemOrigin>) {
        if (trackIds.isEmpty() || trackIds.size != itemOrigins.size) {
            clear()
            return
        }
        ids = trackIds.toList()
        origins = itemOrigins.toList()
    }

    @Synchronized
    fun consume(trackIds: List<UUID>): List<QueueItemOrigin>? {
        if (ids != trackIds || origins.size != trackIds.size) return null
        return origins.toList().also { clear() }
    }

    @Synchronized
    fun clear() {
        ids = emptyList()
        origins = emptyList()
    }
}
