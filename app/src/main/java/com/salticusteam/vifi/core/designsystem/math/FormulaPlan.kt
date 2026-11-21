package com.salticusteam.vifi.core.designsystem.math

import com.salticusteam.vifi.core.util.LatexCompatibility
import com.salticusteam.vifi.core.util.MathRun
import com.salticusteam.vifi.core.util.MathTextTokenizer

// Added in 4.2.0 — the measured size of a typeset formula in pixels (baseline at [ascent] from the top).
internal interface MathBox {
    val width: Float
    val ascent: Float
    val descent: Float
}

internal val MathBox.height: Float get() = ascent + descent

// Added in 4.2.0 — a formula box drawn at [scale] (1 unless it had to shrink to fit its line).
internal data class ScaledBox(val box: MathBox, val scale: Float) {
    val width: Float get() = box.width * scale
    val ascent: Float get() = box.ascent * scale
    val descent: Float get() = box.descent * scale
}

// Added in 4.2.0 — how one formula is drawn in a line of a given width (iOS `FormulaText.wrappingMath`).
internal sealed interface FormulaPlan {
    /** One box (shrunk to the line when it is wider and cannot break). */
    data class Whole(val box: ScaledBox) : FormulaPlan

    /** The formula's [MathLineBreaker] pieces, joined by text spaces, each shrunk to the line if still wider. */
    data class Pieces(val boxes: List<ScaledBox>) : FormulaPlan

    /** The formula could not be typeset: its source shown as plain text (`$…$` / `$$…$$`). */
    data class Source(val text: String) : FormulaPlan
}

// Added in 4.2.0 — chooses a [FormulaPlan]; pure, the typesetter is injected so the fall-back paths are JVM-testable.
internal object FormulaPlanner {

    /**
     * Plans [source] for a line [lineWidthPx] wide ([Float.POSITIVE_INFINITY] = no limit). [LatexCompatibility] runs
     * first; [typeset] gets the normalized LaTeX and returns `null` for anything it cannot draw (parse error, crash,
     * zero width), in which case the plan is the `$…$` source text — never an exception.
     */
    fun plan(
        source: String,
        display: Boolean,
        lineWidthPx: Float,
        typeset: (String) -> MathBox?,
    ): FormulaPlan {
        val latex = LatexCompatibility.normalize(source)
        val whole = safeTypeset(latex, typeset) ?: return FormulaPlan.Source(sourceText(latex, display))
        if (whole.width <= lineWidthPx) return FormulaPlan.Whole(ScaledBox(whole, 1f))

        val pieces = MathLineBreaker.pieces(latex)
        val boxes = if (pieces.size > 1) pieces.map { safeTypeset(it, typeset) } else emptyList()
        if (boxes.isEmpty() || boxes.any { it == null }) {
            return FormulaPlan.Whole(ScaledBox(whole, fitScale(whole.width, lineWidthPx)))
        }
        return FormulaPlan.Pieces(boxes.map { box -> ScaledBox(box!!, fitScale(box.width, lineWidthPx)) })
    }

    fun sourceText(latex: String, display: Boolean): String = if (display) "\$\$" + latex + "\$\$" else "\$" + latex + "\$"

    private fun fitScale(width: Float, lineWidthPx: Float): Float =
        if (width > lineWidthPx && lineWidthPx > 0f) lineWidthPx / width else 1f

    private fun safeTypeset(latex: String, typeset: (String) -> MathBox?): MathBox? {
        val box = try {
            typeset(latex)
        } catch (error: Exception) {
            null
        }
        return box?.takeIf { it.width > 0f && it.width.isFinite() && it.height > 0f && it.height.isFinite() }
    }
}

// Added in 4.2.0 — one part of a paragraph: Markdown text or an inline formula.
internal sealed interface ParagraphPart {
    data class Text(val text: String) : ParagraphPart
    data class Inline(val latex: String) : ParagraphPart
}

// Added in 4.2.0 — a block of question text: a paragraph (text with inline formulas) or a display formula.
internal sealed interface RichBlock {
    data class Paragraph(val parts: List<ParagraphPart>) : RichBlock
    data class Display(val latex: String) : RichBlock
}

// Added in 4.2.0 — splits a question's text into blocks (iOS `FormulaText.views(for:)` grouping).
internal object RichBlocks {

    /**
     * Paragraphs between display formulas. Newlines at the very start or end of a paragraph are layout around a
     * display formula, not content, and a paragraph left blank (no inline formula, only whitespace) is dropped.
     */
    fun build(text: String): List<RichBlock> {
        val blocks = mutableListOf<RichBlock>()
        var parts = mutableListOf<ParagraphPart>()

        fun flush() {
            val trimmed = trimNewlines(parts)
            val isBlank = trimmed.none { it is ParagraphPart.Inline } &&
                trimmed.all { (it as? ParagraphPart.Text)?.text?.isBlank() != false }
            if (!isBlank) blocks += RichBlock.Paragraph(trimmed)
            parts = mutableListOf()
        }

        for (run in MathTextTokenizer.tokenize(text)) {
            when (run) {
                is MathRun.Text -> parts += ParagraphPart.Text(run.text)
                is MathRun.InlineMath -> parts += ParagraphPart.Inline(run.latex)
                is MathRun.DisplayMath -> {
                    flush()
                    blocks += RichBlock.Display(run.latex)
                }
            }
        }
        flush()
        return blocks
    }

    private fun isNewline(char: Char): Boolean = char == '\n' || char == '\r' || char == ' ' || char == ' '

    private fun trimNewlines(parts: List<ParagraphPart>): List<ParagraphPart> {
        val result = parts.toMutableList()
        while (true) {
            val first = result.firstOrNull() as? ParagraphPart.Text ?: break
            val trimmed = first.text.trimStart(::isNewline)
            if (trimmed.isEmpty()) result.removeAt(0) else {
                result[0] = ParagraphPart.Text(trimmed)
                break
            }
        }
        while (true) {
            val last = result.lastOrNull() as? ParagraphPart.Text ?: break
            val trimmed = last.text.trimEnd(::isNewline)
            if (trimmed.isEmpty()) result.removeAt(result.lastIndex) else {
                result[result.lastIndex] = ParagraphPart.Text(trimmed)
                break
            }
        }
        return result
    }
}
