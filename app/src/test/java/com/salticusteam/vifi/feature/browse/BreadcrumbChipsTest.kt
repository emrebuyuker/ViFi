package com.salticusteam.vifi.feature.browse

import com.salticusteam.vifi.core.model.ArchivePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreadcrumbChipsTest {

    private val university = ArchivePath.ROOT.appending("dpu", "KÜTAHYA DUMLUPINAR ÜNİVERSİTESİ")
    private val faculty = university.appending("fen", "FEN EDEBİYAT FAKÜLTESİ")
    private val department = faculty.appending("fiz", "FİZİK BÖLÜMÜ")

    @Test
    fun `breadcrumbChips when at a department lists root then every level`() {
        val chips = breadcrumbChips(department)

        assertEquals(listOf(0, 1, 2, 3), chips.map { it.index })
        assertEquals(
            listOf(null, "KÜTAHYA DUMLUPINAR ÜNİVERSİTESİ", "FEN EDEBİYAT FAKÜLTESİ", "FİZİK BÖLÜMÜ"),
            chips.map { it.title },
        )
    }

    @Test
    fun `breadcrumbChips when tapped targets the ancestor prefix`() {
        val chips = breadcrumbChips(department)

        assertEquals(listOf(ArchivePath.ROOT, university, faculty, department), chips.map { it.target })
        assertTrue(chips.first().target.isRoot)
    }

    @Test
    fun `breadcrumbChips when built marks only the last chip current`() {
        val chips = breadcrumbChips(department)

        assertEquals(listOf(false, false, false, true), chips.map { it.isCurrent })
    }

    @Test
    fun `breadcrumbChips when at a university has root and current chip`() {
        val chips = breadcrumbChips(university)

        assertEquals(2, chips.size)
        assertNull(chips[0].title)
        assertFalse(chips[0].isCurrent)
        assertTrue(chips[1].isCurrent)
    }

    @Test
    fun `breadcrumbChips when path keys differ from names shows display names`() {
        val chips = breadcrumbChips(faculty)

        assertEquals("FEN EDEBİYAT FAKÜLTESİ", chips.last().title)
        assertEquals(listOf("dpu", "fen"), chips.last().target.components)
    }
}
