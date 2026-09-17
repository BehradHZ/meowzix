package com.behradhz.meowzix.data.localmedia

interface LocalMusicScanner {
    /**
     * Returns an authoritative snapshot of readable local audio.
     *
     * Implementations MUST throw when access is unavailable. Returning an empty list because
     * permission is missing would make reconciliation indistinguishable from a genuinely empty
     * device library and could incorrectly remove persisted sources.
     */
    suspend fun scan(): List<ScannedLocalTrack>
}

class MissingAudioPermissionException : SecurityException("Audio library permission is not granted")
