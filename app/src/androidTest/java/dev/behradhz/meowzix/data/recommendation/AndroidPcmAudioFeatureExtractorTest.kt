package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.recommendation.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPcmAudioFeatureExtractorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun realWavDecodingProducesNormalizedAcousticFeatures() = runBlocking {
        val file = File.createTempFile("recommendation-audio", ".wav", context.cacheDir)
        try {
            val rate = 16_000
            val samples = rate
            val payload = samples * 2
            val buffer = ByteBuffer.allocate(44 + payload).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray()).putInt(36 + payload).put("WAVEfmt ".toByteArray())
            buffer.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            buffer.put("data".toByteArray()).putInt(payload)
            repeat(samples) { buffer.putShort((sin(2 * PI * 1000 * it / rate) * 0.5 * 32767).toInt().toShort()) }
            file.writeBytes(buffer.array())
            val trackId = UUID.randomUUID()
            val sourceId = UUID.randomUUID()
            val vector = AndroidPcmAudioFeatureExtractor(context).extract(trackId,
                AudioFeatureSource(sourceId, null, file.absolutePath, "audio/wav", file.length(), "test-content"))
            assertNotNull("The platform WAV extractor must yield readable PCM", vector)
            val result = requireNotNull(vector)
            assertEquals(trackId, result.trackId); assertEquals(sourceId, result.sourceIdUsed)
            assertTrue(AudioFeatureSchema.isCompatible(result.values))
            assertEquals(0.5 / kotlin.math.sqrt(2.0), result.values[0], 0.02)
            assertEquals(0.125, result.values[4], 0.025)
        } finally { file.delete() }
    }
    @Test fun unreadableSourceIsOptionalRatherThanFatal() = runBlocking {
        assertNull(AndroidPcmAudioFeatureExtractor(context).extract(UUID.randomUUID(),
            AudioFeatureSource(UUID.randomUUID(), null, "/does-not-exist", null, null, null)))
    }
}
