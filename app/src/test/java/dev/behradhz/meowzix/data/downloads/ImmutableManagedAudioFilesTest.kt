package dev.behradhz.meowzix.data.downloads

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImmutableManagedAudioFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun distinctAudioVersionsCannotOverwriteOneAnother() {
        val trackId = UUID.randomUUID()
        val first = ImmutableManagedAudioFiles.preferredDestination(
            folder.root, trackId, "song.mp3", "a".repeat(64),
        )
        first.writeText("first audio")
        val next = ImmutableManagedAudioFiles.preferredDestination(
            folder.root, trackId, "song.mp3", "b".repeat(64),
        )
        assertNotEquals(first, next)
        assertFalse(next.exists())
        assertEquals("first audio", first.readText())
    }

    @Test
    fun conflictingDestinationIsPreservedWhenChoosingAnAlternative() {
        val original = ImmutableManagedAudioFiles.preferredDestination(
            folder.root, UUID.randomUUID(), "track.flac", "f".repeat(64),
        )
        original.writeText("keep this file")
        val alternate = ImmutableManagedAudioFiles.alternateDestination(original)
        assertNotEquals(original, alternate)
        assertTrue(alternate.name.endsWith(".flac"))
        assertEquals("keep this file", original.readText())
        assertFalse(alternate.exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUntrustedContentHashes() {
        ImmutableManagedAudioFiles.preferredDestination(
            folder.root, UUID.randomUUID(), "song.mp3", "../not-a-hash",
        )
    }
}
