package com.behradhz.meowzix.data.localmedia

data class ScannedLocalTrack(
    val mediaStoreId: Long,
    val contentUri: String,
    val title: String,
    val normalizedTitle: String,
    val artist: String?,
    val normalizedArtist: String?,
    val album: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val artworkRef: String?,
    val displayName: String?,
    val mimeType: String?,
    val fileSizeBytes: Long?,
    val relativePath: String?,
    val dateModifiedEpochSeconds: Long?,
)
