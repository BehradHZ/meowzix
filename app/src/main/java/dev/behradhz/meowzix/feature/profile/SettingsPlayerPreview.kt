package dev.behradhz.meowzix.feature.profile

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.PlayerAccentSource
import kotlin.math.cos
import kotlin.math.sin

/** A compact live representation of the player's actual accent and lyric scaling. */
@Composable
internal fun SettingsPlayerPreview(appearance: AppearanceSettings) {
    val hue = FloatArray(3)
    AndroidColor.colorToHSV(MaterialTheme.colorScheme.primary.toArgb(), hue)
    val themeIsBlue = hue[0] in 175f..260f
    // Contrast the sample artwork against a blue global theme.
    val artworkAccent = if (themeIsBlue) Color(0xFFE5948A) else Color(0xFF3785D2)
    val accent = if (appearance.playerAccentSource == PlayerAccentSource.TRACK)
        artworkAccent else MaterialTheme.colorScheme.primary
    val lyricScale = appearance.lyricsTextScalePercent.coerceIn(80, 160) / 100f

    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 11.dp, vertical = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .testTag("settings-player-preview")
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowerCover(artworkAccent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Flowerlight", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Meowzix · Sample", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { 0.42f },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.18f),
                )
            }
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.size(27.dp))
            Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth().testTag("settings-lyrics-preview"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val scaledSize = 15.sp * lyricScale
            Text("Petals floating in the light", fontSize = scaledSize, lineHeight = scaledSize * 1.2f,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f), textAlign = TextAlign.Center)
            Text("Every color feels like home", fontSize = scaledSize, lineHeight = scaledSize * 1.2f,
                fontWeight = FontWeight.Bold, color = accent, textAlign = TextAlign.Center)
            Text("And the melody will bloom", fontSize = scaledSize, lineHeight = scaledSize * 1.2f,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FlowerCover(accent: Color) {
    Canvas(
        modifier = Modifier.size(74.dp)
            .background(accent.copy(alpha = 0.23f), RoundedCornerShape(14.dp))
            .testTag("settings-flower-cover"),
    ) {
        val unit = size.minDimension
        val center = Offset(size.width * 0.50f, size.height * 0.45f)
        drawLine(Color(0xFF437E5B), Offset(center.x, center.y + unit * .12f),
            Offset(center.x + unit * .10f, size.height * .93f), strokeWidth = unit * .055f)
        drawOval(Color(0xFF65A878), topLeft = Offset(center.x, center.y + unit * .36f),
            size = androidx.compose.ui.geometry.Size(unit * .22f, unit * .11f))
        repeat(7) { index ->
            val rad = index * (2.0 * Math.PI / 7)
            val petal = Offset(
                center.x + cos(rad).toFloat() * unit * .19f,
                center.y + sin(rad).toFloat() * unit * .19f,
            )
            drawCircle(Color.White.copy(alpha = .91f), unit * .14f, center = petal)
            drawCircle(accent.copy(alpha = .18f), unit * .07f, center = petal, style = Stroke(unit * .012f))
        }
        drawCircle(Color(0xFFFFCA59), unit * .12f, center = center)
        drawCircle(Color(0xFFE79A48), unit * .045f, center = center)
    }
}
