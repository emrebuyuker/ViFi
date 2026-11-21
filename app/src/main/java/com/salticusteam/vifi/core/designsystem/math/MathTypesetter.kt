package com.salticusteam.vifi.core.designsystem.math

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.agog.mathdisplay.MTFontManager
import com.agog.mathdisplay.parse.MTAccent
import com.agog.mathdisplay.parse.MTFraction
import com.agog.mathdisplay.parse.MTInner
import com.agog.mathdisplay.parse.MTLineStyle
import com.agog.mathdisplay.parse.MTMathAtom
import com.agog.mathdisplay.parse.MTMathColor
import com.agog.mathdisplay.parse.MTMathList
import com.agog.mathdisplay.parse.MTMathListBuilder
import com.agog.mathdisplay.parse.MTMathTable
import com.agog.mathdisplay.parse.MTMathTextColor
import com.agog.mathdisplay.parse.MTOverLine
import com.agog.mathdisplay.parse.MTParseError
import com.agog.mathdisplay.parse.MTParseErrors
import com.agog.mathdisplay.parse.MTRadical
import com.agog.mathdisplay.parse.MTUnderLine
import com.agog.mathdisplay.render.MTMathListDisplay
import com.agog.mathdisplay.render.MTTypesetter
import kotlin.math.ceil

// Added in 4.2.0 — a typeset formula (AndroidMath display list) with its size in pixels; draws itself on a canvas.
/**
 * Everything that touches AndroidMath's shared state — [MTFontManager]'s static font map and FreeType's shared glyph
 * slot — runs inside `synchronized(MathTypesetter)`: typesetting, drawing, and the bitmap renders on a background
 * thread. A layout is immutable otherwise (colour and position are set under that lock before each draw).
 */
internal class MathLayout(
    val key: TypesetKey,
    private val display: MTMathListDisplay,
    override val width: Float,
    override val ascent: Float,
    override val descent: Float,
) : MathBox {

    /** Set when a draw threw: the formula cannot be drawn on this device, the cache entry is dropped. */
    @Volatile
    var isBroken: Boolean = false
        private set

    /**
     * Draws with the baseline at ([x], [baselineY]) in ordinary y-down canvas coordinates, in colour [argb]. Never
     * throws (`MTDrawFreeType.drawGlyph` can); returns whether it drew.
     */
    fun draw(canvas: Canvas, x: Float, baselineY: Float, argb: Int): Boolean = synchronized(MathTypesetter) {
        try {
            display.textColor = argb
            display.position.x = 0f
            display.position.y = 0f
            canvas.withSave {
                translate(x, baselineY)
                scale(1f, -1f)
                display.draw(this)
            }
            true
        } catch (error: Throwable) {
            isBroken = true
            MathTypesetter.forget(this)
            false
        }
    }
}

// Added in 4.2.0 — a formula drawn once into a bitmap at its target pixel size (never rescaled), padded by [padding].
internal class FormulaBitmap(val bitmap: Bitmap, val padding: Int) {
    val image: ImageBitmap = bitmap.asImageBitmap()
    val byteCount: Long get() = bitmap.allocationByteCount.toLong()
}

// Added in 4.2.0 — parses, typesets and caches LaTeX formulas with AndroidMath (iosMath port), app-patched for Unicode.
/**
 * The two cache layers of SMT-04: typeset layouts keyed by `(latex, px, mode)`, and drawn bitmaps keyed by
 * `(latex, px, mode, colour)` in a byte-budgeted LRU. Failures are cached too (a malformed formula is parsed once).
 * Pass LaTeX already run through `LatexCompatibility` (the planner does).
 */
internal object MathTypesetter {

    /** A cached outcome: the layout, or `null` when the formula cannot be drawn. */
    private class TypesetEntry(val layout: MathLayout?)

    private const val MAX_TYPESET_ENTRIES = 512L
    private const val MAX_LATEX_CHARS = 4_000
    private const val MAX_BITMAP_BYTES = 6L * 1024 * 1024
    private const val BITMAP_PADDING = 2
    private const val MAX_DIMENSION_PX = 20_000f

    private val typesetCache = SizedLruCache<TypesetKey, TypesetEntry>(MAX_TYPESET_ENTRIES) { 1L }
    private val bitmapCache = SizedLruCache<BitmapKey, FormulaBitmap>(bitmapBudgetBytes()) { it.byteCount }

    @Volatile
    private var initialized = false

    /** Gives the font manager the application context (it loads the math font from assets); idempotent. Returns whether it is ready. */
    fun init(context: Context): Boolean {
        if (initialized) return true
        synchronized(this) {
            if (initialized) return true
            try {
                MTFontManager.setContext(context.applicationContext)
                initialized = true
            } catch (error: Throwable) {
                // Native library or font asset unavailable: every formula falls back to its source text.
            }
            return initialized
        }
    }

