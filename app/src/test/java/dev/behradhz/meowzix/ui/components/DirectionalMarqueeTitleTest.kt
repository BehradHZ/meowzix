package dev.behradhz.meowzix.ui.components

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionalMarqueeTitleTest {
    @Test fun englishTitlesReadLeftToRightAndScrollLeft() {
        assertEquals(LayoutDirection.Ltr, titleReadingDirection("A very long English title"))
        assertEquals(LayoutDirection.Ltr, titleReadingDirection("2026 - Music"))
    }
    @Test fun persianTitlesReadRightToLeftAndScrollRight() {
        assertEquals(LayoutDirection.Rtl, titleReadingDirection("آهنگ خیلی طولانی"))
        assertEquals(LayoutDirection.Rtl, titleReadingDirection("۱۴۰۵ - پاییز"))
        assertEquals(LayoutDirection.Rtl, titleReadingDirection("2026 - پاییز"))
    }
}
