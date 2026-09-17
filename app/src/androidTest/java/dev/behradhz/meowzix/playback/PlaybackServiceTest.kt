package dev.behradhz.meowzix.playback

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import kotlin.math.PI
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var controller: MediaController
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        controllerFuture = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync()
        controller = controllerFuture.get(10, TimeUnit.SECONDS)
        onMain {
            controller.stop()
            controller.clearMediaItems()
        }
    }

    @After
    fun tearDown() {
        onMain {
            controller.stop()
            controller.clearMediaItems()
        }
        onMain { MediaController.releaseFuture(controllerFuture) }
        files.forEach(File::delete)
    }

    @Test
    fun playPauseSeekNextAndNotificationWorkThroughMediaSession() {
        val first = playableItem("First", createWaveFile("first.wav", 4_000))
        val second = playableItem("Second", createWaveFile("second.wav", 4_000))

        onMain {
            controller.setMediaItems(listOf(first, second))
            controller.prepare()
            controller.play()
        }
        waitUntil { onMain { controller.isPlaying } }
        waitUntil {
            context.getSystemService(NotificationManager::class.java)
                .activeNotifications
                .any { it.packageName == context.packageName }
        }

        onMain {
            controller.pause()
            controller.seekTo(1_500)
        }
        waitUntil { onMain { !controller.isPlaying && controller.currentPosition >= 1_300 } }
        onMain { controller.seekToNextMediaItem() }
        waitUntil { onMain { controller.currentMediaItemIndex == 1 } }

        assertEquals("Second", onMain { controller.currentMediaItem?.mediaMetadata?.title })
    }

    @Test
    fun brokenItemAdvancesToTheNextPlayableTrack() {
        val missing = playableItem("Missing", File(context.cacheDir, "does-not-exist.wav"))
        val playable = playableItem("Playable", createWaveFile("playable.wav", 4_000))

        onMain {
            controller.setMediaItems(listOf(missing, playable))
            controller.prepare()
            controller.play()
        }

        waitUntil { onMain { controller.currentMediaItemIndex == 1 && controller.isPlaying } }
        assertTrue(onMain { controller.playerError == null })
    }

    @Test
    fun pureShuffleRepeatAllStartsANewCycleWithoutBoundaryDuplicate() {
        val items = listOf(
            playableItem("First", createWaveFile("cycle-first.wav", 400)),
            playableItem("Second", createWaveFile("cycle-second.wav", 400)),
            playableItem("Third", createWaveFile("cycle-third.wav", 400)),
        ).map { it.withQueuePolicy(PlaybackMode.PURE_SHUFFLE, RepeatMode.ALL) }
        onMain {
            controller.repeatMode = Player.REPEAT_MODE_OFF
            controller.setMediaItems(items)
            controller.prepare()
            controller.play()
        }
        waitUntil(timeoutMs = 15_000) { onMain { controller.currentMediaItemIndex == 2 } }
        waitUntil(timeoutMs = 15_000) { onMain { controller.currentMediaItemIndex == 0 } }

        val nextCycleIds = onMain {
            (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
        }
        assertEquals(items.map(MediaItem::mediaId).toSet(), nextCycleIds.toSet())
        assertTrue(nextCycleIds.first() != items.last().mediaId)
        assertTrue(onMain { controller.playWhenReady })
    }

    private fun playableItem(title: String, file: File): MediaItem = MediaItem.Builder()
        .setMediaId(UUID.randomUUID().toString())
        .setUri(Uri.fromFile(file))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist("Meowzix Test")
                .setDurationMs(4_000)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    private fun createWaveFile(name: String, durationMs: Int): File {
        val sampleRate = 8_000
        val sampleCount = sampleRate * durationMs / 1_000
        val dataSize = sampleCount * Short.SIZE_BYTES
        val file = File(context.cacheDir, name).also(files::add)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(sampleRate * Short.SIZE_BYTES)
            putShort(Short.SIZE_BYTES.toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(dataSize)
        }
        FileOutputStream(file).use { output ->
            output.write(header.array())
            val samples = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            repeat(sampleCount) { index ->
                val value = (sin(2 * PI * 440 * index / sampleRate) * Short.MAX_VALUE * 0.05).toInt()
                samples.putShort(value.toShort())
            }
            output.write(samples.array())
        }
        return file
    }

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runCatching(block)
                .onSuccess(value::set)
                .onFailure(error::set)
        }
        error.get()?.let { throw it }
        return value.get()
    }

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Condition was not met within $timeoutMs ms", condition())
    }
}
