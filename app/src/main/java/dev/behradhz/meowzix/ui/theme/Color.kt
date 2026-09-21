package dev.behradhz.meowzix.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val CalicoOrange = Color(0xFFE85D04)
internal val CalicoOrangeDark = Color(0xFFB84000)
internal val CalicoOrangeLight = Color(0xFFFFB38A)
internal val CalicoCream = Color(0xFFFFF8F2)
internal val CalicoWhite = Color(0xFFFFFFFF)
internal val CalicoInk = Color(0xFF11100F)
internal val CalicoCharcoal = Color(0xFF1A1918)
internal val CalicoGraphite = Color(0xFF2A2826)
internal val CalicoStone = Color(0xFF655F5A)
internal val CalicoWarmGray = Color(0xFFEAE2DC)
internal val CalicoSoftGray = Color(0xFFF4EEE9)
internal val CalicoNightText = Color(0xFFF5F0EB)
internal val CalicoNightTextSecondary = Color(0xFFC9C1BA)

internal val MeowzixLightColorScheme = lightColorScheme(
    primary = CalicoOrangeDark,
    onPrimary = CalicoWhite,
    primaryContainer = Color(0xFFFFDCC9),
    onPrimaryContainer = Color(0xFF351000),
    inversePrimary = CalicoOrangeLight,
    secondary = CalicoGraphite,
    onSecondary = CalicoWhite,
    secondaryContainer = Color(0xFFE9E1DB),
    onSecondaryContainer = Color(0xFF171411),
    tertiary = Color(0xFF7A4E00),
    onTertiary = CalicoWhite,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF281800),
    background = CalicoCream,
    onBackground = Color(0xFF171411),
    surface = CalicoCream,
    onSurface = Color(0xFF171411),
    surfaceVariant = CalicoSoftGray,
    onSurfaceVariant = CalicoStone,
    surfaceTint = CalicoOrangeDark,
    inverseSurface = Color(0xFF24211E),
    inverseOnSurface = Color(0xFFF9EFE8),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD9C2B7),
    scrim = Color.Black,
)

/**
 * High-contrast dark palette for the calico visual language.
 *
 * The previous palette used the same near-black value for both background and
 * surface while several components applied translucency on top. On edge-to-edge
 * screens that made hierarchy fragile and, when no root surface was present,
 * could leave light window pixels visible behind dark-theme content. These
 * values deliberately separate background, surface, raised surface and text.
 */
internal val MeowzixDarkColorScheme = darkColorScheme(
    primary = CalicoOrangeLight,
    onPrimary = Color(0xFF3A1F12),
    primaryContainer = Color(0xFF3A261F),
    onPrimaryContainer = Color(0xFFFFDCC9),
    inversePrimary = CalicoOrangeDark,

    secondary = Color(0xFFD8BBAA),
    onSecondary = Color(0xFF2C211B),
    secondaryContainer = Color(0xFF302925),
    onSecondaryContainer = Color(0xFFF0E6DF),

    tertiary = Color(0xFFE8C4AE),
    onTertiary = Color(0xFF332219),
    tertiaryContainer = Color(0xFF443027),
    onTertiaryContainer = Color(0xFFFFDBCA),

    background = CalicoInk,
    onBackground = CalicoNightText,
    surface = CalicoCharcoal,
    onSurface = CalicoNightText,
    surfaceVariant = CalicoGraphite,
    onSurfaceVariant = CalicoNightTextSecondary,
    surfaceTint = CalicoOrangeLight,

    inverseSurface = CalicoNightText,
    inverseOnSurface = Color(0xFF2C2926),

    outline = Color(0xFF8C817A),
    outlineVariant = Color(0xFF4A433F),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1F1B),
    onErrorContainer = Color(0xFFFFDAD5),
    scrim = Color.Black,
)
