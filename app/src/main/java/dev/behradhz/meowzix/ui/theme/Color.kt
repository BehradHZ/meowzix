package dev.behradhz.meowzix.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val CalicoOrange = Color(0xFFE85D04)
internal val CalicoOrangeDark = Color(0xFFB84000)
internal val CalicoOrangeLight = Color(0xFFFFB68B)
internal val CalicoCream = Color(0xFFFFF8F2)
internal val CalicoWhite = Color(0xFFFFFFFF)
internal val CalicoInk = Color(0xFF171411)
internal val CalicoCharcoal = Color(0xFF24211E)
internal val CalicoGraphite = Color(0xFF393532)
internal val CalicoStone = Color(0xFF655F5A)
internal val CalicoWarmGray = Color(0xFFEAE2DC)
internal val CalicoSoftGray = Color(0xFFF4EEE9)

internal val MeowzixLightColorScheme = lightColorScheme(
    primary = CalicoOrangeDark,
    onPrimary = CalicoWhite,
    primaryContainer = Color(0xFFFFDCC9),
    onPrimaryContainer = Color(0xFF351000),
    inversePrimary = CalicoOrangeLight,
    secondary = CalicoGraphite,
    onSecondary = CalicoWhite,
    secondaryContainer = Color(0xFFE9E1DB),
    onSecondaryContainer = CalicoInk,
    tertiary = Color(0xFF7A4E00),
    onTertiary = CalicoWhite,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF281800),
    background = CalicoCream,
    onBackground = CalicoInk,
    surface = CalicoCream,
    onSurface = CalicoInk,
    surfaceVariant = CalicoSoftGray,
    onSurfaceVariant = CalicoStone,
    surfaceTint = CalicoOrangeDark,
    inverseSurface = CalicoCharcoal,
    inverseOnSurface = Color(0xFFF9EFE8),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD9C2B7),
    scrim = Color.Black,
)

internal val MeowzixDarkColorScheme = darkColorScheme(
    primary = CalicoOrangeLight,
    onPrimary = Color(0xFF551F00),
    primaryContainer = Color(0xFF7A2D00),
    onPrimaryContainer = Color(0xFFFFDCC9),
    inversePrimary = CalicoOrangeDark,
    secondary = Color(0xFFD1C7C0),
    onSecondary = Color(0xFF342F2B),
    secondaryContainer = Color(0xFF4B4540),
    onSecondaryContainer = Color(0xFFEDE4DE),
    tertiary = Color(0xFFFFBA4A),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = CalicoInk,
    onBackground = Color(0xFFF1E8E1),
    surface = CalicoInk,
    onSurface = Color(0xFFF1E8E1),
    surfaceVariant = CalicoCharcoal,
    onSurfaceVariant = Color(0xFFD5C4BB),
    surfaceTint = CalicoOrangeLight,
    inverseSurface = Color(0xFFF1E8E1),
    inverseOnSurface = Color(0xFF322E2B),
    outline = Color(0xFFA18E84),
    outlineVariant = Color(0xFF53453F),
    scrim = Color.Black,
)
