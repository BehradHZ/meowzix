package dev.behradhz.meowzix.data.localmedia

data class ScannedLocalTrack(
    val mediaStoreId: Long,
    val contentUri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val artworkRef: String?,
    val mimeType: String?,
    val fileSizeBytes: Long?,
    val relativePath: String?,
    val displayName: String?,
    val dateModifiedSeconds: Long?,
)
