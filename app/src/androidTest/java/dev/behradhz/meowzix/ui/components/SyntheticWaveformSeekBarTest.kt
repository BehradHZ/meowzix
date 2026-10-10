package dev.behradhz.meowzix.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyntheticWaveformSeekBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun liveSpectrumDrawsVisibleTallBarsWithinPlayedPartOfFullPlayerSeekBar() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SyntheticWaveformSeekBar(
                trackKey = "live-spectrum-test",
                liveBands = FloatArray(48) { 0.85f },
                value = 0.75f,
                onValueChange = {},
                valueRange = 0f..1f,
                isPlaying = true,
                activeBarColor = Color.Red,
                inactiveBarColor = Color.Blue,
                pointerColor = Color.White,
                modifier = Modifier.width(340.dp),
            )
        }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val tallActiveBars = (0 until bitmap.height / 3).any { y ->
            (bitmap.width / 6 until bitmap.width / 2).any { x ->
                val pixel = bitmap.getPixel(x, y)
                AndroidColor.red(pixel) > 180 &&
                    AndroidColor.green(pixel) < 100 &&
                    AndroidColor.blue(pixel) < 100
            }
        }
        assertTrue("Full player seekbar must render raised live-spectrum bars", tallActiveBars)
    }
}
