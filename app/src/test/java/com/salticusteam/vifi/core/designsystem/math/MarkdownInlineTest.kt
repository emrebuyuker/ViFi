package com.salticusteam.vifi.core.designsystem.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The inline Markdown subset of question text (`**`, `*`, `` ` ``, `~~`); block syntax stays plain. */
class MarkdownInlineTest {

    private fun parse(input: String) = MarkdownInline.parse(input)

    @Test
    fun `plain text is one plain span and empty input has none`() {
        assertEquals(listOf(MarkdownSpan("Bir soru")), parse("Bir soru"))
        assertTrue(parse("").isEmpty())
    }

    @Test
    fun `bold italic strikethrough and code`() {
        assertEquals(
            listOf(MarkdownSpan("a "), MarkdownSpan("b", bold = true), MarkdownSpan(" c")),
            parse("a **b** c"),
        )
        assertEquals(
            listOf(MarkdownSpan("a "), MarkdownSpan("b", italic = true), MarkdownSpan(" c")),
            parse("a *b* c"),
        )
        assertEquals(
            listOf(MarkdownSpan("a "), MarkdownSpan("b", strikethrough = true), MarkdownSpan(" c")),
            parse("a ~~b~~ c"),
        )
        assertEquals(
            listOf(MarkdownSpan("a "), MarkdownSpan("b", code = true), MarkdownSpan(" c")),
            parse("a `b` c"),
        )
    }

    @Test
    fun `emphasis nests`() {
        assertEquals(listOf(MarkdownSpan("x", bold = true, italic = true)), parse("***x***"))
        assertEquals(
            listOf(
                MarkdownSpan("a ", bold = true),
                MarkdownSpan("b", bold = true, italic = true),
                MarkdownSpan(" c", bold = true),
            ),
            parse("**a *b* c**"),
        )
        assertEquals(
            listOf(
                MarkdownSpan("a ", italic = true),
                MarkdownSpan("b", bold = true, italic = true),
                MarkdownSpan(" c", italic = true),
            ),
            parse("*a **b** c*"),
        )
    }

    @Test
    fun `code is not parsed further`() {
        assertEquals(listOf(MarkdownSpan("**x**", code = true)), parse("`**x**`"))
    }

    @Test
    fun `markers around spaces or without a partner stay text`() {
        assertEquals(listOf(MarkdownSpan("2 * 3 * 4")), parse("2 * 3 * 4"))
        assertEquals(listOf(MarkdownSpan("a **b")), parse("a **b"))
        assertEquals(listOf(MarkdownSpan("a `b")), parse("a `b"))
        assertEquals(listOf(MarkdownSpan("~a~")), parse("~a~"))
        assertEquals(listOf(MarkdownSpan("** x **")), parse("** x **"))
    }

    @Test
    fun `escaped markers are literal characters`() {
        assertEquals(listOf(MarkdownSpan("*a*")), parse("""\*a\*"""))
        assertEquals(listOf(MarkdownSpan("`a`")), parse("""\`a\`"""))
    }

    @Test
    fun `underscores and block syntax stay plain text`() {
        assertEquals(listOf(MarkdownSpan("snake_case_name")), parse("snake_case_name"))
        assertEquals(listOf(MarkdownSpan("# Başlık\n- madde\n> alıntı\n1. bir")), parse("# Başlık\n- madde\n> alıntı\n1. bir"))
        assertEquals(listOf(MarkdownSpan("[a](b)")), parse("[a](b)"))
    }

    @Test
    fun `line breaks are kept`() {
        assertEquals(
            listOf(MarkdownSpan("bir\niki "), MarkdownSpan("üç", bold = true), MarkdownSpan("\n")),
            parse("bir\niki **üç**\n"),
        )
    }

    @Test
    fun `a backslash that escapes nothing stays`() {
        assertEquals(listOf(MarkdownSpan("""a\b""")), parse("""a\b"""))
    }

    @Test
    fun `plain returns the visible text`() {
        assertEquals("a b c d", MarkdownInline.plain("a **b** *c* ~~d~~"))
    }

    @Test
    fun `parsing never throws on stray markers`() {
        listOf("*", "**", "***", "~~", "~~~~", "`", "``", "****", "*a", "a*", "\\", "**a*", "*a**").forEach { input ->
            val visible = MarkdownInline.plain(input)
            assertTrue(input, visible.length <= input.length)
        }
    }
}
