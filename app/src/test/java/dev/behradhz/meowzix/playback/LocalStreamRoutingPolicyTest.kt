package dev.behradhz.meowzix.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStreamRoutingPolicyTest {
    @Test fun verifiedLocalFileAndContentUrisCanReplaceStaleTelegramUri() {
        assertTrue(isSupportedLocalPlaybackUri("file:///data/user/0/meowzix/offline/song.mp3"))
        assertTrue(isSupportedLocalPlaybackUri("content://media/external/audio/media/1"))
    }

    @Test fun neverRedirectsTelegramStreamsToNetworkOrNestedTelegramUris() {
        assertFalse(isSupportedLocalPlaybackUri("https://example.com/track.mp3"))
        assertFalse(isSupportedLocalPlaybackUri("meowzix-tdlib://audio/123"))
        assertFalse(isSupportedLocalPlaybackUri("/storage/emulated/0/audio.mp3"))
    }
}
