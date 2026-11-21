package com.salticusteam.vifi.core.designsystem.math

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max

// Added in 4.2.0 — the Markdown inline subset as an AnnotatedString (iOS `FormulaText.markdown`).
internal fun AnnotatedString.Builder.appendMarkdown(text: String, baseSp: Float) {
    for (span in MarkdownInline.parse(text)) {
        if (span.isPlain) {
            append(span.text)
            continue
        }
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            textDecoration = if (span.strikethrough) TextDecoration.LineThrough else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            fontSize = if (span.code) (baseSp * CODE_SIZE_FACTOR).sp else TextUnit.Unspecified,
        )
        withStyle(style) { append(span.text) }
    }
}

private const val CODE_SIZE_FACTOR = 0.92f

/** Stands for a formula in the text when its source has nothing readable (never empty: inline content needs a char). */
private const val BOX_CHAR = "�"

/** Glues the characters of a formula's spoken source so the line breaker never splits one formula box. */
private const val WORD_JOINER = '⁠'

/** A space inside a formula's spoken source (a plain space would be a line-break opportunity). */
private const val NO_BREAK_SPACE = ' '

/** A line-break opportunity between pieces of a wrapped formula (also stands for the comma's / operator's space). */
private const val SPACE = " "

/** The alternate text of the zero-width strut that gives every line of a paragraph the tallest formula's ascent. */
private const val STRUT_TEXT = "⁠"
private const val STRUT_ID = "formulaStrut"

/** Inline formula boxes are as tall as the formula's ascent; this is the line spacing used when none is styled. */
internal const val LINE_HEIGHT_FACTOR = 1.45f

/** Extra room, as a fraction of the font size, between a formula's descent and the next line when lines must grow. */
private const val LINE_GAP_FACTOR = 0.12f

/** Width (in em) of the probe placeholder that reads the font size the text is laid out with. */
private const val FONT_PROBE_EM = 10f

// Added in 4.2.0 — the font's own line metrics and space width, measured once per text style.
/** [ascentPx] / [descentPx] of one unspaced line of the style, and the width of its space. */
internal data class LineFont(val fontPx: Float, val ascentPx: Float, val descentPx: Float, val spacePx: Float)

@Composable
private fun rememberLineFont(style: TextStyle): LineFont {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return remember(style, density.density, density.fontScale) { measureLineFont(measurer, style, density) }
}

private fun measureLineFont(measurer: TextMeasurer, style: TextStyle, density: Density): LineFont {
    val plain = style.copy(lineHeight = TextUnit.Unspecified, lineHeightStyle = null)
    val spaced = measurer.measure("x x", plain, softWrap = false, maxLines = 1, density = density)
    val tight = measurer.measure("xx", plain, softWrap = false, maxLines = 1, density = density)
    val baseline = spaced.firstBaseline
    // The font size in the text's own pixels: a placeholder of FONT_PROBE_EM em is exactly that many font sizes wide.
    val probe = measurer.measure(
        AnnotatedString(BOX_CHAR),
        plain,
        softWrap = false,
        maxLines = 1,
        placeholders = listOf(
            AnnotatedString.Range(
                Placeholder(FONT_PROBE_EM.em, FONT_PROBE_EM.em, PlaceholderVerticalAlign.AboveBaseline),
                0,
                BOX_CHAR.length,
            ),
        ),
        density = density,
    )
    val probed = probe.placeholderRects.firstOrNull()?.width?.div(FONT_PROBE_EM)
    val fontPx = probed?.takeIf { it > 0f } ?: with(density) { MathSizing.BODY_FONT_SP.sp.toPx() }
    return LineFont(
        fontPx = fontPx,
        ascentPx = baseline - spaced.getLineTop(0),
        descentPx = spaced.getLineBottom(0) - baseline,
        spacePx = (spaced.getHorizontalPosition(3, true) - tight.getHorizontalPosition(2, true)).coerceAtLeast(0f),
    )
}

/**
 * The spoken / semantic text of a formula box: its LaTeX source (as `MathTextTokenizer.plainText` reads it), with
 * spaces made non-breaking and word joiners around every non-alphanumeric character, so TalkBack reads the source
 * instead of a placeholder and the line breaker still treats the box as one unbreakable unit.
 */
internal fun formulaAlternateText(latex: String): String {
    val source = latex.trim()
    if (source.isEmpty()) return BOX_CHAR
    return buildString {
        source.forEachIndexed { index, char ->
            if (index > 0 && !(char.isLetterOrDigit() && source[index - 1].isLetterOrDigit())) append(WORD_JOINER)
            append(if (char.isWhitespace()) NO_BREAK_SPACE else char)
        }
    }
}

