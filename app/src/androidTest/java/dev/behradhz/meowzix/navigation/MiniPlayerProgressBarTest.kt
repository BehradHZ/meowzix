package dev.behradhz.meowzix.navigation

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniPlayerProgressBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun progressIsOnBottomAndFillsLeftToRightInLtr() {
        assertProgressDirection(LayoutDirection.Ltr)
    }

    @Test fun progressIsOnBottomAndFillsLeftToRightInRtl() {
        assertProgressDirection(LayoutDirection.Rtl)
    }

    private fun assertProgressDirection(direction: LayoutDirection) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(
                    modifier = Modifier.width(200.dp).height(72.dp)
                        .background(Color.Green).testTag("mini-player-bounds"),
                ) {
                    MiniPlayerProgressBar(
                        progress = 0.25f,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        progressColor = Color.Red,
                        trackColor = Color.Blue,
                    )
                }
            }
        }
        val screenshot = compose.onNodeWithTag("mini-player-bounds")
            .captureToImage().asAndroidBitmap()
        val left = screenshot.width / 10
        val right = screenshot.width * 9 / 10
        val bottom = screenshot.height - 1
        assertEquals(AndroidColor.GREEN, screenshot.getPixel(left, 0))
        assertEquals(AndroidColor.RED, screenshot.getPixel(left, bottom))
        assertEquals(AndroidColor.BLUE, screenshot.getPixel(right, bottom))
    }
}
