package com.salticusteam.vifi.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/** iOS uses the system font with Dynamic Type; the Material3 type scale (sp, honours fontScale) is the equivalent. */
internal val VifiTypography = Typography()

/**
 * The app's single theme entry point. Dynamic color (Material You) is intentionally not used:
 * it would replace the brand orange with the wallpaper palette.
 */
@Composable
fun ViFiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) VifiDarkColors else VifiLightColors,
        typography = VifiTypography,
        content = content,
    )
}
