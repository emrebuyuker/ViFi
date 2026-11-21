package com.salticusteam.vifi.core.designsystem.math

// Added in 4.2.0 — formula font sizes: follow the body text, its font scale and its cap (iOS `FormulaText.Style`).
/**
 * iOS draws body text at 17 pt following Dynamic Type up to 34 pt, formulas at the text's size and display formulas at
 * 1.1 times it. Android: the body is ≈ 17 sp, the formula follows the text's pixel size (so the user's `fontScale`
 * applies) and stops growing at twice the body's design size — the same 2× ratio, equal to the largest font scale
 * Android offers — so a card never grows past the screen.
 */
internal object MathSizing {
    /** The body text size formulas are designed against, in sp. */
    const val BODY_FONT_SP = 17f

    /** Display formulas are drawn this much larger than inline ones. */
    const val DISPLAY_SCALE = 1.1f

    /** The text may grow to this multiple of its design size before formulas stop following. */
    const val MAX_SCALE = 2f

    /**
     * The pixel size of an inline formula for text drawn at [fontPx] pixels (already scaled by the user's font
     * scale), designed at [designSp] sp on a screen of [density] pixels per dp.
     */
    fun inlinePx(fontPx: Float, designSp: Float, density: Float): Float {
        val cap = designSp * MAX_SCALE * density
        return fontPx.coerceIn(1f, maxOf(1f, cap))
    }

    /** The pixel size of a display formula whose inline size is [inlinePx]. */
    fun displayPx(inlinePx: Float): Float = inlinePx * DISPLAY_SCALE
}
