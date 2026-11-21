package com.salticusteam.vifi.core.designsystem.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a formula is drawn in a line, with a fake typesetter: the fall-back paths never throw (SMT-01…03). */
class FormulaPlannerTest {

    private class Box(
        override val width: Float,
        override val ascent: Float = 10f,
        override val descent: Float = 4f,
    ) : MathBox

    /** One character = 10 px; anything containing `BAD` cannot be typeset. */
    private fun typesetter(seen: MutableList<String> = mutableListOf()): (String) -> MathBox? = { latex ->
        seen += latex
        if ("BAD" in latex) null else Box(latex.length * 10f)
    }

    @Test
    fun `a formula that fits is drawn whole at scale one`() {
        val plan = FormulaPlanner.plan("a+b", display = false, lineWidthPx = 200f, typeset = typesetter())
        val whole = plan as FormulaPlan.Whole
        assertEquals(1f, whole.box.scale, 0f)
    }

    @Test
    fun `no width limit never wraps`() {
        val plan = FormulaPlanner.plan(
            "a+b+c+d+e+f", display = false, lineWidthPx = Float.POSITIVE_INFINITY, typeset = typesetter(),
        )
        assertTrue(plan is FormulaPlan.Whole)
    }

    @Test
    fun `malformed LaTeX falls back to its source text`() {
        val inline = FormulaPlanner.plan("""\frac{BAD}{""", display = false, lineWidthPx = 200f, typeset = typesetter())
        assertEquals(FormulaPlan.Source("""$\frac{BAD}{$"""), inline)
        val display = FormulaPlanner.plan("""\frac{BAD}{""", display = true, lineWidthPx = 200f, typeset = typesetter())
        assertEquals(FormulaPlan.Source("""$$\frac{BAD}{$$"""), display)
    }

    @Test
    fun `a typesetter that throws falls back to the source text`() {
        val plan = FormulaPlanner.plan("x^2", display = false, lineWidthPx = 200f) { error("native boom") }
        assertEquals(FormulaPlan.Source("\$x^2\$"), plan)
    }

    @Test
    fun `a zero or invalid size falls back to the source text`() {
        listOf(0f, -3f, Float.NaN, Float.POSITIVE_INFINITY).forEach { width ->
            val plan = FormulaPlanner.plan("x", display = false, lineWidthPx = 200f) { Box(width) }
            assertEquals(width.toString(), FormulaPlan.Source("\$x\$"), plan)
        }
        val flat = FormulaPlanner.plan("x", display = false, lineWidthPx = 200f) { Box(10f, 0f, 0f) }
        assertEquals(FormulaPlan.Source("\$x\$"), flat)
    }

    @Test
    fun `LatexCompatibility runs before the typesetter`() {
        val seen = mutableListOf<String>()
        FormulaPlanner.plan("""\dfrac{1}{2}\leqslant\Bigl(x\Bigr)""", false, 500f, typesetter(seen))
        assertEquals(listOf("""\frac{1}{2}\leq(x)"""), seen)
    }

    @Test
    fun `the source fallback shows the normalized formula`() {
        val plan = FormulaPlanner.plan("""\dfrac{BAD}{2}""", display = false, lineWidthPx = 200f, typeset = typesetter())
        assertEquals(FormulaPlan.Source("""$\frac{BAD}{2}$"""), plan)
    }

    @Test
    fun `a formula wider than the line wraps into its pieces`() {
        // 36 characters = 360 px in a 200 px line.
        val latex = "aaaaaaaaaaaa+bbbbbbbbbbbb+cccccccccccc"
        val plan = FormulaPlanner.plan(latex, display = false, lineWidthPx = 200f, typeset = typesetter())
        val pieces = plan as FormulaPlan.Pieces
        assertEquals(listOf(120f, 10f, 120f, 10f, 120f), pieces.boxes.map { it.width })
        assertTrue(pieces.boxes.all { it.scale == 1f })
    }

    @Test
    fun `a piece still wider than the line is shrunk to it`() {
        val latex = "a" + "x".repeat(40) + "+b" // pieces: 41 chars (410 px), "+", "b"
        val plan = FormulaPlanner.plan(latex, display = false, lineWidthPx = 200f, typeset = typesetter())
        val pieces = plan as FormulaPlan.Pieces
        val first = pieces.boxes.first()
        assertEquals(200f / 410f, first.scale, 1e-6f)
        assertEquals(200f, first.width, 1e-3f)
        assertEquals(1f, pieces.boxes.last().scale, 0f)
    }

    @Test
    fun `a wide formula without a break point shrinks whole`() {
        val latex = """\sqrt{""" + "x".repeat(40) + "}"
        val plan = FormulaPlanner.plan(latex, display = true, lineWidthPx = 100f, typeset = typesetter())
        val whole = plan as FormulaPlan.Whole
        assertEquals(100f, whole.box.width, 1e-3f)
    }

    @Test
    fun `when a piece cannot be typeset the whole formula shrinks instead`() {
        // The whole formula typesets (it has no "BAD"), one piece is rejected: back to one shrunk box.
        val latex = "aaaaaaaaaaaaaaaaaaaaaaaa+bbbbbbbbbbbb"
        val plan = FormulaPlanner.plan(latex, display = false, lineWidthPx = 100f) { piece ->
            if (piece == "bbbbbbbbbbbb") null else Box(piece.length * 10f)
        }
        // whole = 37 chars = 370 px > 100 → pieces tried, one fails → whole shrunk, not Source
        val whole = plan as FormulaPlan.Whole
        assertEquals(100f, whole.box.width, 1e-3f)
    }
}
