package dev.behradhz.meowzix.data.telegram

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramStreamUriTest {
    @Test fun streamUriPreservesTrackAndEscapesStableFileIdentity() {
        val uri = Uri.parse(telegramStreamUri(123, "track-42", "unique/+?=&"))
        assertEquals("meowzix-tdlib", uri.scheme)
        assertEquals("123", uri.lastPathSegment)
        assertEquals("track-42", uri.getQueryParameter("trackId"))
        assertEquals("unique/+?=&", uri.getQueryParameter(TELEGRAM_FILE_UNIQUE_ID_QUERY))
    }

    @Test fun legacySourceWithoutIdentityCannotPretendToBeVerified() {
        val uri = Uri.parse(telegramStreamUri(123, "track-42", null))
        assertNull(uri.getQueryParameter(TELEGRAM_FILE_UNIQUE_ID_QUERY))
    }
}
