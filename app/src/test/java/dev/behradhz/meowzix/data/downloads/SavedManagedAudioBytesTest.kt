package dev.behradhz.meowzix.data.downloads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedManagedAudioBytesTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun streamsAndOfflineAudioAreSavedWithoutCountingDuplicatesOrDeviceMusic() {
        val offline = files.newFolder("offline")
        val playback = files.newFolder("playback-cache")
        val offlineSong = write(offline, "offline.mp3", 17)
        val legacyStream = write(playback, "stream.mp3", 23)
        val unfinishedSave = write(offline, "incoming.mp3.part", 7)
        val deviceMusic = write(files.root, "device-music.mp3", 100)

        assertEquals(
            47L,
            savedManagedAudioBytes(
                offline,
                playback,
                listOf(offlineSong, legacyStream, unfinishedSave, deviceMusic, offlineSong),
            ),
        )
    }

    @Test fun emptyManagedDirectoriesNeverCountUnrelatedMusic() {
        val offline = files.newFolder("offline")
        val playback = files.newFolder("playback-cache")
        val deviceMusic = write(files.root, "device-music.mp3", 100)
        assertEquals(0L, savedManagedAudioBytes(offline, playback, listOf(deviceMusic)))
    }

    private fun write(root: File, name: String, bytes: Int): File =
        File(root, name).apply { writeBytes(ByteArray(bytes) { 1 }) }
}
