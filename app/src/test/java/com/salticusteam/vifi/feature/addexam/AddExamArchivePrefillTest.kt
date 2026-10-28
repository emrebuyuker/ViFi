package com.salticusteam.vifi.feature.addexam

import com.salticusteam.vifi.core.data.catalog.UniversityCatalog
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AddExamArchivePrefillTest {

    private val sakarya = CatalogItem("sakarya-universitesi", "Sakarya Üniversitesi")
    private val engineering = CatalogItem("muhendislik-fakultesi", "Mühendislik Fakültesi")
    private val engineeringAndScience = CatalogItem("muhendislik-ve-doga-bilimleri", "Mühendislik ve Doğa Bilimleri Fakültesi")
    private val electrical = CatalogItem("elektrik-elektronik-muhendisligi", "Elektrik Elektronik Mühendisliği")

    private val catalog = UniversityCatalog(
        universities = listOf(CatalogItem("sakarya-uygulamali", "Sakarya Uygulamalı Bilimler Üniversitesi"), sakarya),
        // The longer name first: a faculty must match as a whole name, not by prefix.
        faculties = listOf(engineeringAndScience, engineering),
        departments = listOf(CatalogItem("elektrik-muhendisligi", "Elektrik Mühendisliği"), electrical),
    )

    /** The field report's lesson, spelled as archive names usually are (upper case, suffixes). */
    private fun lesson(faculty: String = "MÜHENDİSLİK FAKÜLTESİ") = ArchivePath.ROOT
        .appending("sau", "SAKARYA ÜNİVERSİTESİ")
        .appending("muh", faculty)
        .appending("eem", "ELEKTRİK ELEKTRONİK MÜHENDİSLİĞİ BÖLÜMÜ")
        .appending("dif", "DİFERANSİYEL DENKLEMLER")

    private val emptyForm = AddExamUiState()

    @Test
    fun `withArchiveSchool when every level is listed picks the bundled items`() {
        val state = emptyForm.withArchiveSchool(lesson(), catalog)

        assertEquals(CatalogSelection.catalog(sakarya), state.university)
        assertEquals(CatalogSelection.catalog(engineering), state.faculty)
        assertEquals(CatalogSelection.catalog(electrical), state.department)
    }

    @Test
    fun `withArchiveSchool when the archive faculty omits fakültesi still matches it`() {
        val state = emptyForm.withArchiveSchool(lesson(faculty = "MÜHENDİSLİK"), catalog)

        assertEquals(CatalogSelection.catalog(engineering), state.faculty)
    }

    @Test
    fun `withArchiveSchool when a level is not listed leaves it empty`() {
        val state = emptyForm.withArchiveSchool(lesson(faculty = "TEKNOLOJİ FAKÜLTESİ"), catalog)

        assertTrue(state.faculty.isEmpty)
        assertEquals(CatalogSelection.catalog(sakarya), state.university)
    }

    @Test
    fun `withArchiveSchool when a row is already filled keeps it`() {
        val picked = CatalogSelection.custom("Kendi Üniversitem")

        val state = emptyForm.copy(university = picked).withArchiveSchool(lesson(), catalog)

        assertSame(picked, state.university)
        assertEquals(CatalogSelection.catalog(engineering), state.faculty)
    }

    @Test
    fun `withArchiveSchool when the bundled lists are empty changes nothing`() {
        val state = emptyForm.withArchiveSchool(lesson(), UniversityCatalog.EMPTY)

        assertEquals(emptyForm, state)
    }

    @Test
    fun `withArchiveLesson picks the archive lesson as from the lesson list`() {
        val state = emptyForm.withArchiveLesson(lesson())

        assertEquals(CatalogSelection.archive("DİFERANSİYEL DENKLEMLER"), state.lesson)
        assertEquals(false, state.lesson.isCustom)
    }

    @Test
    fun `withArchiveLesson when the path stops above a lesson changes nothing`() {
        val department = lesson().prefix(3)

        assertEquals(emptyForm, emptyForm.withArchiveLesson(department))
    }
}
