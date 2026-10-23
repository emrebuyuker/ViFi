package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveSortTest {

    @Test
    fun `sorted when catalog level orders in Turkish alphabet with numeric awareness`() {
        val names = listOf("10. Sınıf", "Çankaya", "2. Sınıf", "Cumhuriyet")

        val sorted = ArchiveSort.sorted(items(names, ArchiveLevel.DEPARTMENT), ArchiveLevel.DEPARTMENT)

        assertEquals(listOf("2. Sınıf", "10. Sınıf", "Cumhuriyet", "Çankaya"), sorted.map { it.name })
    }

    @Test
    fun `sorted when exam level lists newest first`() {
        val sorted = ArchiveSort.sorted(items(listOf("2002 VİZE", "2019 FİNAL"), ArchiveLevel.EXAM), ArchiveLevel.EXAM)

        assertEquals(listOf("2019 FİNAL", "2002 VİZE"), sorted.map { it.name })
    }

    @Test
    fun `sorted places Turkish letters after their Latin base`() {
        val names = listOf("Şırnak", "Sakarya", "Ömer Halisdemir", "Osmaniye", "Uşak", "Ünye", "Iğdır", "İnönü", "Gümüşhane", "Ğ")

        val sorted = ArchiveSort.sorted(items(names, ArchiveLevel.UNIVERSITY), ArchiveLevel.UNIVERSITY).map { it.name }

        assertTrue(sorted.indexOf("Sakarya") < sorted.indexOf("Şırnak"))
        assertTrue(sorted.indexOf("Osmaniye") < sorted.indexOf("Ömer Halisdemir"))
        assertTrue(sorted.indexOf("Uşak") < sorted.indexOf("Ünye"))
        assertTrue(sorted.indexOf("Iğdır") < sorted.indexOf("İnönü"))
    }

    @Test
    fun `nameComparator is case insensitive and compares numbers by value`() {
        val comparator = ArchiveSort.nameComparator()

        assertEquals(0, comparator.compare("KOCAELİ ÜNİVERSİTESİ", "kocaeli üniversitesi"))
        assertTrue(comparator.compare("MAT 9", "MAT 101") < 0)
        assertTrue(comparator.compare("Sınıf 007", "Sınıf 10") < 0)
        assertTrue(comparator.compare("BLG", "BLG 101") < 0)
        assertTrue(comparator.compare("99999999999999999999", "100000000000000000000") < 0)
    }

    @Test
    fun `sorted keeps the input order of equal names`() {
        val lesson = ArchivePath.ROOT.appending("a", "A")
        val first = ArchiveItem(lesson.appending("x", "Vize"), ArchiveLevel.FACULTY, null, null)
        val second = ArchiveItem(lesson.appending("y", "VİZE"), ArchiveLevel.FACULTY, null, null)

        assertEquals(listOf(first, second), ArchiveSort.sorted(listOf(first, second), ArchiveLevel.FACULTY))
    }

    private fun items(names: List<String>, level: ArchiveLevel): List<ArchiveItem> =
        names.mapIndexed { index, name ->
            ArchiveItem(ArchivePath.ROOT.appending("id$index", name), level, null, null)
        }
}
