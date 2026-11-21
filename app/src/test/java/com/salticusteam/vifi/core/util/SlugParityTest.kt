package com.salticusteam.vifi.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `SlugTests`, case for case: `ExamTitleParser` and the catalog ids depend on the same slug on both platforms. */
class SlugParityTest {

    @Test
    fun `Turkish names become lowercase ASCII slugs`() {
        listOf(
            "Sakarya Üniversitesi" to "sakarya-universitesi",
            "Boğaziçi Üniversitesi" to "bogazici-universitesi",
            "İstanbul Teknik Üniversitesi" to "istanbul-teknik-universitesi",
            "İşletme" to "isletme",
            "Mühendislik-Mimarlık Fakültesi" to "muhendislik-mimarlik-fakultesi",
            "Fen-Edebiyat Fakültesi" to "fen-edebiyat-fakultesi",
            "Çaycuma" to "caycuma",
        ).forEach { (input, expected) -> assertEquals(input, expected, Slug.make(input)) }
    }

    @Test
    fun `separators collapse and do not lead or trail`() {
        assertEquals("bos-koy", Slug.make("  Boş   Köy  "))
        assertEquals("a-b-c", Slug.make("A, B & C"))
        assertEquals("x", Slug.make("---x---"))
    }

    @Test
    fun `a value with no usable characters falls back to a placeholder`() {
        assertEquals("unknown", Slug.make(""))
        assertEquals("unknown", Slug.make("   "))
        assertEquals("unknown", Slug.make("..."))
    }
}
