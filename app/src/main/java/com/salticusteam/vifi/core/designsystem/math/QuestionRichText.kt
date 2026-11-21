package com.salticusteam.vifi.core.designsystem.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

/** The vertical gap between a paragraph and a display formula (iOS question text stack spacing). */
private val BLOCK_SPACING = 12.dp

/**
 * A question's Markdown + LaTeX text (question, choice, answer, solution step, AI note), drawn inside the screen's
 * capture protection (4.2.0, iOS `QuestionContentView` text runs).
 *
 * The signature is the shared contract (SCT-01): Hat A's `QuestionCard` and Hat C's practice session call it. The text
 * is split by `MathTextTokenizer` into paragraphs (Markdown inline subset: `**bold**`, `*italic*`, `` `code` ``,
 * `~~strike~~`; block syntax stays plain text, line breaks are kept) and display formulas (`$$…$$`, centred on their
 * own line, 1.1 times larger). Inline formulas (`$…$`) flow on the text baseline and wrap with the words; a formula
 * wider than the column breaks into pieces (`MathLineBreaker`) and shrinks only as a last resort. `LatexCompatibility`
 * runs before every formula; one that cannot be typeset shows its source (`$…$`) as plain text, never a crash.
 *
 * Formulas are drawn by AndroidMath onto the compose canvas, so `FLAG_SECURE` / the screen's capture protection covers
 * them like any other pixel. Typeset layouts and drawn bitmaps are cached ([MathTypesetter]), so a `LazyColumn` of
 * cards does not typeset or draw a formula twice.
 *
 * @param style the body style; formulas follow its font size (≈ 17 sp when it has none) and the user's font scale.
 * @param color the text colour; [Color.Unspecified] takes it from [style] / the content colour.
 * @param textAlign alignment of text runs; display formulas are always centred.
 */
@Composable
fun QuestionRichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val blocks = remember(text) { RichBlocks.build(text) }
    val metrics = rememberMathMetrics(style, color)
    val hasFormula = remember(blocks) { blocks.any { it.hasFormula() } }
    val hasInlineFormula = remember(blocks) {
        blocks.any { block -> block is RichBlock.Paragraph && block.parts.any { it is ParagraphPart.Inline } }
    }
    val resolved = remember(style, metrics, textAlign, hasInlineFormula) {
        style.copy(
            fontSize = metrics.designSp.sp,
            textAlign = textAlign ?: style.textAlign,
            // Inline formulas hang their descent below the baseline: leave room for it between lines.
            lineHeight = if (hasInlineFormula && style.lineHeight.isUnspecified) {
                (metrics.designSp * LINE_HEIGHT_FACTOR).sp
            } else {
                style.lineHeight
            },
        )
    }

    if (hasFormula) {
        BoxWithConstraints(modifier = modifier) {
            val lineWidth = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
            RichBlocksColumn(blocks, metrics, lineWidth, resolved, Modifier.fillMaxWidth())
        }
    } else {
        // Plain Markdown: no formula to measure, so no width is needed.
        RichBlocksColumn(blocks, metrics, Float.POSITIVE_INFINITY, resolved, modifier)
    }
}

@Composable
private fun RichBlocksColumn(
    blocks: List<RichBlock>,
    metrics: MathMetrics,
    lineWidthPx: Float,
    style: TextStyle,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BLOCK_SPACING)) {
        for (block in blocks) {
            when (block) {
                is RichBlock.Paragraph -> FormulaParagraph(
                    parts = block.parts,
                    metrics = metrics,
                    lineWidthPx = lineWidthPx,
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                )
                is RichBlock.Display -> FormulaBlock(
                    latex = block.latex,
                    display = true,
                    metrics = metrics,
                    lineWidthPx = lineWidthPx,
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun RichBlock.hasFormula(): Boolean = when (this) {
    is RichBlock.Display -> true
    is RichBlock.Paragraph -> parts.any { it is ParagraphPart.Inline }
}
