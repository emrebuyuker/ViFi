package com.salticusteam.vifi.core.designsystem.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a question's text is grouped into paragraphs and display formulas (iOS `FormulaText.views(for:)`). */
class RichBlocksTest {

    private fun paragraph(vararg parts: ParagraphPart) = RichBlock.Paragraph(parts.toList())

    @Test
    fun `plain text is one paragraph and empty text has no block`() {
        assertEquals(listOf(paragraph(ParagraphPart.Text("Bir soru"))), RichBlocks.build("Bir soru"))
        assertTrue(RichBlocks.build("").isEmpty())
    }

    @Test
    fun `inline math stays in its paragraph`() {
        assertEquals(
            listOf(
                paragraph(
                    ParagraphPart.Text("Eğer "),
                    ParagraphPart.Inline("x^2"),
                    ParagraphPart.Text(" ise"),
                ),
            ),
            RichBlocks.build("Eğer \$x^2\$ ise"),
        )
    }

    @Test
    fun `a display formula splits the paragraphs around it`() {
        assertEquals(
            listOf(
                paragraph(ParagraphPart.Text("Önce")),
                RichBlock.Display("x=1"),
                paragraph(ParagraphPart.Text("sonra")),
            ),
            RichBlocks.build("Önce\n\$\$x=1\$\$\nsonra"),
        )
    }

    @Test
    fun `newlines around a display formula are layout not content`() {
        val blocks = RichBlocks.build("a\n\n\$\$x\$\$\n\nb")
        assertEquals(
            listOf(paragraph(ParagraphPart.Text("a")), RichBlock.Display("x"), paragraph(ParagraphPart.Text("b"))),
            blocks,
        )
    }

    @Test
    fun `spaces between two display formulas are not a paragraph`() {
        assertEquals(
            listOf(RichBlock.Display("a"), RichBlock.Display("b")),
            RichBlocks.build("\$\$a\$\$ \n \$\$b\$\$"),
        )
    }

    @Test
    fun `a paragraph holding only an inline formula is kept`() {
        assertEquals(listOf(paragraph(ParagraphPart.Inline("x"))), RichBlocks.build("\$x\$"))
    }

    @Test
    fun `inner newlines are kept`() {
        assertEquals(
            listOf(paragraph(ParagraphPart.Text("bir\niki"))),
            RichBlocks.build("bir\niki"),
        )
    }

    @Test
    fun `an unclosed dollar is text`() {
        assertEquals(
            listOf(paragraph(ParagraphPart.Text("Fiyat \$5 ve \$6"))),
            RichBlocks.build("Fiyat \$5 ve \$6"),
        )
    }
}
