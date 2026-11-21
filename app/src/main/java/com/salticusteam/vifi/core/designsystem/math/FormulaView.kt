package com.salticusteam.vifi.core.designsystem.math

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Added in 4.2.0 — pixel sizes and colour a block of question text draws its formulas with.
internal data class MathMetrics(
    val designSp: Float,
    val inlinePx: Float,
    val argb: Int,
) {
    val displayPx: Float get() = MathSizing.displayPx(inlinePx)
}

/**
 * The metrics for text styled [style] / [color]: the body size in sp (17 when the style carries none), its pixel size
 * under the user's font scale (capped, see [MathSizing]) and the resolved text colour. Also hands AndroidMath the
 * application context the first time.
 */
@Composable
internal fun rememberMathMetrics(style: TextStyle, color: Color): MathMetrics {
    val context = LocalContext.current
    remember(context) { MathTypesetter.init(context) }
    val density = LocalDensity.current
    val contentColor = LocalContentColor.current
    val designSp = style.fontSize.takeIf { it.isSp }?.value ?: MathSizing.BODY_FONT_SP
    val argb = color.takeOrElse { style.color.takeOrElse { contentColor } }.toArgb()
    return remember(designSp, density.density, density.fontScale, argb) {
        val fontPx = with(density) { designSp.sp.toPx() }
        MathMetrics(designSp, MathSizing.inlinePx(fontPx, designSp, density.density), argb)
    }
}

// Added in 4.2.0 — one formula as a block: whole, wrapped into pieces, or its `$…$` source when it cannot be drawn.
/**
 * A formula between `$…$` (text style) or `$$…$$` ([display], 1.1 times larger and centred in its width). Commands
 * AndroidMath lacks are rewritten first (`LatexCompatibility`); a formula wider than its column wraps into
 * [MathLineBreaker] pieces, and a piece that still does not fit shrinks to the column. Malformed LaTeX, a crash or a
 * zero-width result shows the formula's source as plain text — never an exception.
 *
 * @param style the surrounding text style; the formula follows its font size and font scale.
 * @param color the formula colour; [Color.Unspecified] takes the style's, then the content colour.
 */
@Composable
fun FormulaView(
    latex: String,
    modifier: Modifier = Modifier,
    display: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val metrics = rememberMathMetrics(style, color)
    BoxWithConstraints(modifier = modifier) {
        val lineWidth = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
        FormulaBlock(
            latex = latex,
            display = display,
            metrics = metrics,
            lineWidthPx = lineWidth,
            style = style,
            modifier = if (display) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

@Composable
internal fun FormulaBlock(
    latex: String,
    display: Boolean,
    metrics: MathMetrics,
    lineWidthPx: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val sizePx = if (display) metrics.displayPx else metrics.inlinePx
    val plan = remember(latex, display, sizePx, lineWidthPx) {
        FormulaPlanner.plan(latex, display, lineWidthPx) { MathTypesetter.layout(it, sizePx, display) }
    }
    val textColor = Color(metrics.argb)
    when (plan) {
        is FormulaPlan.Source -> Text(
            text = plan.text,
            modifier = modifier.testTag(FormulaTags.SOURCE),
            style = style,
            color = textColor,
            textAlign = if (display) TextAlign.Center else style.textAlign.takeIf { it != TextAlign.Unspecified },
        )
        is FormulaPlan.Whole -> {
            val density = LocalDensity.current
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                FormulaGlyph(
                    box = plan.box,
                    argb = metrics.argb,
                    description = latex,
                    modifier = Modifier.size(
                        with(density) { plan.box.width.toDp() },
                        with(density) { (plan.box.ascent + plan.box.descent).toDp() },
                    ),
                    baselineY = plan.box.ascent,
                )
            }
        }
        is FormulaPlan.Pieces -> FormulaPiecesText(
            pieces = plan.boxes,
            latex = latex,
            metrics = metrics,
            style = style,
            textAlign = if (display) TextAlign.Center else null,
            modifier = modifier,
        )
    }
}

// Added in 4.2.0 — draws a typeset formula: from the cached bitmap, or directly until the bitmap is ready.
/**
 * The first frame draws the display list straight onto the canvas (under the typesetter lock) while a bitmap is
 * rendered on a background thread and cached; every later frame, and every other place showing the same formula in
 * the same colour, blits that bitmap at its native pixel size (a bitmap is rescaled only for a formula shrunk to fit).
 *
 * @param baselineY where the baseline sits from the canvas top in pixels; `null` = the canvas bottom (inline boxes
 *   are as tall as the formula's ascent, its descent hangs below the text baseline like a glyph descender).
 */
@Composable
internal fun FormulaGlyph(
    box: ScaledBox,
    argb: Int,
    description: String,
    modifier: Modifier = Modifier,
    baselineY: Float? = null,
) {
    val layout = box.box as? MathLayout ?: return
    val bitmap = rememberFormulaBitmap(layout, argb)
    val scale = box.scale
    Canvas(
        modifier = modifier
            .testTag(FormulaTags.FORMULA)
            .semantics { contentDescription = description },
    ) {
        val baseline = baselineY ?: size.height
        val ready = bitmap.value
        if (ready != null) {
            val padding = ready.padding * scale
            drawImage(
                image = ready.image,
                dstOffset = IntOffset((-padding).roundToInt(), (baseline - layout.ascent * scale - padding).roundToInt()),
                dstSize = IntSize(
                    (ready.bitmap.width * scale).roundToInt(),
                    (ready.bitmap.height * scale).roundToInt(),
                ),
                filterQuality = if (scale == 1f) FilterQuality.None else FilterQuality.Medium,
            )
        } else {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.withSave {
                    translate(0f, baseline)
                    scale(scale, scale)
                    layout.draw(this, 0f, 0f, argb)
                }
            }
        }
    }
}

@Composable
private fun rememberFormulaBitmap(layout: MathLayout, argb: Int): State<FormulaBitmap?> {
    val key = BitmapKey(layout.key, argb)
    return produceState(initialValue = MathTypesetter.cachedBitmap(key), key) {
        if (value == null) value = withContext(Dispatchers.Default) { MathTypesetter.bitmap(layout, argb) }
    }
}

// Test tags of the math layer (`<ekran>.<öğe>`): the drawn formula, a formula shown as source text, a paragraph.
internal object FormulaTags {
    const val FORMULA = "questionRichText.formula"
    const val SOURCE = "questionRichText.formulaSource"
    const val PARAGRAPH = "questionRichText.paragraph"
    const val DISPLAY = "questionRichText.display"
}
