package dev.behradhz.meowzix.playback

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import dev.behradhz.meowzix.MainActivity
import dev.behradhz.meowzix.data.settings.DataStoreSettingsRepository
import dev.behradhz.meowzix.domain.playback.CrossfadePolicy
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var activityScenario: ActivityScenario<MainActivity>
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var controller: MediaController
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        // Android 15+ intentionally rejects audio-focus requests from a background-only app. Keep
        // the app foregrounded so these tests exercise the same lifecycle as a user pressing Play.
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
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
        runBlocking {
            val settings = DataStoreSettingsRepository(context)
            settings.setCrossfadeDurationSeconds(0)
            settings.setLoudnessNormalizationEnabled(false)
        }
        onMain {
            controller.stop()
            controller.clearMediaItems()
        }
        onMain { MediaController.releaseFuture(controllerFuture) }
        activityScenario.close()
        files.forEach(File::delete)
    }

    @Test
    fun mediaBrowserUsesTheAuthoritativePlaybackServiceAndExposesRootTree() {
        val browserFuture = MediaBrowser.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync()
        val browser = browserFuture.get(10, TimeUnit.SECONDS)
        try {
            val rootResult = onMain { browser.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS)
            assertEquals(MeowzixMediaLibrary.ROOT_ID, rootResult.value?.mediaId)

            val childrenResult = onMain { browser.getChildren(
                MeowzixMediaLibrary.ROOT_ID,
                0,
                20,
                null,
            ) }.get(10, TimeUnit.SECONDS)
            assertEquals(
                listOf(
                    MeowzixMediaLibrary.TRACKS_ID,
                    MeowzixMediaLibrary.ARTISTS_ID,
                    MeowzixMediaLibrary.ALBUMS_ID,
                    MeowzixMediaLibrary.PLAYLISTS_ID,
                    MeowzixMediaLibrary.FAVORITES_ID,
                ),
                childrenResult.value?.map(MediaItem::mediaId),
            )

            // MediaBrowser is also a controller for this exact session. Existing transport tests in
            // this class therefore validate playback on the same service/session used for browse.
            assertEquals(onMain { controller.connectedToken }, onMain { browser.connectedToken })
        } finally {
            onMain { MediaController.releaseFuture(browserFuture) }
        }
    }


    @Test
    fun mediaBrowserCanReconnectToTheSameServiceAfterClientRelease() {
        fun connect(): Pair<ListenableFuture<MediaBrowser>, MediaBrowser> {
            val future = MediaBrowser.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java)),
            ).buildAsync()
            return future to future.get(10, TimeUnit.SECONDS)
        }

        val (firstFuture, first) = connect()
        assertEquals(MeowzixMediaLibrary.ROOT_ID, onMain { first.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS).value?.mediaId)
        onMain { MediaController.releaseFuture(firstFuture) }

        val (secondFuture, second) = connect()
        try {
            assertEquals(MeowzixMediaLibrary.ROOT_ID, onMain { second.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS).value?.mediaId)
            assertEquals(onMain { controller.connectedToken }, onMain { second.connectedToken })
        } finally {
            onMain { MediaController.releaseFuture(secondFuture) }
        }
    }

    @Test
    fun mediaBrowserSearchReturnsBoundedCarSafeResultsWithoutProviderDetails() {
        val browserFuture = MediaBrowser.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync()
        val browser = browserFuture.get(10, TimeUnit.SECONDS)
        try {
            // An intentionally unmatched query exercises the complete indexed-search callback
            // without relying on a live Telegram account or a separate playback engine.
            val query = "meowzix-no-such-canonical-track-80938"
            val searchResult = onMain { browser.search(query, null) }.get(10, TimeUnit.SECONDS)
            assertEquals(0, searchResult.resultCode)

            val matches = onMain { browser.getSearchResult(query, 0, 20, null) }
                .get(10, TimeUnit.SECONDS)
            assertEquals(0, matches.resultCode)
            assertTrue(matches.value.isNullOrEmpty())
        } finally {
            onMain { MediaController.releaseFuture(browserFuture) }
        }
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
    fun localPlaylistBoundaryDoesNotEnterEndedStateBeforeNextTrack() {
        val first = playableItem("Gapless First", createWaveFile("gapless-first.wav", 1_500))
        val second = playableItem("Gapless Second", createWaveFile("gapless-second.wav", 1_500))
        val endedOnFirst = AtomicBoolean(false)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && controller.currentMediaItemIndex == 0) {
                    endedOnFirst.set(true)
                }
            }
        }
        onMain {
            controller.addListener(listener)
            controller.setMediaItems(listOf(first, second))
            controller.prepare()
            controller.play()
        }
        waitUntil(timeoutMs = 8_000) { onMain { controller.currentMediaItemIndex == 1 && controller.isPlaying } }
        onMain { controller.removeListener(listener) }

        assertTrue("Playlist transition must remain continuous instead of ending between items", !endedOnFirst.get())
        assertEquals("Gapless Second", onMain { controller.currentMediaItem?.mediaMetadata?.title })
    }

    @Test
    fun localCrossfadeHandsMediaSessionToIncomingBeforeOutgoingNaturalEnd() {
        runBlocking {
            val settings = DataStoreSettingsRepository(context)
            settings.setLoudnessNormalizationEnabled(false)
            settings.setCrossfadeDurationSeconds(2)
        }
        // The source files are actual audio, and the test must check an audible-media
        // invariant, not elapsed wall time: an overloaded emulator can take 12+ wall-clock
        // seconds to advance a 6-second WAV, despite valid overlapped player playback.
        val firstDurationMs = 6_000
        val first = playableItem("Crossfade First", createWaveFile("crossfade-first.wav", firstDurationMs))
        val second = playableItem("Crossfade Second", createWaveFile("crossfade-second.wav", 6_000))
        val incomingPositionAtHandoffMs = AtomicReference<Long?>(null)
        val handoffListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem?.mediaId == second.mediaId) {
                    // In real crossfade the incoming ExoPlayer has *already been playing* when
                    // MediaLibrarySession.setPlayer hands the authoritative identity over.
                    // Ordinary gapless/sequential advancement begins the new track at ~0 ms.
                    incomingPositionAtHandoffMs.compareAndSet(null, controller.currentPosition)
                }
            }
        }
        onMain {
            controller.addListener(handoffListener)
            controller.setMediaItems(listOf(first, second))
            controller.prepare()
            controller.play()
        }
        try {
            waitUntil { onMain { controller.currentMediaItemIndex == 0 && controller.isPlaying } }
            waitUntil(timeoutMs = 15_000) {
                onMain { controller.currentMediaItemIndex == 1 && controller.currentMediaItem?.mediaId == second.mediaId }
            }
            val incomingPositionMs = incomingPositionAtHandoffMs.get()
            assertNotNull("Must observe Media3 session item handoff", incomingPositionMs)
            assertTrue(
                "Crossfade must hand off an already-playing incoming track (position=$incomingPositionMs ms)",
                incomingPositionMs!! >= CrossfadePolicy.MIN_REAL_OVERLAP_MS / 2,
            )
            assertTrue(onMain { controller.isPlaying })
        } finally {
            onMain { controller.removeListener(handoffListener) }
        }
    }

    @Test
    fun brokenItemStaysSelectedUntilTheUserExplicitlySkips() {
        val missing = playableItem("Missing", File(context.cacheDir, "does-not-exist.wav"))
        val playable = playableItem("Playable", createWaveFile("playable.wav", 4_000))

        onMain {
            controller.setMediaItems(listOf(missing, playable))
            controller.prepare()
            controller.play()
        }

        waitUntil { onMain { controller.playerError != null } }
        assertEquals(0, onMain { controller.currentMediaItemIndex })
        assertEquals("Missing", onMain { controller.currentMediaItem?.mediaMetadata?.title })
        assertNotNull(onMain { controller.playerError })

        onMain {
            controller.seekToNextMediaItem()
            controller.prepare()
            controller.play()
        }
        waitUntil { onMain { controller.currentMediaItemIndex == 1 && controller.isPlaying } }
        assertEquals("Playable", onMain { controller.currentMediaItem?.mediaMetadata?.title })
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