// Added in 4.2.0 — collects the inline boxes of one Text so formulas sit on the text's baseline.
/**
 * Placeholder and line sizes are given in `em` of the text's font, not `sp`: the text converts `sp` with its own
 * (linear) font scale while `Density.toSp` may use a non-linear one, so an `sp` round trip can misplace a box at large
 * font scales; `em` multiplies the very font size the text was laid out with.
 */
private class InlineBoxes(
    private val density: Density,
    private val font: LineFont,
    private val argb: Int,
    private val lineWidthPx: Float,
) {
    val content = LinkedHashMap<String, InlineTextContent>()
    var maxAscentPx = 0f
        private set
    var maxDescentPx = 0f
        private set
    private var counter = 0

    /**
     * One formula box. With [trailingSpacePx] > 0 the space after it becomes part of the box (when it still fits the
     * line), so a line that breaks after the formula never starts with that space.
     */
    fun AnnotatedString.Builder.appendBox(box: ScaledBox, latex: String, trailingSpacePx: Float = 0f): Boolean {
        val id = "formula${counter++}"
        val width = ceil(box.width)
        val absorbs = trailingSpacePx > 0f && width + trailingSpacePx <= lineWidthPx
        val ascent = ceil(box.ascent).coerceAtLeast(1f)
        maxAscentPx = max(maxAscentPx, ascent)
        maxDescentPx = max(maxDescentPx, ceil(box.descent))
        appendInlineContent(id, formulaAlternateText(latex) + if (absorbs) SPACE else "")
        // AboveBaseline: the box's bottom sits on the text baseline; the formula hangs its descent below it.
        val placeholder = Placeholder(
            width = (width + if (absorbs) trailingSpacePx else 0f).toEm(),
            height = ascent.toEm(),
            placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
        )
        val inkHeight = with(density) { (box.ascent + box.descent).toDp() }
        content[id] = InlineTextContent(placeholder) {
            // The glyph's node spans the formula's ink (ascent + descent), overflowing the placeholder downwards.
            FormulaGlyph(
                box = box,
                argb = argb,
                description = latex,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height(inkHeight),
                baselineY = box.ascent,
            )
        }
        return absorbs
    }

    /** Pieces of a wrapped formula: every piece but the last carries the space that separates it from the next. */
    fun AnnotatedString.Builder.appendPieces(pieces: List<ScaledBox>, latex: String, spacePx: Float, trailing: Float) =
        pieces.foldIndexed(false) { index, _, box ->
            val last = index == pieces.lastIndex
            val absorbed = appendBox(box, latex, if (last) trailing else spacePx)
            if (!last && !absorbed) append(SPACE)
            absorbed
        }

    /**
     * A zero-width box as tall as the tallest formula, at the start of the text: the line height span takes its
     * metrics from the first line, so this gives every line room for every formula's ascent.
     */
    fun AnnotatedString.Builder.appendStrut() {
        if (maxAscentPx <= 0f) return
        appendInlineContent(STRUT_ID, STRUT_TEXT)
        content[STRUT_ID] = InlineTextContent(
            Placeholder(0.em, maxAscentPx.toEm(), PlaceholderVerticalAlign.AboveBaseline),
        ) {}
    }

    private fun Float.toEm(): TextUnit = (this / font.fontPx).em

    /**
     * The line height that keeps every formula clear of its neighbours, at least [basePx].
     *
     * Compose's line height span takes every line's metrics from the first line (ascent = the strut's tallest ascent,
     * descent = the font's) and, centred like Material text, adds half of the extra height above and half below. So
     * the descent below each baseline is `fontDescent + extra / 2`; the line is made tall enough for that to cover the
     * deepest formula descent plus a small gap.
     */
    fun lineStyle(style: TextStyle, basePx: Float): TextStyle {
        if (maxAscentPx <= 0f) return style // no formula box: plain text keeps its own line spacing
        val natural = max(font.ascentPx, maxAscentPx) + font.descentPx
        val missingBelow = (max(font.descentPx, maxDescentPx) + font.fontPx * LINE_GAP_FACTOR - font.descentPx)
            .coerceAtLeast(0f)
        return style.copy(
            lineHeight = max(basePx, natural + 2 * missingBelow).toEm(),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
}

/** The style's line height in pixels, or [LINE_HEIGHT_FACTOR] times its font when it has none. */
private fun baseLineHeightPx(style: TextStyle, font: LineFont, density: Density): Float = when {
    // sp over sp: the same proportion of the laid-out font size (see InlineBoxes on sp conversions).
    style.lineHeight.isSp && style.fontSize.isSp -> style.lineHeight.value / style.fontSize.value * font.fontPx
    style.lineHeight.isSp -> with(density) { style.lineHeight.toPx() }
    style.lineHeight.isEm -> style.lineHeight.value * font.fontPx
    else -> font.fontPx * LINE_HEIGHT_FACTOR
}

/** A built paragraph: the styled text, the boxes that replace its placeholder characters and its line style. */
private class BuiltText(val text: AnnotatedString, val inline: Map<String, InlineTextContent>, val style: TextStyle)

// Added in 4.2.0 — a paragraph of Markdown text with inline formulas flowing on the baseline.
/**
 * [parts] drawn as one `Text`: Markdown runs as styled spans, each inline formula as an inline box (`Placeholder` +
 * `AboveBaseline`) so it moves with the words and wraps like a word; a formula wider than [lineWidthPx] becomes its
 * [MathLineBreaker] pieces joined by spaces, a piece still too wide shrinks to the line, one that cannot be typeset is
 * its `$…$` source. [style] already carries the resolved size and colour; the line height grows so no formula's
 * ascent or descent reaches into a neighbouring line (font scale included).
 */
@Composable
internal fun FormulaParagraph(
    parts: List<ParagraphPart>,
    metrics: MathMetrics,
    lineWidthPx: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val font = rememberLineFont(style)
    val built = remember(parts, metrics, lineWidthPx, style, font, density.density, density.fontScale) {
        buildParagraph(parts, metrics, lineWidthPx, style, font, density)
    }
    Text(
        text = built.text,
        modifier = modifier.testTag(FormulaTags.PARAGRAPH),
        style = built.style,
        color = Color(metrics.argb),
        inlineContent = built.inline,
    )
}

private fun buildParagraph(
    parts: List<ParagraphPart>,
    metrics: MathMetrics,
    lineWidthPx: Float,
    style: TextStyle,
    font: LineFont,
    density: Density,
): BuiltText {
    val boxes = InlineBoxes(density, font, metrics.argb, lineWidthPx)
    val body = buildAnnotatedString {
        var dropLeadingSpace = false
        parts.forEachIndexed { index, part ->
            when (part) {
                is ParagraphPart.Text -> {
                    val text = if (dropLeadingSpace) part.text.removePrefix(SPACE) else part.text
                    appendMarkdown(text, metrics.designSp)
                    dropLeadingSpace = false
                }
                is ParagraphPart.Inline -> {
                    val next = parts.getOrNull(index + 1)
                    val spaceFollows = next is ParagraphPart.Text && next.text.startsWith(SPACE)
                    dropLeadingSpace = appendFormula(part.latex, metrics, lineWidthPx, font, spaceFollows, boxes)
                }
            }
        }
    }
    val text = buildAnnotatedString {
        with(boxes) { appendStrut() }
        append(body)
    }
    return BuiltText(text, boxes.content, boxes.lineStyle(style, baseLineHeightPx(style, font, density)))
}

/** Appends one inline formula; returns whether it took the space that follows it into its last box. */
private fun AnnotatedString.Builder.appendFormula(
    latex: String,
    metrics: MathMetrics,
    lineWidthPx: Float,
    font: LineFont,
    spaceFollows: Boolean,
    boxes: InlineBoxes,
): Boolean {
    val plan = FormulaPlanner.plan(latex, display = false, lineWidthPx = lineWidthPx) {
        MathTypesetter.layout(it, metrics.inlinePx, display = false)
    }
    val trailing = if (spaceFollows) font.spacePx else 0f
    return with(boxes) {
        when (plan) {
            is FormulaPlan.Source -> {
                append(plan.text)
                false
            }
            is FormulaPlan.Whole -> appendBox(plan.box, latex, trailing)
            is FormulaPlan.Pieces -> appendPieces(plan.boxes, latex, font.spacePx, trailing)
        }
    }
}

// Added in 4.2.0 — the pieces of a wrapped display formula as centred lines of inline boxes.
@Composable
internal fun FormulaPiecesText(
    pieces: List<ScaledBox>,
    latex: String,
    metrics: MathMetrics,
    lineWidthPx: Float,
    style: TextStyle,
    textAlign: TextAlign?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val displayStyle = style.copy(
        fontSize = with(density) { metrics.displayPx.toSp() },
        lineHeight = TextUnit.Unspecified,
    )
    val font = rememberLineFont(displayStyle)
    val built = remember(pieces, latex, metrics, lineWidthPx, displayStyle, font, density.density, density.fontScale) {
        val boxes = InlineBoxes(density, font, metrics.argb, lineWidthPx)
        val body = buildAnnotatedString { with(boxes) { appendPieces(pieces, latex, font.spacePx, trailing = 0f) } }
        val text = buildAnnotatedString {
            with(boxes) { appendStrut() }
            append(body)
        }
        BuiltText(text, boxes.content, boxes.lineStyle(displayStyle, font.fontPx * LINE_HEIGHT_FACTOR))
    }
    Text(
        text = built.text,
        modifier = modifier.testTag(FormulaTags.DISPLAY),
        style = built.style,
        color = Color(metrics.argb),
        textAlign = textAlign,
        inlineContent = built.inline,
    )
}
