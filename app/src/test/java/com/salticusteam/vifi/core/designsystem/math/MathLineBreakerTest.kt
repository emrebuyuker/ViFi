package com.salticusteam.vifi.core.designsystem.math

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `MathLineBreakerTests`, case for case: where a too-wide formula may wrap. */
class MathLineBreakerTest {

    private fun withoutSpaces(text: String) = text.filterNot { it.isWhitespace() }

    @Test
    fun `a set wraps between its elements and pairs in parentheses stay whole`() {
        val pieces = MathLineBreaker.pieces("""B = \{\emptyset, \{(a,k), (a,m)\}\}""")
        assertEquals(listOf("B", "=", """\{\emptyset,""", """\{(a,k),""", """(a,m)\}\}"""), pieces)
    }

    @Test
    fun `relations and binary operators become pieces of their own`() {
        assertEquals(listOf("a", "+", "b", "=", "c"), MathLineBreaker.pieces("a + b = c"))
        assertEquals(
            listOf("X", """\times""", "Y", """\subset""", "Z"),
            MathLineBreaker.pieces("""X \times Y \subset Z"""),
        )
    }

    @Test
    fun `a sign never leaves its number`() {
        assertEquals(listOf("x", "=", "-1"), MathLineBreaker.pieces("x = -1"))
        assertEquals(listOf("x^-1"), MathLineBreaker.pieces("x^-1"))
        assertEquals(listOf("(-a + b)"), MathLineBreaker.pieces("(-a + b)"))
        assertEquals(listOf("-a", "+", "b"), MathLineBreaker.pieces("-a + b"))
    }

    @Test
    fun `groups left right environments and text are never split`() {
        listOf(
            """\frac{a + b, c}{d = e}""",
            """\left( a, b = c \right)""",
            """\begin{cases} a, & x = 1 \\ b, & x = 2 \end{cases}""",
            """\text{a, b = c}""",
            "[a, b]",
            "f(x, y)",
        ).forEach { latex -> assertEquals(latex, listOf(latex), MathLineBreaker.pieces(latex)) }
    }

    @Test
    fun `a trailing comma or a formula without break points is one piece`() {
        assertEquals(listOf("a,"), MathLineBreaker.pieces("a,"))
        assertEquals(listOf("""\sqrt{x}"""), MathLineBreaker.pieces("""\sqrt{x}"""))
        assertEquals(listOf(""), MathLineBreaker.pieces(""))
    }

    @Test
    fun `joining the pieces gives the input back apart from spaces`() {
        listOf(
            """B = \{\emptyset, X \times Y, \{(a,k), (a,m)\}, \{(b,k), (b,m), (c,k), (c,m)\}\}""",
            """f(x) = x^3 - 2x^2 + \frac{1}{x} - \sqrt{x+1} \leq 10""",
            """\Im_1 \times \Im_2 = \{\emptyset, \{(a,k)\}\}""",
            """a \\ b, c""",
        ).forEach { latex ->
            assertEquals(latex, withoutSpaces(latex), withoutSpaces(MathLineBreaker.pieces(latex).joinToString("")))
        }
    }

    @Test
    fun `malformed input never throws and still round-trips`() {
        listOf("""\frac{1}{""", "}", "\\", "{".repeat(50), "a = ", """\left( x""", "= = =").forEach { latex ->
            assertEquals(latex, withoutSpaces(latex), withoutSpaces(MathLineBreaker.pieces(latex).joinToString("")))
        }
    }

    @Test
    fun `a command is only its ASCII letters so a following character stays attached`() {
        assertEquals(listOf("""\alpha""", "=", "1"), MathLineBreaker.pieces("""\alpha=1"""))
        // `\leqx` is one unknown command, not `\leq` + x.
        assertEquals(listOf("""\leqx"""), MathLineBreaker.pieces("""\leqx"""))
    }
}
