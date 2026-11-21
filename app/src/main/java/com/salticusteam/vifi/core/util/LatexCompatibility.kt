package com.salticusteam.vifi.core.util

// Added in 4.2.0 — rewrites LaTeX commands the renderer may not typeset (iOS `LatexCompatibility`, server `normalizeLatex`).
/**
 * Same pairs, same order as `LATEX_REPLACEMENTS` in the shared schema.js. The server normalizes what it writes; the app
 * applies the table to every formula before drawing it, so older questions render too. Only exact command names match
 * (`\big` never touches `\bigcup`), and a command after an escaped backslash (`\\dots` = line break + "dots") is left
 * alone. Pure and idempotent.
 */
object LatexCompatibility {
    /** Command name (without the backslash) → its replacement; an empty replacement drops a sizing/numbering command. */
    val replacements: List<Pair<String, String>> = listOf(
        "varnothing" to "\\emptyset",
        "dfrac" to "\\frac",
        "tfrac" to "\\frac",
        "dbinom" to "\\binom",
        "tbinom" to "\\binom",
        "leqslant" to "\\leq",
        "geqslant" to "\\geq",
        "implies" to "\\Longrightarrow",
        "impliedby" to "\\Longleftarrow",
        "dots" to "\\ldots",
        "lvert" to "|",
        "rvert" to "|",
        "lVert" to "\\Vert",
        "rVert" to "\\Vert",
        "operatorname" to "\\mathrm",
        "coloneqq" to ":=",
        "bigl" to "",
        "bigr" to "",
        "Bigl" to "",
        "Bigr" to "",
        "biggl" to "",
        "biggr" to "",
        "Biggl" to "",
        "Biggr" to "",
        "big" to "",
        "Big" to "",
        "bigg" to "",
        "Bigg" to "",
        "nonumber" to "",
    )

    private val MAP: Map<String, String> = replacements.reversed().toMap()

    /** An escaped backslash (kept as is) or a whole command name `\name`; the same pattern as the server's. */
    private val PATTERN = Regex("""\\\\|\\([A-Za-z]+)""")

    /** [latex] with every listed command replaced; unchanged when it holds none of them. */
    fun normalize(latex: String): String {
        if ('\\' !in latex) return latex
        return PATTERN.replace(latex) { match ->
            val name = match.groups[1]?.value ?: return@replace match.value
            MAP[name] ?: match.value
        }
    }
}
