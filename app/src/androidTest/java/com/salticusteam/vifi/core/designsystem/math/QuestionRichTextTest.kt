package com.salticusteam.vifi.core.designsystem.math

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Question text on device: formulas are drawn by AndroidMath (native FreeType) on the compose canvas, and a formula
 * that cannot be typeset shows its `$…$` source as plain text instead of crashing.
 */
@RunWith(AndroidJUnit4::class)
class QuestionRichTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(text: String, fontScale: Float = 1f, width: Dp? = null) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Surface {
                        val column = Modifier.padding(16.dp).let { if (width != null) it.width(width) else it }
                        Column(column) { QuestionRichText(text = text) }
                    }
                }
            }
        }
    }

    /** The paragraph's text layout (offsets and line metrics are relative to the paragraph's top-left corner). */
    private fun paragraphLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = composeRule.onNodeWithTag(FormulaTags.PARAGRAPH).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("no text layout", action?.invoke(results) == true)
        return results.first()
    }

    /**
     * An inline formula is the `Text`'s inline content, and the text node clears its descendants' semantics
     * (`TextAnnotatedStringNode.shouldClearDescendantSemantics` is always true), so the formula only exists in the
     * unmerged tree. Asserts it is placed on screen with a non-empty size, inside its paragraph.
     */
    private fun assertInlineFormulaDrawn() {
        val formula: SemanticsNodeInteraction =
            composeRule.onNodeWithTag(FormulaTags.FORMULA, useUnmergedTree = true).assertIsDisplayed()
        val bounds = formula.fetchSemanticsNode().boundsInRoot
        assertTrue("formula has no size: $bounds", bounds.width > 0f && bounds.height > 0f)
        val paragraph = composeRule.onNodeWithTag(FormulaTags.PARAGRAPH).fetchSemanticsNode().boundsInRoot
        assertTrue("formula $bounds is outside its paragraph $paragraph", paragraph.contains(bounds.center))
    }

    /** Nodes tagged [tag] in the unmerged tree (inline formulas are not in the merged one, see above). */
    private fun nodeCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun inlineFormula_isDrawnNextToItsText() {
        show("Eğer \$x^2 + 1\$ ise sonuç nedir?")

        composeRule.onNodeWithTag(FormulaTags.PARAGRAPH).assertIsDisplayed()
        assertInlineFormulaDrawn()
        assertEquals(0, nodeCount(FormulaTags.SOURCE))
    }

    @Test
    fun displayFormula_isDrawnOnItsOwnLine() {
        show("Çözüm:\n\$\$\\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}\$\$")

        composeRule.onNodeWithTag(FormulaTags.FORMULA).assertIsDisplayed()
    }

    @Test
    fun turkishLettersInsideText_areTypeset() {
        show("\$\\text{hız } v = 5\\,\\text{m/s}\$")

        assertInlineFormulaDrawn()
        assertEquals(0, nodeCount(FormulaTags.SOURCE))
    }

    @Test
    fun malformedFormula_fallsBackToItsSourceText() {
        show("a \$\\frac{1}{\$ b")

        composeRule.onNodeWithText("a \$\\frac{1}{\$ b", substring = true).assertIsDisplayed()
        assertEquals(0, nodeCount(FormulaTags.FORMULA))
    }

    @Test
    fun malformedDisplayFormula_showsItsSourceCentred() {
        show("\$\$\\begin{pmatrix}1&2\$\$")

        composeRule.onNodeWithTag(FormulaTags.SOURCE).assertIsDisplayed()
        assertEquals(0, nodeCount(FormulaTags.FORMULA))
    }

    @Test
    fun markdownMarkers_areNotShown() {
        show("Bu **önemli** bir *soru* ve `kod`.")

        composeRule.onNodeWithText("Bu önemli bir soru ve kod.").assertIsDisplayed()
    }

    @Test
    fun plainTextWithoutFormula_isOneParagraph() {
        show("Sadece düz metin.")

        composeRule.onNodeWithText("Sadece düz metin.").assertIsDisplayed()
        assertEquals(0, nodeCount(FormulaTags.FORMULA))
    }

    @Test
    fun formulaView_drawsAndFallsBack() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Column(Modifier.padding(16.dp)) {
                        FormulaView(latex = "e^{i\\pi} + 1 = 0", display = true)
                        FormulaView(latex = "\\frac{1}{")
                    }
                }
            }
        }

        composeRule.onNodeWithTag(FormulaTags.FORMULA).assertIsDisplayed()
        composeRule.onNodeWithTag(FormulaTags.SOURCE).assertIsDisplayed()
    }

    @Test
    fun inlineFormulasAtFontScale2_stayInsideTheirOwnLine() {
        show(
            "\$\\displaystyle \\int_0^1 x^2 \\, dx\$ integralini hesaplayınız ve \$f(x) = x^3 - 3x\$ fonksiyonunun " +
                "yerel maksimum noktası \$R_1 = 2\\,\\Omega\$ hangisidir?",
            fontScale = 2f,
        )

        val layout = paragraphLayout()
        assertTrue("expected several lines, got ${layout.lineCount}", layout.lineCount > 2)
        val paragraph = composeRule.onNodeWithTag(FormulaTags.PARAGRAPH).fetchSemanticsNode().boundsInRoot
        val formulas = composeRule.onAllNodesWithTag(FormulaTags.FORMULA, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(3, formulas.size)
        for (formula in formulas) {
            // The glyph node spans the formula's ink: its ascent above the text baseline, its descent below it.
            val ink = formula.boundsInRoot.translate(-paragraph.left, -paragraph.top)
            val line = layout.getLineForVerticalPosition(ink.center.y)
            assertTrue(
                "formula $ink reaches above line $line (top ${layout.getLineTop(line)})",
                ink.top >= layout.getLineTop(line) - PIXEL_TOLERANCE,
            )
            assertTrue(
                "formula $ink reaches into the next line (line $line bottom ${layout.getLineBottom(line)})",
                ink.bottom <= layout.getLineBottom(line) + PIXEL_TOLERANCE,
            )
            // Still on the text baseline: its placeholder (the ascent) starts where the ink does, ends on the baseline.
            val baseline = layout.getLineBaseline(line)
            val box = layout.placeholderRects.filterNotNull()
                .firstOrNull {
                    it.width > 0f && abs(it.left - ink.left) < 1f &&
                        layout.getLineForVerticalPosition(it.center.y) == line
                }
            assertTrue("no placeholder for formula $ink", box != null)
            if (box != null) {
                assertEquals("formula $ink is not on the baseline $baseline", baseline, box.bottom, PIXEL_TOLERANCE)
                assertEquals("ink $ink does not start at its placeholder $box", box.top, ink.top, PIXEL_TOLERANCE)
                assertTrue("formula $ink has no descent below the baseline", ink.bottom >= baseline)
            }
        }
    }

    @Test
    fun lineBreakAfterFormula_doesNotStartTheNextLineWithASpace() {
        show(
            "\$f(x) = x^3 - 3x\$ fonksiyonunun yerel maksimum noktası \$x = 1\$ ve \$y = 2\$ hangisidir?",
            fontScale = 2f,
            width = 220.dp,
        )

        val layout = paragraphLayout()
        val text = layout.layoutInput.text.text
        assertTrue("expected several lines, got ${layout.lineCount}", layout.lineCount > 2)
        for (line in 1 until layout.lineCount) {
            val start = layout.getLineStart(line)
            assertFalse("line $line starts with a space: \"${text.substring(start)}\"", text[start] == ' ')
        }
    }

    @Test
    fun inlineFormula_isReadAsItsSourceNotAPlaceholder() {
        show("Eğer \$x^2 + 1\$ ise sonuç nedir?")

        val node = composeRule.onNodeWithTag(FormulaTags.PARAGRAPH).fetchSemanticsNode()
        val spoken = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
        val readable = spoken.replace("⁠", "").replace(' ', ' ')
        assertFalse("placeholder character in \"$spoken\"", spoken.contains('�'))
        assertTrue("source missing in \"$readable\"", readable.contains("Eğer x^2 + 1 ise sonuç nedir?"))
    }

    private companion object {
        const val PIXEL_TOLERANCE = 1f
    }
}
