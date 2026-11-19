package dev.behradhz.meowzix.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWidgetTitleTickerTest {
    @Test fun shortTitlesRemainUnchanged() {
        assertEquals("Numb", scrollingTitleWindow("Numb", 400, width = 20))
    }

    @Test fun longTitleMovesInBoundedWindows() {
        val title = "A very long song name that needs room to move"
        val first = scrollingTitleWindow(title, 0, width = 16)
        val second = scrollingTitleWindow(title, 4, width = 16)
        assertEquals(16, first.length)
        assertEquals(16, second.length)
        assertFalse(first == second)
    }

    @Test fun nonLatinTextIsPreservedInScrollWindow() {
        val title = "آهنگ زیبای ایرانی برای شنیدن در شب پاییزی"
        assertTrue(scrollingTitleWindow(title, 0, width = 18).isNotBlank())
        assertFalse(scrollingTitleWindow(title, 0, width = 18).contains("\uFFFD"))
    }
}
