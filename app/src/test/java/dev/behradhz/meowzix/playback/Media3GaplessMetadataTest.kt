package dev.behradhz.meowzix.playback

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.GaplessInfoHolder
import androidx.media3.extractor.metadata.id3.InternalFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(markerClass = [UnstableApi::class])
class Media3GaplessMetadataTest {
    @Test
    fun iTunSmpbMetadataExposesEncoderDelayAndPadding() {
        val metadata = Metadata(
            InternalFrame(
                "com.apple.iTunes",
                "iTunSMPB",
                " 00000000 00000840 00000210 0000000000012340 00000000 00000000 00000000 00000000 00000000 00000000",
            ),
        )
        val holder = GaplessInfoHolder()

        assertTrue(holder.setFromMetadata(metadata))
        assertTrue(holder.hasGaplessInfo())
        assertEquals(0x840, holder.encoderDelay)
        assertEquals(0x210, holder.encoderPadding)
    }

    @Test
    fun unrelatedMetadataDoesNotInventGaplessTrim() {
        val metadata = Metadata(
            InternalFrame("com.apple.iTunes", "unrelated", "00000000 00000000"),
        )
        val holder = GaplessInfoHolder()

        assertFalse(holder.setFromMetadata(metadata))
        assertFalse(holder.hasGaplessInfo())
    }
}
