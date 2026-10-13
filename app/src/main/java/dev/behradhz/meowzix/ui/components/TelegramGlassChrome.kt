package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** Shared visual tokens taken from the Telegram Forward sheet. */
object TelegramGlassChrome {
    val base = Color(0xFF151413)
    val stroke = Color.White.copy(alpha = 0.10f)
    val fill = Color.White.copy(alpha = 0.055f)
    val muted = Color.White.copy(alpha = 0.58f)
    val backdrop: Brush
        get() = Brush.verticalGradient(
            0f to base.copy(alpha = 0.68f),
            0.42f to base.copy(alpha = 0.91f),
            1f to base.copy(alpha = 0.99f),
        )
}

@Composable
fun TelegramGlassFrame(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        shape = shape,
        modifier = modifier.border(1.dp, TelegramGlassChrome.stroke, shape),
        fallbackColor = TelegramGlassChrome.base.copy(alpha = 0.86f),
        tint = Color.White.copy(alpha = 0.075f),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.025f), Color.Transparent),
                ),
            ),
            content = content,
        )
    }
}

@Composable
fun TelegramGlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    val shape = RoundedCornerShape(22.dp)
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = TelegramGlassChrome.fill,
            unfocusedContainerColor = TelegramGlassChrome.fill,
            disabledContainerColor = TelegramGlassChrome.fill,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = TelegramGlassChrome.muted,
            focusedLabelColor = Color.White.copy(alpha = 0.78f),
            unfocusedLabelColor = TelegramGlassChrome.muted,
            disabledLabelColor = TelegramGlassChrome.muted,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.border(1.dp, TelegramGlassChrome.stroke, shape),
    )
}

/** Same capsule/selection affordance as Forward's Sender / Caption / Remember options. */
@Composable
fun TelegramGlassPill(
    title: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                if (selected) Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.38f), accent.copy(alpha = 0.17f)),
                ) else Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.065f), Color.White.copy(alpha = 0.025f)),
                ),
                CircleShape,
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.72f) else TelegramGlassChrome.stroke,
                CircleShape,
            )
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = onSelectedChange),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
        )
    }
}
