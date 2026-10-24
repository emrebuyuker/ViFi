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

    private fun assertTextPairs(scheme: ColorScheme) = with(scheme) {
        listOf(
            Triple("onBackground/background", onBackground, background),
            Triple("onSurface/surface", onSurface, surface),
            Triple("onSurfaceVariant/surface", onSurfaceVariant, surface),
            Triple("onSurfaceVariant/surfaceVariant", onSurfaceVariant, surfaceVariant),
            Triple("onSurfaceVariant/surfaceContainerHighest", onSurfaceVariant, surfaceContainerHighest),
            Triple("primary/background", primary, background),
            Triple("primary/surfaceContainerHigh", primary, surfaceContainerHigh),
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
