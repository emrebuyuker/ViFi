package com.salticusteam.vifi.core.util

import com.salticusteam.vifi.core.util.MathRun.DisplayMath
import com.salticusteam.vifi.core.util.MathRun.InlineMath
import com.salticusteam.vifi.core.util.MathRun.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MathTextTokenizerTests`, case for case (STRUCTURED-EXAMS.md §5.1). */
class MathTextTokenizerTest {

    private fun tokenize(input: String) = MathTextTokenizer.tokenize(input)

    @Test
    fun `plain text is one text run and empty input has no runs`() {
        assertEquals(listOf(Text("Bir soru")), tokenize("Bir soru"))
        assertTrue(tokenize("").isEmpty())
    }

    @Test
    fun `inline math splits the text around it`() {
        assertEquals(listOf(InlineMath("x^2"), Text(" ifadesi")), tokenize("\$x^2\$ ifadesi"))
        assertEquals(listOf(Text("a "), InlineMath("b"), Text(" c")), tokenize("a \$b\$ c"))
    }

    @Test
    fun `display math keeps its own run and is trimmed`() {
        assertEquals(
            listOf(Text("Hesapla:\n"), DisplayMath("\\int_0^1 x\\,dx"), Text("\nsonucu bul")),
            tokenize("Hesapla:\n\$\$ \\int_0^1 x\\,dx \$\$\nsonucu bul"),
        )
    }

    @Test
    fun `several formulas in a row keep their order`() {
        assertEquals(
            listOf(InlineMath("a"), Text(" ve "), InlineMath("b"), Text(", "), DisplayMath("c")),
            tokenize("\$a\$ ve \$b\$, \$\$c\$\$"),
        )
    }

    @Test
    fun `an escaped dollar sign is literal text`() {
        assertEquals(listOf(Text("Fiyat \$5 ve "), InlineMath("x")), tokenize("Fiyat \\\$5 ve \$x\$"))
        assertEquals(listOf(InlineMath("a \\\$ b")), tokenize("\$a \\\$ b\$"))
    }

    @Test
    fun `prices and stray dollar signs stay text (Pandoc rules)`() {
        assertEquals(listOf(Text("\$5 ve \$6 tutar")), tokenize("\$5 ve \$6 tutar"))
        assertEquals(listOf(Text("Maliyet \$ 10 \$ olur")), tokenize("Maliyet \$ 10 \$ olur"))
    }

    @Test
    fun `an unclosed dollar is text, not a crash`() {
        assertEquals(listOf(Text("a \$b c")), tokenize("a \$b c"))
        assertEquals(listOf(Text("a \$\$b c")), tokenize("a \$\$b c"))
    }

    @Test
    fun `inline math does not run across a blank line`() {
        assertEquals(listOf(Text("\$a\n\nb\$")), tokenize("\$a\n\nb\$"))
    }

    @Test
    fun `an empty display formula is dropped`() {
        assertEquals(listOf(Text("önce  sonra")), tokenize("önce \$\$  \$\$ sonra"))
    }

    @Test
    fun `the plain text drops the delimiters and keeps the formula source`() {
        assertEquals("x ve y ile \$", MathTextTokenizer.plainText("\$x\$ ve \$\$y\$\$ ile \\\$"))
    }

    @Test
    fun `malformed LaTeX is still a math run`() {
        assertEquals(listOf(InlineMath("\\frac{1}{")), tokenize("\$\\frac{1}{\$"))
    }
}
