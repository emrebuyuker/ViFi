package com.salticusteam.vifi.core.designsystem.math

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formula sizes follow the body text, the user's font scale and the 2x cap (iOS 17 pt → 34 pt, display 1.1x). */
class MathSizingTest {

    @Test
    fun `body text at the design size gives the design size in pixels`() {
        // 17 sp at density 2.625 and font scale 1: 44.625 px.
        assertEquals(44.625f, MathSizing.inlinePx(17f * 2.625f, 17f, 2.625f), 1e-4f)
    }

    @Test
    fun `the formula follows the font scale up to twice the design size`() {
        val density = 3f
        val design = 17f
        assertEquals(17f * 1.3f * density, MathSizing.inlinePx(17f * 1.3f * density, design, density), 1e-3f)
        assertEquals(34f * density, MathSizing.inlinePx(34f * density, design, density), 1e-3f)
        // Beyond twice (an OEM font scale above 2): capped, the card must not grow past the screen.
        assertEquals(34f * density, MathSizing.inlinePx(17f * 2.6f * density, design, density), 1e-3f)
    }

    @Test
    fun `a smaller body style gets a proportionally smaller cap`() {
        assertEquals(32f * 2f, MathSizing.inlinePx(16f * 3f * 2f, 16f, 2f), 1e-3f)
    }

    @Test
    fun `display formulas are 1_1 times the inline size`() {
        assertEquals(50f * 1.1f, MathSizing.displayPx(50f), 1e-4f)
    }

    @Test
    fun `the size never drops below one pixel`() {
        assertEquals(1f, MathSizing.inlinePx(0f, 17f, 2f), 0f)
    }
}
