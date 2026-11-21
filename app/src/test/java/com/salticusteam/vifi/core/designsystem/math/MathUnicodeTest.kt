package com.salticusteam.vifi.core.designsystem.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Unicode placeholder patch for AndroidMath's ASCII-only parser. */
class MathUnicodeTest {

    /** What the restore walk does: every placeholder atom gets its original text back. */
    private fun restored(substitution: MathUnicode.Substitution): String =
        substitution.source.map { substitution.originals[it.toString()] ?: it.toString() }.joinToString("")

    private fun substitute(latex: String): MathUnicode.Substitution =
        MathUnicode.substitute(latex) ?: error("no substitution for $latex")

    @Test
    fun `ASCII only input is untouched`() {
        val substitution = substitute("""\frac{a}{b} + @ + 5""")
        assertTrue(substitution.isEmpty)
        assertEquals("""\frac{a}{b} + @ + 5""", substitution.source)
    }

    @Test
    fun `the output is ASCII and restoring gives the input back`() {
        listOf(
            """\text{hız } v=5\,\text{m/s}""",
            """\text{Eğer } x>0 \text{ ise}""",
            """\text{çğıöşüÇĞİÖŞÜ}""",
            """25°\mathrm{C}+3×4""",
            """\frac{\text{çğ}}{\text{ış}}""",
            """x=\begin{cases}1 & \text{eğer } x\geq 0\\0 & \text{değilse}\end{cases}""",
        ).forEach { latex ->
            val substitution = substitute(latex)
            assertTrue(latex, substitution.source.all { it.code in 0x20..0x7E })
            assertEquals(latex, latex, restored(substitution))
        }
    }

    @Test
    fun `indices and exponents keep their letters whatever order the parser stores them in`() {
        // The old queue restored in tree order (superscript before subscript): x_{ö}^{ç} came back as ç / ö swapped.
        // A placeholder per character makes the order irrelevant.
        val subThenSuper = substitute("""x_{\text{ö}}^{\text{ç}}""")
        val superThenSub = substitute("""x^{\text{ç}}_{\text{ö}}""")
        val placeholderOfO = subThenSuper.originals.entries.single { it.value == "ö" }.key
        val placeholderOfC = subThenSuper.originals.entries.single { it.value == "ç" }.key
        assertNotEquals(placeholderOfO, placeholderOfC)
        assertEquals("""x_{\text{ö}}^{\text{ç}}""", restored(subThenSuper))
        assertEquals("""x^{\text{ç}}_{\text{ö}}""", restored(superThenSub))
    }

    @Test
    fun `the same character always gets the same placeholder`() {
        val substitution = substitute("""\text{ğğğ}""")
        assertEquals(1, substitution.originals.size)
        val placeholder = substitution.originals.keys.single()
        assertEquals(3, substitution.source.count { it.toString() == placeholder })
    }

    @Test
    fun `a placeholder never occurs in the formula's own source`() {
        // `@` and the digits are real characters here; the placeholders must be something else.
        val substitution = substitute("""a@b 0123456789 \text{ü}""")
        val placeholder = substitution.originals.entries.single { it.value == "ü" }.key
        assertTrue("source of the formula has no $placeholder", """a@b 0123456789 \text{""".none { it.toString() == placeholder })
        assertEquals("""a@b 0123456789 \text{ü}""", restored(substitution))
    }

    @Test
    fun `at sign comes first when the formula has none`() {
        val substitution = substitute("""\text{ö}""")
        assertEquals("@", substitution.originals.entries.single().key)
    }

    @Test
    fun `twelve distinct Turkish letters are all restored`() {
        val substitution = substitute("""\text{çğıöşüÇĞİÖŞÜ}""")
        assertEquals(12, substitution.originals.size)
        assertEquals("""\text{çğıöşüÇĞİÖŞÜ}""", restored(substitution))
    }

    @Test
    fun `a placeholder letter never glues onto a command name`() {
        // Eleven characters use up `@` and the ten digits; the twelfth would need a letter.
        val eleven = (0x100 until 0x100 + 11).map { it.toChar() }.joinToString("")
        // After a command (`\alphaö` must not become `\alphaq`) a letter is not allowed: give up.
        assertNull(MathUnicode.substitute("""\text{$eleven}\alphaö"""))
        // Anywhere else a letter placeholder is fine, and it is a letter.
        val fine = substitute("""\text{$eleven}\,ö""")
        val placeholder = fine.originals.entries.single { it.value == "ö" }.key
        assertTrue(placeholder[0].isLetter())
        assertEquals("""\text{$eleven}\,ö""", restored(fine))
    }

    @Test
    fun `too many distinct characters give up so the formula falls back to its source`() {
        val many = (0x100 until 0x100 + 80).map { it.toChar() }.joinToString("")
        assertNull(MathUnicode.substitute("""\text{$many}"""))
    }

    @Test
    fun `no-break and thin spaces become a plain space`() {
        val substitution = substitute("a b c")
        assertTrue(substitution.isEmpty)
        assertEquals("a b c", substitution.source)
    }

    @Test
    fun `characters outside the basic plane stay one unit`() {
        val substitution = substitute("""\text{😀}""")
        assertEquals("😀", substitution.originals.values.single())
        assertEquals("""\text{😀}""", restored(substitution))
    }

    @Test
    fun `needsSubstitution is true only for non-ASCII input`() {
        assertTrue(MathUnicode.needsSubstitution("ö"))
        assertTrue(!MathUnicode.needsSubstitution("""\frac{1}{2}"""))
    }
}
