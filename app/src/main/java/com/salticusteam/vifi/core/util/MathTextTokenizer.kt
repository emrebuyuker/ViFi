package com.salticusteam.vifi.core.util

// Added in 4.2.0 — one run of a question's Markdown + LaTeX text (iOS `MathRun`).
sealed interface MathRun {
    /** Markdown text (rendered by the text layer). */
    data class Text(val text: String) : MathRun

    /** LaTeX between `$…$`, shown inline with the surrounding text. */
    data class InlineMath(val latex: String) : MathRun

    /** LaTeX between `$$…$$`, shown on its own line. */
    data class DisplayMath(val latex: String) : MathRun
}

// Added in 4.2.0 — splits a question's text into text, inline-math and display-math runs (iOS `MathTextTokenizer`).
/**
 * Pandoc's `$` convention, so prices and stray dollar signs stay text: an opening `$` must be followed by a non-space
 * (and not by `$`), a closing `$` must follow a non-space and must not be followed by an ASCII digit ("$5 ve $6" is
 * text, "$x$." is math). `\$` is a literal dollar sign, inline math never crosses a blank line, a `$` without a
 * closing partner stays text, and an empty `$$ $$` is dropped (the text around it joins). Never throws.
 */
object MathTextTokenizer {

    fun tokenize(input: String): List<MathRun> {
        val chars = input
        val runs = mutableListOf<MathRun>()
        val buffer = StringBuilder()
        var index = 0

        fun flush() {
            if (buffer.isEmpty()) return
            val previous = runs.lastOrNull()
            if (previous is MathRun.Text) {
                runs[runs.lastIndex] = MathRun.Text(previous.text + buffer)
            } else {
                runs += MathRun.Text(buffer.toString())
            }
            buffer.clear()
        }

        while (index < chars.length) {
            val char = chars[index]
            if (char == '\\' && index + 1 < chars.length && chars[index + 1] == '$') {
                buffer.append('$')
                index += 2
            } else if (char == '$' && index + 1 < chars.length && chars[index + 1] == '$') {
                val close = closingDisplay(chars, index + 2)
                if (close != null) {
                    val latex = chars.substring(index + 2, close).trim()
                    flush()
                    if (latex.isNotEmpty()) runs += MathRun.DisplayMath(latex)
                    index = close + 2
                } else {
                    buffer.append("$$")
                    index += 2
                }
            } else if (char == '$') {
                val close = closingInline(chars, index)
                if (close != null) {
                    flush()
                    runs += MathRun.InlineMath(chars.substring(index + 1, close))
                    index = close + 1
                } else {
                    buffer.append('$')
                    index += 1
                }
            } else {
                buffer.append(char)
                index += 1
            }
        }
        flush()
        return runs
    }

    /** The text without math delimiters (LaTeX source kept as is): the spoken fallback when no `speech` is stored. */
    fun plainText(input: String): String = tokenize(input).joinToString("") { run ->
        when (run) {
            is MathRun.Text -> run.text
            is MathRun.InlineMath -> run.latex
            is MathRun.DisplayMath -> run.latex
        }
    }

    // Delimiter search

    /** The index of the `$$` closing a display formula that starts at [start], or `null`. */
    private fun closingDisplay(chars: String, start: Int): Int? {
        var index = start
        while (index + 1 < chars.length) {
            if (chars[index] == '\\') {
                index += 2
                continue
            }
            if (chars[index] == '$' && chars[index + 1] == '$') return index
            index += 1
        }
        return null
    }

    /** The index of the `$` closing an inline formula opened at [open], or `null` (Pandoc rules, same paragraph). */
    private fun closingInline(chars: String, open: Int): Int? {
        val first = open + 1
        if (first >= chars.length || chars[first].isWhitespace() || chars[first] == '$') return null
        var index = first
        while (index < chars.length) {
            val char = chars[index]
            if (char == '\\') {
                index += 2
                continue
            }
            if (char == '\n' && index + 1 < chars.length && chars[index + 1] == '\n') return null
            if (char == '$') {
                val followedByDigit = index + 1 < chars.length && chars[index + 1] in '0'..'9'
                return if (!chars[index - 1].isWhitespace() && !followedByDigit) index else null
            }
            index += 1
        }
        return null
    }
}
