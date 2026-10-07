package dev.behradhz.meowzix.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import dev.behradhz.meowzix.domain.playback.LoudnessAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainMetadataParserTest {
    @Test
    fun trackGainWinsOverAlbumGain() {
        val metadata = Metadata(
            VorbisComment("REPLAYGAIN_ALBUM_GAIN", "-4.0 dB"),
            VorbisComment("REPLAYGAIN_TRACK_GAIN", "-7.5 dB"),
        )
        val result = ReplayGainMetadataParser.parse(metadata)
        assertEquals(LoudnessAlgorithm.REPLAY_GAIN_TRACK, result?.algorithm)
        assertEquals(-7.5f, result?.suggestedGainDb ?: 0f, 0.0001f)
    }

    @Test
    fun id3TxxxReplayGainIsParsed() {
        val metadata = Metadata(
            TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", listOf("+2.25 dB")),
        )
        val result = ReplayGainMetadataParser.parse(metadata)
        assertEquals(2.25f, result?.suggestedGainDb ?: 0f, 0.0001f)
    }

    @Test
    fun unrelatedMetadataIsIgnored() {
        assertNull(ReplayGainMetadataParser.parse(Metadata(VorbisComment("ARTIST", "Meowzix"))))
    }
}
