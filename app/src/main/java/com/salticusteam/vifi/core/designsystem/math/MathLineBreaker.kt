package com.salticusteam.vifi.core.designsystem.math

// Added in 4.2.0 — splits a formula into pieces so one wider than its line wraps like words (iOS `MathLineBreaker`).
/**
 * AndroidMath typesets a formula as one unbreakable box; the text layer draws each piece as its own inline box with a
 * line-break opportunity (a text space) between them.
 *
 * Breaks only at the formula's top level: never inside `{…}` groups such as `\frac{…}{…}` or `\text{…}`, inside
 * `\left…\right`, inside `\begin…\end` environments, or inside `(…)` / `[…]` (a pair `(a,k)` or an interval stays
 * whole). Set braces `\{…\}` do not protect their content, so a long set can wrap between its elements:
 * - after a comma;
 * - around a relation (`=`, `<`, `>`, `\leq`, `\in`, `\subset`, `\Rightarrow` …) or a binary operator (`+`, `-`,
 *   `\times`, `\cup` …), which becomes a piece of its own. A `+` / `-` only counts after an operand, so a sign
 *   (`= -1`, `x^{-1}`, `(-a`) never moves away from its number.
 *
 * Pieces are trimmed: a typeset image loses the space at its edges anyway, so the drawing side joins pieces with a
 * text space (the line-break opportunity), standing for the comma's or operator's own spacing.
 *
 * Pure. Joining the pieces gives the input back, apart from whitespace at the break points. Never throws.
 */
object MathLineBreaker {
    val relations: Set<String> = setOf(
        "leq", "geq", "neq", "ne", "le", "ge", "in", "notin", "ni", "subset", "subseteq", "supset", "supseteq",
        "approx", "equiv", "sim", "simeq", "cong", "propto", "to", "mapsto", "rightarrow", "Rightarrow",
        "Longrightarrow", "leftarrow", "Leftarrow", "Longleftarrow", "leftrightarrow", "Leftrightarrow",
        "Longleftrightarrow", "iff",
    )
    val binaryOperators: Set<String> = setOf(
        "times", "cdot", "cup", "cap", "pm", "mp", "setminus", "div", "oplus", "otimes", "wedge", "vee", "circ",
    )

    private val groupOpeners = setOf("left", "begin")
    private val groupClosers = setOf("right", "end")

    /** One lexical unit of the source. */
    private sealed interface Token {
        val source: String

        /** `\name` (ASCII letters). */
        data class Command(val name: String) : Token {
            override val source: String get() = "\\$name"
        }

        /** `\` followed by one non-letter, e.g. `\{`, `\,`, `\\`. */
        data class Symbol(override val source: String) : Token

        data class Literal(val value: Char) : Token {
            override val source: String get() = value.toString()
            val isSpace: Boolean get() = value.isWhitespace()
        }
    }

    /** The pieces of [latex]; a single piece when it has no top-level break point. */
    fun pieces(latex: String): List<String> {
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var parentheses = 0
        var previous: Token? = null
        var breakAfterComma = false

        fun flush() {
            val piece = current.toString().trim(::isHorizontalSpace)
            if (piece.isNotEmpty()) pieces += piece
            current.clear()
        }

        for (token in tokenize(latex)) {
            val atTopLevel = depth == 0 && parentheses == 0
            val isSpace = token is Token.Literal && token.isSpace
            if (breakAfterComma && !isSpace) {
                breakAfterComma = false
                if (atTopLevel) flush()
            }
            if (atTopLevel && isOperatorBreak(token, previous)) {
                // The operator stands alone; the text spaces around it carry its spacing.
                flush()
                pieces += token.source
                previous = token
                continue
            }
            current.append(token.source)
            depth = maxOf(0, depth + depthChange(token))
            if (depth == 0) parentheses = maxOf(0, parentheses + parenthesisChange(token))
            if (depth == 0 && parentheses == 0 && token == Token.Literal(',')) breakAfterComma = true
            if (!isSpace) previous = token
        }
        flush()
        return pieces.ifEmpty { listOf(latex) }
    }

    /** Swift's `CharacterSet.whitespaces`: tabs and Unicode space separators, not line breaks. */
    private fun isHorizontalSpace(char: Char): Boolean =
        char == '\t' || Character.getType(char) == Character.SPACE_SEPARATOR.toInt()

    private fun isOperatorBreak(token: Token, previous: Token?): Boolean = when (token) {
        is Token.Literal -> when (token.value) {
            '=', '<', '>' -> true
            '+', '-' -> isOperand(previous)
            else -> false
        }
        is Token.Command -> token.name in relations || token.name in binaryOperators
        is Token.Symbol -> false
    }

    /** Whether [token] can end an operand, so a following `+` / `-` is a binary operator, not a sign. */
    private fun isOperand(token: Token?): Boolean = when (token) {
        is Token.Literal -> token.value.isLetter() || token.value.isDigit() || token.value in ")]}|!'"
        is Token.Symbol -> token.source == "\\}" || token.source == "\\|"
        is Token.Command ->
            token.name !in relations && token.name !in binaryOperators &&
                token.name !in groupOpeners && token.name !in groupClosers
        null -> false
    }

    private fun depthChange(token: Token): Int = when (token) {
        is Token.Literal -> when (token.value) {
            '{' -> 1
            '}' -> -1
            else -> 0
        }
        is Token.Command -> when (token.name) {
            in groupOpeners -> 1
            in groupClosers -> -1
            else -> 0
        }
        is Token.Symbol -> 0
    }

    private fun parenthesisChange(token: Token): Int = when {
        token !is Token.Literal -> 0
        token.value == '(' || token.value == '[' -> 1
        token.value == ')' || token.value == ']' -> -1
        else -> 0
    }

    private fun isAsciiLetter(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z'

    private fun tokenize(latex: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < latex.length) {
            val char = latex[index]
            if (char != '\\') {
                tokens += Token.Literal(char)
                index += 1
                continue
            }
            val next = index + 1
            if (next >= latex.length) {
                tokens += Token.Literal(char)
                index = next
                continue
            }
            if (isAsciiLetter(latex[next])) {
                var end = next
                while (end < latex.length && isAsciiLetter(latex[end])) end += 1
                tokens += Token.Command(latex.substring(next, end))
                index = end
            } else {
                // `\` + one code point (a surrogate pair stays whole).
                val end = if (Character.isHighSurrogate(latex[next]) && next + 1 < latex.length) next + 2 else next + 1
                tokens += Token.Symbol(latex.substring(index, end))
                index = end
            }
        }
        return tokens
    }
}
