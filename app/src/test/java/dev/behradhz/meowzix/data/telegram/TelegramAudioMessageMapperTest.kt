package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAudioMessageMapperTest {
    @Test
    fun `accepts audio mime types`() {
        assertTrue(isSupportedAudioDocument("audio/mpeg", "anything.bin"))
        assertTrue(isSupportedAudioDocument("audio/flac", null))
    }

    @Test
    fun `accepts supported extensions when mime is missing`() {
        assertTrue(isSupportedAudioDocument(null, "track.MP3"))
        assertTrue(isSupportedAudioDocument("application/octet-stream", "track.opus"))
    }

    @Test
    fun `rejects arbitrary documents`() {
        assertFalse(isSupportedAudioDocument("application/pdf", "notes.pdf"))
        assertFalse(isSupportedAudioDocument(null, "archive.zip"))
    }
}
