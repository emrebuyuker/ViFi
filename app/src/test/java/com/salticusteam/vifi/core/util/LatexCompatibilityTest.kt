package com.salticusteam.vifi.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `LatexCompatibilityTests` = server `LATEX_CASES` (conversionSchema.test.mjs). */
class LatexCompatibilityTest {

    private val cases = listOf(
        """${'$'}\Im_1 = \{\varnothing, X\}${'$'}""" to """${'$'}\Im_1 = \{\emptyset, X\}${'$'}""",
        """${'$'}x_n := \dfrac{(-1)^n}{n}${'$'} ve ${'$'}\tfrac{1}{2}${'$'}""" to
            """${'$'}x_n := \frac{(-1)^n}{n}${'$'} ve ${'$'}\frac{1}{2}${'$'}""",
        """${'$'}\dbinom{n}{k}${'$'}""" to """${'$'}\binom{n}{k}${'$'}""",
        """${'$'}a \leqslant b \geqslant c${'$'}""" to """${'$'}a \leq b \geq c${'$'}""",
        """${'$'}p \implies q${'$'}""" to """${'$'}p \Longrightarrow q${'$'}""",
        """${'$'}1, 2, \dots, n${'$'}""" to """${'$'}1, 2, \ldots, n${'$'}""",
        """${'$'}\lvert x \rvert + \lVert v \rVert${'$'}""" to """${'$'}| x | + \Vert v \Vert${'$'}""",
        """${'$'}\operatorname{rank} A${'$'}""" to """${'$'}\mathrm{rank} A${'$'}""",
        """${'$'}\bigl( a \bigr) \Big[ b \Big]${'$'}""" to """${'$'}( a ) [ b ]${'$'}""",
        """${'$'}\bigcup_i A_i \cap \bigcap_j B_j${'$'}""" to """${'$'}\bigcup_i A_i \cap \bigcap_j B_j${'$'}""",
        """${'$'}\varnothingx${'$'}""" to """${'$'}\varnothingx${'$'}""",
        """satır\\dots""" to """satır\\dots""",
        "Formülsüz metin." to "Formülsüz metin.",
    )

    @Test
    fun `each shared case normalizes like the server`() {
        assertEquals(13, cases.size)
        for ((input, expected) in cases) assertEquals(input, expected, LatexCompatibility.normalize(input))
    }

    @Test
    fun `normalizing is idempotent`() {
        for ((input, _) in cases) {
            val once = LatexCompatibility.normalize(input)
            assertEquals(once, LatexCompatibility.normalize(once))
        }
    }

    @Test
    fun `every replacement target is left alone`() {
        for ((_, replacement) in LatexCompatibility.replacements) {
            assertEquals(replacement, LatexCompatibility.normalize(replacement))
        }
    }

    @Test
    fun `the table has no duplicate commands`() {
        val commands = LatexCompatibility.replacements.map { it.first }
        assertEquals(commands.size, commands.toSet().size)
    }
}
