package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveNameMatchTest {

    @Test
    fun `matches when casing and Turkish letters differ`() {
        assertTrue(ArchiveNameMatch.matches("BOĞAZİÇİ ÜNİVERSİTESİ", "Boğaziçi Üniversitesi"))
        assertTrue(ArchiveNameMatch.matches("Işık Üniversitesi", "ISIK UNIVERSITESI"))
        assertTrue(ArchiveNameMatch.matches("2023 VİZE", "2023 Vize"))
    }

    @Test
    fun `matches when dotless i spelled as i returns true`() {
        assertTrue(ArchiveNameMatch.matches("Fizik Bölümü", "fizik bolumu"))
        assertTrue(ArchiveNameMatch.matches("Kırşehir", "kirsehir"))
    }

    @Test
    fun `matches when names differ returns false`() {
        assertFalse(ArchiveNameMatch.matches("Matematik", "Matematik Mühendisliği"))
    }

    @Test
    fun `matchesDepartment when archive carries a trailing bolum suffix ignores it`() {
        assertTrue(ArchiveNameMatch.matchesDepartment(archive = "MATEMATİK BÖLÜMÜ", profile = "Matematik"))
        assertTrue(ArchiveNameMatch.matchesDepartment(archive = "Fizik Bölüm", profile = "Fizik"))
        assertTrue(ArchiveNameMatch.matchesDepartment(archive = "Bilgisayar Mühendisliği", profile = "BİLGİSAYAR MÜHENDİSLİĞİ"))
    }

    @Test
    fun `matchesDepartment when a distinct word remains returns false`() {
        assertFalse(ArchiveNameMatch.matchesDepartment(archive = "MATEMATİK MÜHENDİSLİĞİ", profile = "Matematik"))
        // Only a whole trailing word is dropped, never a stem.
        assertFalse(ArchiveNameMatch.matchesDepartment(archive = "Matematikbölümü", profile = "Matematik"))
    }

    @Test
    fun `contains matches a folded substring`() {
        assertTrue(ArchiveNameMatch.contains("BLG 101 Bilgisayar Bilimine Giriş", "giris"))
        assertFalse(ArchiveNameMatch.contains("BLG 101", "mat"))
    }

    @Test
    fun `key folds to plain lowercase ascii letters`() {
        assertEquals("cagri ozguven", ArchiveNameMatch.key("ÇAĞRI ÖZGÜVEN"))
    }

    @Test
    fun `exam name catalog ids match iOS`() {
        assertEquals(
            listOf("vize", "final", "butunleme", "mazeret", "kisa-sinav", "yaz-okulu", "ara-sinav"),
            ExamNameCatalog.names.map { it.id },
        )
        assertEquals("VİZE", ExamNameCatalog.names.first().name)
    }
}
