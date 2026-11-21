package com.salticusteam.vifi.core.designsystem.math

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

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

/** Placeholder character standing for a formula box in the text; the box itself is drawn by the inline content. */
private const val BOX_CHAR = "�"

/** A line-break opportunity between two pieces of a wrapped formula (also stands for the comma's / operator's space). */
private const val PIECE_SEPARATOR = " "

/** Inline formula boxes are as tall as the formula's ascent; this is the line spacing used when none is styled. */
internal const val LINE_HEIGHT_FACTOR = 1.45f

// Added in 4.2.0 — collects the inline boxes of one Text so formulas sit on the text's baseline.
private class InlineBoxes(private val density: Density, private val argb: Int) {
    val content = LinkedHashMap<String, InlineTextContent>()
    private var counter = 0

    fun AnnotatedString.Builder.appendBox(box: ScaledBox, description: String) {
        val id = "formula${counter++}"
        appendInlineContent(id, BOX_CHAR)
        val placeholder = with(density) {
            // AboveBaseline: the box's bottom sits on the text baseline; the formula hangs its descent below it.
            Placeholder(
                width = ceil(box.width).toSp(),
                height = ceil(box.ascent).coerceAtLeast(1f).toSp(),
                placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
            )
        }
        content[id] = InlineTextContent(placeholder) {
            FormulaGlyph(box = box, argb = argb, description = description, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A built paragraph: the styled text and the boxes that replace its placeholder characters. */
private class BuiltText(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

// Added in 4.2.0 — a paragraph of Markdown text with inline formulas flowing on the baseline.
/**
 * [parts] drawn as one `Text`: Markdown runs as styled spans, each inline formula as an inline box (`Placeholder` +
 * `AboveBaseline`) so it moves with the words and wraps like a word; a formula wider than [lineWidthPx] becomes its
 * [MathLineBreaker] pieces joined by spaces, a piece still too wide shrinks to the line, one that cannot be typeset is
 * its `$…$` source. [style] already carries the resolved size and colour.
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
    val built = remember(parts, metrics, lineWidthPx, density.density, density.fontScale) {
        buildParagraph(parts, metrics, lineWidthPx, density)
    }
    Text(
        text = built.text,
        modifier = modifier.testTag(FormulaTags.PARAGRAPH),
        style = style,
        color = Color(metrics.argb),
        inlineContent = built.inline,
    )
}

private fun buildParagraph(
    parts: List<ParagraphPart>,
    metrics: MathMetrics,
    lineWidthPx: Float,
    density: Density,
): BuiltText {
    val boxes = InlineBoxes(density, metrics.argb)
    val text = buildAnnotatedString {
        for (part in parts) {
            when (part) {
                is ParagraphPart.Text -> appendMarkdown(part.text, metrics.designSp)
                is ParagraphPart.Inline -> appendFormula(part.latex, metrics, lineWidthPx, boxes)
            }
        }
    }
    return BuiltText(text, boxes.content)
}

private fun AnnotatedString.Builder.appendFormula(
    latex: String,
    metrics: MathMetrics,
    lineWidthPx: Float,
    boxes: InlineBoxes,
) {
    val plan = FormulaPlanner.plan(latex, display = false, lineWidthPx = lineWidthPx) {
        MathTypesetter.layout(it, metrics.inlinePx, display = false)
    }
    with(boxes) {
        when (plan) {
            is FormulaPlan.Source -> append(plan.text)
            is FormulaPlan.Whole -> appendBox(plan.box, latex)
            is FormulaPlan.Pieces -> plan.boxes.forEachIndexed { index, box ->
                if (index > 0) append(PIECE_SEPARATOR)
                appendBox(box, latex)
            }
        }
    }
}

// Added in 4.2.0 — the pieces of a wrapped display formula as centred lines of inline boxes.
@Composable
internal fun FormulaPiecesText(
    pieces: List<ScaledBox>,
    latex: String,
    metrics: MathMetrics,
    style: TextStyle,
    textAlign: TextAlign?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val built = remember(pieces, latex, metrics, density.density, density.fontScale) {
        val boxes = InlineBoxes(density, metrics.argb)
        val text = buildAnnotatedString {
            with(boxes) {
                pieces.forEachIndexed { index, box ->
                    if (index > 0) append(PIECE_SEPARATOR)
                    appendBox(box, latex)
                }
            }
        }
        BuiltText(text, boxes.content)
    }
    Text(
        text = built.text,
        modifier = modifier.testTag(FormulaTags.DISPLAY),
        style = style.copy(
            fontSize = with(density) { metrics.displayPx.toSp() },
            lineHeight = with(density) { (metrics.displayPx * LINE_HEIGHT_FACTOR).toSp() },
        ),
        color = Color(metrics.argb),
        textAlign = textAlign,
        inlineContent = built.inline,
    )
}
