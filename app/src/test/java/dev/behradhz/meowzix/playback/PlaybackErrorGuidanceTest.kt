package dev.behradhz.meowzix.playback

import androidx.media3.common.PlaybackException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorGuidanceTest {
    @Test fun missingFileIsActionableInsteadOfGenericSourceError() {
        val error = PlaybackException("Source error", IOException("not found"),
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        assertTrue(userFacingPlaybackError(error).contains("missing"))
    }

    @Test fun identityFailureSuggestsRefreshingPlaylist() {
        val error = PlaybackException("Source error",
            IOException("Telegram audio identity changed; refresh the source before playback"),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        assertTrue(userFacingPlaybackError(error).contains("Refresh the playlist"))
    }

    @Test fun filePathsAreNeverCopiedIntoTheUserFacingMessage() {
        val error = PlaybackException("Source error",
            IOException("Cannot open /data/user/0/private/audio.mp3"),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        val message = userFacingPlaybackError(error)
        assertFalse(message.contains("/data/user"))
        assertFalse(message == "Source error")
    }
}
