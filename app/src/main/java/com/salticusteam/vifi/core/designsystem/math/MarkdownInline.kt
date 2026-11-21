package com.salticusteam.vifi.core.designsystem.math

// Added in 4.2.0 — a run of text with one set of inline Markdown styles (iOS `FormulaText.markdown` runs).
data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
) {
    val isPlain: Boolean get() = !bold && !italic && !strikethrough && !code
}

// Added in 4.2.0 — the inline Markdown subset of question text.
/**
 * `**bold**`, `*italic*`, `` `code` ``, `~~strikethrough~~` and their nesting (`***both***`, `**a *b* c**`); every
 * other character, block syntax (`#`, `-`, `>`, `1.`), links and line breaks included, stays plain text. The rules are
 * CommonMark's flanking rules in short: an opening marker must be followed by a non-space and a closing one must
 * follow a non-space, so `2 * 3 * 4` stays text; a marker without a closing partner stays text; `\*`, `` \` `` and
 * `\~` are literal characters; code is not parsed further. Underscores are not markers (`snake_case` stays text).
 * Pure; never throws; adjacent spans of equal style are merged, so the spans joined give the visible text.
 */
object MarkdownInline {

    fun parse(input: String): List<MarkdownSpan> {
        val spans = mutableListOf<MarkdownSpan>()
        parseInto(input, MarkdownSpan(""), spans)
        return merge(spans)
    }

    /** The visible text of [input] (markers removed). */
    fun plain(input: String): String = parse(input).joinToString("") { it.text }

    // Parsing

    private fun parseInto(text: String, style: MarkdownSpan, out: MutableList<MarkdownSpan>) {
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                out += style.copy(text = buffer.toString())
                buffer.clear()
            }
        }

        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\\' && index + 1 < text.length && text[index + 1] in ESCAPABLE -> {
                    buffer.append(text[index + 1])
                    index += 2
                }
                char == '`' -> {
                    val close = findClosingBacktick(text, index)
                    if (close == null) {
                        buffer.append(char)
                        index += 1
                    } else {
                        flush()
                        out += style.copy(text = text.substring(index + 1, close), code = true)
                        index = close + 1
                    }
                }
                char == '*' || char == '~' -> {
                    val consumed = tryEmphasis(text, index, style, out, ::flush)
                    if (consumed == 0) {
                        buffer.append(char)
                        index += 1
                    } else {
                        index += consumed
                    }
                }
                else -> {
                    buffer.append(char)
                    index += 1
                }
            }
        }
        flush()
    }

    /**
     * Parses an emphasis run starting at [start]; returns the number of characters consumed (marker, content and
     * closing marker), or 0 when the marker here opens nothing.
     */
    private fun tryEmphasis(
        text: String,
        start: Int,
        style: MarkdownSpan,
        out: MutableList<MarkdownSpan>,
        flush: () -> Unit,
    ): Int {
        val marker = markerAt(text, start) ?: return 0
        val contentStart = start + marker.length
        if (contentStart >= text.length || text[contentStart].isWhitespace()) return 0
        val close = findClosing(text, contentStart, marker) ?: return 0
        flush()
        val inner = text.substring(contentStart, close)
        val nested = when (marker) {
            "***" -> style.copy(bold = true, italic = true)
            "**" -> style.copy(bold = true)
            "*" -> style.copy(italic = true)
            else -> style.copy(strikethrough = true)
        }
        parseInto(inner, nested, out)
        return close + marker.length - start
    }

    /** The longest marker (`***`, `**`, `*`, `~~`) at [index], or `null`. */
    private fun markerAt(text: String, index: Int): String? = when {
        text.startsWith("***", index) -> "***"
        text.startsWith("**", index) -> "**"
        text.startsWith("~~", index) -> "~~"
        text[index] == '*' -> "*"
        else -> null
    }

    /**
     * The index of the first valid closing [marker] at or after [from]: preceded by a non-space, not escaped, outside
     * code spans, and (for `*`) not part of a longer run of asterisks.
     */
    private fun findClosing(text: String, from: Int, marker: String): Int? {
        var index = from
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\\' && index + 1 < text.length -> index += 2
                char == '`' -> index = findClosingBacktick(text, index)?.plus(1) ?: (index + 1)
                // A bold run inside italic (`*a **b** c*`) is skipped whole, so its markers never close the italic.
                marker == "*" && text.startsWith("**", index) -> index += 2
                text.startsWith(marker, index) && isValidCloser(text, index, marker) -> return index
                else -> index += 1
            }
        }
        return null
    }

    private fun isValidCloser(text: String, index: Int, marker: String): Boolean {
        if (index == 0 || text[index - 1].isWhitespace()) return false
        if (marker == "*") {
            // A single `*` must not be the start of a `**` run (that closes/opens bold, handled by nesting).
            val next = index + 1
            if (next < text.length && text[next] == '*') return false
        }
        return true
    }

    private fun findClosingBacktick(text: String, open: Int): Int? {
        val close = text.indexOf('`', open + 1)
        return if (close > open + 1) close else null
    }

    /** Merges neighbours of equal style and drops empty spans. */
    private fun merge(spans: List<MarkdownSpan>): List<MarkdownSpan> {
        val merged = mutableListOf<MarkdownSpan>()
        for (span in spans) {
            if (span.text.isEmpty()) continue
            val last = merged.lastOrNull()
            if (last != null && last.copy(text = "") == span.copy(text = "") && !span.code) {
                merged[merged.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                merged += span
            }
        }
        return merged
    }

    private const val ESCAPABLE = "*`~"
}
