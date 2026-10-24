package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** First strong character determines the scrolling direction; leading digits are neutral. */
fun titleReadingDirection(title: String): LayoutDirection {
    title.forEach { character ->
        when (Character.getDirectionality(character)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return LayoutDirection.Rtl
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return LayoutDirection.Ltr
        }
    }
    return LayoutDirection.Ltr
}

/** Long LTR titles travel right-to-left; RTL titles travel left-to-right. Short titles stay still. */
@Composable
fun DirectionalMarqueeTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    reduceMotion: Boolean = false,
) {
    val direction = titleReadingDirection(text)
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        key(text) {
            Text(
                text = text,
                modifier = modifier.then(
                    if (reduceMotion) Modifier else Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 1_350,
                        initialDelayMillis = 1_000,
                        velocity = 28.dp,
                    ),
                ),
                style = style.copy(
                    textDirection = if (direction == LayoutDirection.Rtl) TextDirection.Rtl else TextDirection.Ltr,
                ),
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                color = color,
                maxLines = 1,
                overflow = if (reduceMotion) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
        }
    }
}