    /** The layout of [latex] at [sizePx] pixels in text or display style, or `null` when it cannot be typeset. */
    fun layout(latex: String, sizePx: Float, display: Boolean): MathLayout? {
        val key = TypesetKey(latex, sizePx, display)
        typesetCache.get(key)?.let { return it.layout?.takeUnless(MathLayout::isBroken) }
        val layout = synchronized(this) {
            // Another thread may have typeset it while this one waited for the lock.
            typesetCache.get(key)?.let { return@synchronized it.layout }
            typeset(key, latex, sizePx, display).also { typesetCache.put(key, TypesetEntry(it)) }
        }
        return layout?.takeUnless(MathLayout::isBroken)
    }

    /** The cached bitmap for [key] if one was drawn already. */
    fun cachedBitmap(key: BitmapKey): FormulaBitmap? = bitmapCache.get(key)

    /**
     * Draws [layout] in [argb] into a bitmap and caches it; `null` when the formula is too large to keep as a bitmap
     * (the caller draws it directly) or drawing failed. Safe on any thread.
     */
    fun bitmap(layout: MathLayout, argb: Int): FormulaBitmap? {
        val key = BitmapKey(layout.key, argb)
        bitmapCache.get(key)?.let { return it }
        val width = ceil(layout.width).toInt() + 2 * BITMAP_PADDING
        val height = ceil(layout.height).toInt() + 2 * BITMAP_PADDING
        if (width <= 0 || height <= 0 || width.toLong() * height * 4 > MAX_BITMAP_BYTES) return null
        val rendered = try {
            val bitmap = createBitmap(width, height)
            val drawn = layout.draw(
                Canvas(bitmap), BITMAP_PADDING.toFloat(), layout.ascent + BITMAP_PADDING, argb,
            )
            if (drawn) FormulaBitmap(bitmap, BITMAP_PADDING) else null
        } catch (error: Throwable) {
            null
        }
        rendered?.let { bitmapCache.put(key, it) }
        return rendered
    }

    /** Drops a layout that failed while drawing, so the next composition shows the source text. */
    fun forget(layout: MathLayout) {
        typesetCache.put(layout.key, TypesetEntry(null))
    }

    // Typesetting

    private fun typeset(key: TypesetKey, latex: String, sizePx: Float, display: Boolean): MathLayout? {
        if (!initialized || !sizePx.isFinite() || sizePx <= 0f || latex.isBlank() || latex.length > MAX_LATEX_CHARS) {
            return null
        }
        return try {
            val substitution = MathUnicode.substitute(latex) ?: return null
            val error = MTParseError()
            val list = MTMathListBuilder.buildFromString(substitution.source, error)
            if (list == null || error.errorcode != MTParseErrors.ErrorNone) return null
            if (!substitution.isEmpty) restore(list, substitution.originals)
            val font = MTFontManager.latinModernFontWithSize(sizePx) ?: return null
            val style = if (display) MTLineStyle.KMTLineStyleDisplay else MTLineStyle.KMTLineStyleText
            val lines = MTTypesetter.createLineForMathList(list, font, style)
            layoutOf(key, lines)
        } catch (error: Throwable) {
            null
        }
    }

    private fun layoutOf(key: TypesetKey, lines: MTMathListDisplay): MathLayout? {
        val width = lines.width
        val ascent = lines.ascent
        val descent = lines.descent
        val usable = width.isFinite() && ascent.isFinite() && descent.isFinite() &&
            width > 0f && ascent + descent > 0f &&
            width < MAX_DIMENSION_PX && ascent + descent < MAX_DIMENSION_PX
        return if (usable) MathLayout(key, lines, width, ascent, descent) else null
    }

    /** Puts the original characters back into the placeholder atoms of [list] (see [MathUnicode]). */
    private fun restore(list: MTMathList, originals: Map<String, String>) {
        for (atom in list.atoms) {
            originals[atom.nucleus]?.let { atom.nucleus = it }
            restoreChildren(atom, originals)
            atom.superScript?.let { restore(it, originals) }
            atom.subScript?.let { restore(it, originals) }
        }
    }

    private fun restoreChildren(atom: MTMathAtom, originals: Map<String, String>) {
        when (atom) {
            is MTFraction -> {
                atom.numerator?.let { restore(it, originals) }
                atom.denominator?.let { restore(it, originals) }
            }
            is MTRadical -> {
                atom.degree?.let { restore(it, originals) }
                atom.radicand?.let { restore(it, originals) }
            }
            is MTInner -> atom.innerList?.let { restore(it, originals) }
            is MTOverLine -> atom.innerList?.let { restore(it, originals) }
            is MTUnderLine -> atom.innerList?.let { restore(it, originals) }
            is MTAccent -> atom.innerList?.let { restore(it, originals) }
            is MTMathColor -> atom.innerList?.let { restore(it, originals) }
            is MTMathTextColor -> atom.innerList?.let { restore(it, originals) }
            is MTMathTable -> atom.cells.forEach { row -> row.forEach { restore(it, originals) } }
            else -> Unit
        }
    }

    private fun bitmapBudgetBytes(): Long =
        (Runtime.getRuntime().maxMemory() / 16).coerceIn(4L * 1024 * 1024, 16L * 1024 * 1024)
}
