package com.salticusteam.vifi.core.designsystem.math

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.salticusteam.vifi.core.util.MathTextTokenizer

/**
 * A question's Markdown + LaTeX text (question, choice, answer, solution step, AI note), drawn inside the screen's
 * capture protection (4.2.0, iOS `QuestionContentView` text runs).
 *
 * The signature is the shared contract (SCT-01): Hat A's `QuestionCard` and Hat C's practice session call it; Hat B
 * (SMT-02) replaces the body with the real renderer — Markdown inline subset, inline formulas on the baseline,
 * display formulas centred on their own line, `LatexCompatibility` before every formula, `MathLineBreaker` for wide
 * ones. Until then it shows the plain text with the `$` delimiters removed ([MathTextTokenizer.plainText]).
 *
 * @param style the body style; formulas follow its font size (and the user's font scale).
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
    // Temporary body (SCT-01): Hat B (SMT-02) renders the runs.
    val plain = remember(text) { MathTextTokenizer.plainText(text) }
    Text(text = plain, modifier = modifier, style = style, color = color, textAlign = textAlign)
}
