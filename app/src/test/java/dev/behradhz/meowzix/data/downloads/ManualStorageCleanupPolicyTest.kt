package dev.behradhz.meowzix.data.downloads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualStorageCleanupPolicyTest {
    @Test fun completedSongsAreNeverSelectedEvenWithoutARecord() {
        assertFalse(ManualStorageCleanupPolicy.canRemove("song.mp3", false))
        assertFalse(ManualStorageCleanupPolicy.canRemove("song.flac", false))
        assertFalse(ManualStorageCleanupPolicy.canRemove("song.m4a", false))
    }

    @Test fun explicitCleanupRemovesOnlyUnprotectedIncompleteTransfers() {
        assertTrue(ManualStorageCleanupPolicy.canRemove("song.mp3.part", false))
        assertFalse(ManualStorageCleanupPolicy.canRemove("song.mp3.part", true))
    }
}
