package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A quieter, rearranged version of Home's abstract geometry for other destinations.
 * The final matte layer keeps the shapes unobtrusive behind lists and frosted panels.
 */
@Composable
fun SecondaryAmbientBackdrop(variation: Int) {
    val colors = MaterialTheme.colorScheme
    val arrangement = Math.floorMod(variation, 4)
    Box(
        Modifier.fillMaxSize().background(colors.background).graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        Box(
            Modifier
                .align(if (arrangement % 2 == 0) Alignment.TopEnd else Alignment.TopStart)
                .offset(x = if (arrangement % 2 == 0) 70.dp else (-76).dp, y = (90 + arrangement * 24).dp)
                .size(240.dp)
                .rotate(if (arrangement % 2 == 0) 17f else -22f)
                .blur(64.dp)
                .background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(26.dp)),
        )
        Box(
            Modifier
                .align(if (arrangement % 2 == 0) Alignment.CenterStart else Alignment.CenterEnd)
                .offset(x = if (arrangement % 2 == 0) (-100).dp else 96.dp, y = (48 - arrangement * 27).dp)
                .width(300.dp).height(112.dp)
                .rotate(if (arrangement % 2 == 0) -15f else 21f)
                .blur(60.dp)
                .background(colors.tertiary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
        )
        Box(
            Modifier
                .align(if (arrangement % 2 == 0) Alignment.BottomEnd else Alignment.BottomStart)
                .offset(x = if (arrangement % 2 == 0) 42.dp else (-30).dp, y = 45.dp)
                .size(172.dp)
                .rotate(33f)
                .blur(62.dp)
                .background(colors.secondary.copy(alpha = 0.11f), RoundedCornerShape(20.dp)),
        )
        Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.62f)))
    }
}
