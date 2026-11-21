package dev.behradhz.meowzix.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorGuidanceTest {
    @Test fun missingFileIsActionableInsteadOfGenericSourceError() {
        val message = userFacingPlaybackError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, "not found")
        assertTrue(message.contains("missing"))
    }

    @Test fun identityFailureDoesNotRequirePlaylistRefresh() {
        val message = userFacingPlaybackError(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            "Telegram audio identity changed; refresh the source before playback",
        )
        assertTrue(message.contains("Try playing the track again"))
        assertFalse(message.contains("Refresh the playlist"))
    }

    @Test fun onlyTelegramFileIdentityFailuresTriggerTargetedRecovery() {
        assertTrue(isTelegramIdentityFailure("Telegram audio identity changed; refresh the source"))
        assertTrue(isTelegramIdentityFailure("Telegram source identity missing; refresh the source"))
        assertFalse(isTelegramIdentityFailure("Telegram download stalled"))
        assertFalse(isTelegramIdentityFailure("Decoder identity is missing"))
    }

    @Test fun filePathsAreNeverCopiedIntoTheUserFacingMessage() {
        val message = userFacingPlaybackError(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            "Cannot open /data/user/0/private/audio.mp3",
        )
        assertFalse(message.contains("/data/user"))
        assertFalse(message == "Source error")
    }
}
