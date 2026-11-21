package com.salticusteam.vifi.core.designsystem.math

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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

    private fun show(text: String) {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Column(Modifier.padding(16.dp)) { QuestionRichText(text = text) }
                }
            }
        }
    }

    @Test
    fun inlineFormula_isDrawnNextToItsText() {
        show("Eğer \$x^2 + 1\$ ise sonuç nedir?")

        composeRule.onNodeWithTag(FormulaTags.PARAGRAPH).assertIsDisplayed()
        composeRule.onNodeWithTag(FormulaTags.FORMULA).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag(FormulaTags.SOURCE).fetchSemanticsNodes().size)
    }

    @Test
    fun displayFormula_isDrawnOnItsOwnLine() {
        show("Çözüm:\n\$\$\\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}\$\$")

        composeRule.onNodeWithTag(FormulaTags.FORMULA).assertIsDisplayed()
    }

    @Test
    fun turkishLettersInsideText_areTypeset() {
        show("\$\\text{hız } v = 5\\,\\text{m/s}\$")

        composeRule.onNodeWithTag(FormulaTags.FORMULA).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag(FormulaTags.SOURCE).fetchSemanticsNodes().size)
    }

    @Test
    fun malformedFormula_fallsBackToItsSourceText() {
        show("a \$\\frac{1}{\$ b")

        composeRule.onNodeWithText("a \$\\frac{1}{\$ b", substring = true).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag(FormulaTags.FORMULA).fetchSemanticsNodes().size)
    }

    @Test
    fun malformedDisplayFormula_showsItsSourceCentred() {
        show("\$\$\\begin{pmatrix}1&2\$\$")

        composeRule.onNodeWithTag(FormulaTags.SOURCE).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag(FormulaTags.FORMULA).fetchSemanticsNodes().size)
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
        assertEquals(0, composeRule.onAllNodesWithTag(FormulaTags.FORMULA).fetchSemanticsNodes().size)
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
}
