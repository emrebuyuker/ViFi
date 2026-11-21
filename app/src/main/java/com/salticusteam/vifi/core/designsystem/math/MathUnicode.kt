package com.salticusteam.vifi.core.designsystem.math

// Added in 4.2.0 — carries non-ASCII characters through AndroidMath's ASCII-only parser.
/**
 * AndroidMath's parser silently drops every character outside `0x21..0x7E` (`\text{hız}` would typeset "hz", `25°C`
 * would lose the degree sign). The app patches this in place instead of forking the library: each distinct non-ASCII
 * character is swapped for an ordinary ASCII placeholder before parsing, and the originals are put back into the
 * parsed atoms before typesetting (the atom's nucleus is replaced, the font draws the real glyph).
 *
 * Placeholders are chosen per *character*, never per position, so the restore step does not depend on the order the
 * parser stores things in — `x_{ö}^{ç}` (subscript parsed first, then superscript) and `x^{ç}_{ö}` both come back
 * right, which a source-order queue cannot guarantee. A placeholder never occurs in the formula's own source (so a
 * genuine `@` or digit is never mistaken for one): `@` first, then digits, then letters — a letter only for a
 * character that never directly follows an ASCII letter or a backslash (`\alphaö` must not become `\alphaq`).
 * Whitespace characters (no-break space, thin space …) become a plain space. When there are more distinct characters
 * than usable placeholders, [substitute] returns `null` and the formula falls back to its source text.
 *
 * Pure (no Android classes); the restore walk over the parsed atoms lives in [MathTypesetter].
 */
internal object MathUnicode {

    /** [source] is ASCII only; [originals] maps each placeholder atom back to the characters it replaced. */
    class Substitution(val source: String, val originals: Map<String, String>) {
        val isEmpty: Boolean get() = originals.isEmpty()
    }

    private const val FIRST_PRINTABLE = 0x21
    private const val LAST_PRINTABLE = 0x7E

    fun needsSubstitution(latex: String): Boolean = latex.any { it.code > LAST_PRINTABLE }

    /** [latex] made parser-safe, or `null` when it holds more distinct non-ASCII characters than placeholders exist. */
    fun substitute(latex: String): Substitution? {
        if (!needsSubstitution(latex)) return Substitution(latex, emptyMap())

        val used = latex.filter { it.code in FIRST_PRINTABLE..LAST_PRINTABLE }.toSet()
        val letterSafe = HashMap<String, Boolean>()
        val order = ArrayList<String>()
        var previous: Char? = null
        var index = 0
        while (index < latex.length) {
            val codePoint = latex.codePointAt(index)
            val width = Character.charCount(codePoint)
            val char = latex[index]
            if (codePoint > LAST_PRINTABLE && !isSpace(codePoint)) {
                val key = latex.substring(index, index + width)
                if (key !in letterSafe) order += key
                val afterCommandText = previous != null && (isAsciiLetter(previous) || previous == '\\')
                letterSafe[key] = (letterSafe[key] ?: true) && !afterCommandText
            }
            previous = char
            index += width
        }

        val digits = DIGITS.filter { it !in used }
        val letters = LETTERS.filter { it !in used }
        val symbols = SYMBOLS.filter { it !in used }
        val digitQueue = ArrayDeque((symbols + digits).toList())
        val letterQueue = ArrayDeque(letters.toList())

        val placeholders = HashMap<String, Char>()
        for (key in order) {
            val placeholder = digitQueue.removeFirstOrNull()
                ?: if (letterSafe[key] == true) letterQueue.removeFirstOrNull() else null
            if (placeholder == null) return null
            placeholders[key] = placeholder
        }

        val source = StringBuilder(latex.length)
        index = 0
        while (index < latex.length) {
            val codePoint = latex.codePointAt(index)
            val width = Character.charCount(codePoint)
            when {
                codePoint <= LAST_PRINTABLE -> source.append(latex[index])
                isSpace(codePoint) -> source.append(' ')
                else -> source.append(placeholders.getValue(latex.substring(index, index + width)))
            }
            index += width
        }
        return Substitution(source.toString(), placeholders.entries.associate { (key, value) -> value.toString() to key })
    }

    private fun isSpace(codePoint: Int): Boolean =
        Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)

    private fun isAsciiLetter(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z'

    private const val SYMBOLS = "@"
    private const val DIGITS = "0123456789"
    private const val LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
}
