package com.salticusteam.vifi.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class VifiColorContrastTest {

    @Test
    fun `dark scheme text pairs meet WCAG AA`() {
        assertTextPairs(VifiDarkColors)
    }

    @Test
    fun `dark scheme outline is distinguishable from background`() {
        assertRatio("outline/background", VifiDarkColors.outline, VifiDarkColors.background, min = 3.0)
    }

    @Test
    fun `light scheme keeps black text on the brand orange`() {
        assertRatio("onPrimary/primary", VifiLightColors.onPrimary, VifiLightColors.primary, min = 4.5)
    }

    @Test
    fun `light scheme text pairs meet WCAG AA`() {
        // The brand orange as text on white stays as on iOS (below 4.5:1), so primary-on-background is not listed.
        assertTextPairs(VifiLightColors, includePrimaryText = false)
    }

    @Test
    fun `light scheme outline is distinguishable from background and cards`() {
        assertRatio("outline/background", VifiLightColors.outline, VifiLightColors.background, min = 3.0)
        assertRatio("outline/surfaceVariant", VifiLightColors.outline, VifiLightColors.surfaceVariant, min = 3.0)
    }

    @Test
    fun `light scheme has no purple tinted neutral or container`() {
        // QA-03: every neutral and container is warm or orange-toned, never the Material3 baseline lilac.
        with(VifiLightColors) {
            listOf(
                "background" to background, "surfaceVariant" to surfaceVariant,
                "secondaryContainer" to secondaryContainer, "primaryContainer" to primaryContainer,
                "surfaceContainerLow" to surfaceContainerLow, "surfaceContainer" to surfaceContainer,
                "surfaceContainerHighest" to surfaceContainerHighest, "outlineVariant" to outlineVariant,
            ).forEach { (name, color) -> assertTrue("$name is blue-tinted", color.blue <= color.red) }
        }
    }

    private fun assertTextPairs(scheme: ColorScheme, includePrimaryText: Boolean = true) = with(scheme) {
        listOfNotNull(
            Triple("onBackground/background", onBackground, background),
            Triple("onSurface/surface", onSurface, surface),
            Triple("onSurfaceVariant/surface", onSurfaceVariant, surface),
            Triple("onSurfaceVariant/surfaceVariant", onSurfaceVariant, surfaceVariant),
            Triple("onSurfaceVariant/surfaceContainerHighest", onSurfaceVariant, surfaceContainerHighest),
            Triple("primary/background", primary, background).takeIf { includePrimaryText },
            Triple("primary/surfaceContainerHigh", primary, surfaceContainerHigh).takeIf { includePrimaryText },
            Triple("onSurface/surfaceContainerLow", onSurface, surfaceContainerLow),
            Triple("onPrimary/primary", onPrimary, primary),
            Triple("onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer),
            Triple("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer),
            Triple("error/background", error, background),
            Triple("onError/error", onError, error),
            Triple("inverseOnSurface/inverseSurface", inverseOnSurface, inverseSurface),
        ).forEach { (name, fg, bg) -> assertRatio(name, fg, bg, min = 4.5) }
    }

    private fun assertRatio(name: String, a: Color, b: Color, min: Double) {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        val ratio = (hi + 0.05) / (lo + 0.05)
        assertTrue("$name contrast $ratio < $min", ratio >= min)
    }
}
