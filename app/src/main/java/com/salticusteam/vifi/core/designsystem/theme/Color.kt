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
 * Contrast rule (iOS): text on the brand orange is BLACK — white falls below 3:1.
 * Everything not listed keeps the Material3 baseline neutrals.
 */
internal val VifiLightColors = lightColorScheme(
    primary = VifiOrange,
    onPrimary = VifiAlwaysBlack,
    secondary = VifiOrange,
    onSecondary = VifiAlwaysBlack,
)

internal val VifiDarkColors = darkColorScheme(
    primary = VifiOrangeOnDark,
    onPrimary = VifiAlwaysBlack,
    secondary = VifiOrangeOnDark,
    onSecondary = VifiAlwaysBlack,
)
