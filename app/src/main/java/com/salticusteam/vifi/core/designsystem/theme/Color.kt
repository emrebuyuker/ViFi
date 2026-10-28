package com.salticusteam.vifi.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — same values as the iOS AccentColor asset.
val VifiOrange = Color(0xFFFB910D)
val VifiOrangeOnDark = Color(0xFFFFA62B)

// iOS system orange: the tint of the "locked" and "contribute" tour pages, distinct from the brand accent.
val VifiSystemOrange = Color(0xFFFF9500)

// Colors that must not change with dark mode (iOS constant colors).
val VifiAlwaysBlack = Color(0xFF000000)
val VifiAlwaysWhite = Color(0xFFFFFFFF)

/**
 * Full light scheme, the counterpart of [VifiDarkColors] (QA-03). The Material3 baseline light neutrals are
 * purple/pink-tinted (lilac selected segments, lavender cards, pink background); here the background is white
 * (iOS systemBackground), cards and sheets are warm neutral greys and containers are orange tones of the brand.
 * Contrast rule (iOS): text on the brand orange is BLACK — white falls below 3:1. All text/background pairs are
 * WCAG AA (body text ≥ 7:1, outline ≥ 3:1).
 */
internal val VifiLightColors = lightColorScheme(
    primary = VifiOrange,
    onPrimary = VifiAlwaysBlack,
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2D1600),
    inversePrimary = Color(0xFFFFB870),
    secondary = VifiOrange,
    onSecondary = VifiAlwaysBlack,
    // Selected segment (Görsel/PDF, Kadın/Erkek): a light orange tone instead of the baseline lilac.
    secondaryContainer = Color(0xFFFFE3C9),
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = Color(0xFF626231),
    onTertiary = VifiAlwaysWhite,
    tertiaryContainer = Color(0xFFE9E9AA),
    onTertiaryContainer = Color(0xFF1D1D00),
    background = VifiAlwaysWhite,
    onBackground = Color(0xFF1F1B17),
    surface = VifiAlwaysWhite,
    onSurface = Color(0xFF1F1B17),
    // Card fill on Home/breadcrumb (iOS secondarySystemBackground role): a warm neutral grey just off white.
    surfaceVariant = Color(0xFFF3F0ED),
    onSurfaceVariant = Color(0xFF51463C),
    surfaceTint = VifiOrange,
    inverseSurface = Color(0xFF34302C),
    inverseOnSurface = Color(0xFFF8EFE8),
    outline = Color(0xFF857669),
    outlineVariant = Color(0xFFD8D1CA),
    surfaceDim = Color(0xFFE1DDD9),
    surfaceBright = VifiAlwaysWhite,
    surfaceContainerLowest = VifiAlwaysWhite,
    surfaceContainerLow = Color(0xFFFBF9F7),
    surfaceContainer = Color(0xFFF5F2EF),
    surfaceContainerHigh = Color(0xFFEFECE9),
    surfaceContainerHighest = Color(0xFFE9E5E1),
)

/**
 * Full dark scheme (2026-10-24 decision: light + dark, following the system). The Material3 baseline dark
 * neutrals are purple-tinted and its containers are purple; here every role is a warm neutral or an orange
 * tone of the brand. All text/background pairs are WCAG AA (body text ≥ 7:1, outline ≥ 4.5:1).
 */
internal val VifiDarkColors = darkColorScheme(
    primary = VifiOrangeOnDark,
    onPrimary = VifiAlwaysBlack,
    primaryContainer = Color(0xFF6E3A00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    inversePrimary = Color(0xFF8B5000),
    secondary = VifiOrangeOnDark,
    onSecondary = VifiAlwaysBlack,
    secondaryContainer = Color(0xFF5C4127),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFFC8C98F),
    onTertiary = Color(0xFF303209),
    tertiaryContainer = Color(0xFF47481E),
    onTertiaryContainer = Color(0xFFE5E6A9),
    background = Color(0xFF141311),
    onBackground = Color(0xFFE7E1DC),
    surface = Color(0xFF141311),
    onSurface = Color(0xFFE7E1DC),
    // Card fill on Home/breadcrumb (iOS secondarySystemBackground role): kept a step above the background,
    // lighter than the Material baseline so cards don't read as heavy brown blocks.
    surfaceVariant = Color(0xFF3A342E),
    onSurfaceVariant = Color(0xFFD4C4B5),
    surfaceTint = VifiOrangeOnDark,
    inverseSurface = Color(0xFFE7E1DC),
    inverseOnSurface = Color(0xFF32302D),
    outline = Color(0xFF9D8E81),
    outlineVariant = Color(0xFF4F453B),
    surfaceDim = Color(0xFF141311),
    surfaceBright = Color(0xFF3B3936),
    surfaceContainerLowest = Color(0xFF0F0E0C),
    surfaceContainerLow = Color(0xFF1C1B19),
    surfaceContainer = Color(0xFF211F1D),
    surfaceContainerHigh = Color(0xFF2B2A27),
    surfaceContainerHighest = Color(0xFF363432),
)
