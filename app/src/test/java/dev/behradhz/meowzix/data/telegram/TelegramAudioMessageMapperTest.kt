package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.drinkless.tdlib.TdApi

class TelegramAudioMessageMapperTest {
    @Test
    fun `document thumbnail metadata is available to artwork repair`() {
        val minithumbnailBytes = byteArrayOf(1, 2, 3, 4)
        val thumbnailFile = TdApi.File(
            42,
            0L,
            0L,
            TdApi.LocalFile(),
            TdApi.RemoteFile(),
        )
        val document = TdApi.Document(
            "track.mp3",
            "audio/mpeg",
            TdApi.Minithumbnail(40, 40, minithumbnailBytes),
            TdApi.Thumbnail(TdApi.ThumbnailFormatJpeg(), 320, 320, thumbnailFile),
            TdApi.File(),
        )

        val artwork = document.toArtworkMetadata()

        assertArrayEquals(minithumbnailBytes, artwork.minithumbnail)
        assertEquals(42, artwork.thumbnailFileId)
    }

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
