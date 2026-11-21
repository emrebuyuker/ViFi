package com.salticusteam.vifi.feature.conversionadmin

import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.ConversionUsage
import com.salticusteam.vifi.core.model.SubjectCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pure helpers of the admin conversion screens: dollars, row texts, the subject picker's search rules. */
class ConversionAdminSupportTest {

    @Test
    fun `dollars use two decimals with the Turkish comma`() {
        assertEquals("\$0,42", 0.42.toUsd())
        assertEquals("\$50,00", 50.0.toUsd())
        assertEquals("\$12,40", 12.4.toUsd())
    }

    @Test
    fun `a job row shows the exam, its place and its cost`() {
        val job = ConversionFixture.job()

        assertEquals("2023 VİZE", job.examTitle)
        assertEquals("Uni › Fak › Böl › Ders", job.placementPath)
        assertEquals("\$0,42", job.costText)
        assertNull(job.copy(usage = null).costText)
        assertEquals("sub-s1", job.copy(placementNames = emptyList()).examTitle)
        assertEquals("", job.copy(placementNames = listOf("Sadece")).placementPath)
        assertEquals(ConversionUsage(100, 50, 0.42), job.usage)
    }

    private fun subject(id: String, name: String) = AdminSubject(id, name, listOf(id), emptyList())

    private val subjects = listOf(subject("algoritmalar", "Algoritmalar"), subject("veri", "Veri Yapıları"))

    @Test
    fun `the picker search ignores case and diacritics and shows everything for a blank query`() {
        assertEquals(2, SubjectPickerLogic.matches(subjects, "  ").size)
        assertEquals(listOf("veri"), SubjectPickerLogic.matches(subjects, "YAPILARI").map { it.id })
        assertEquals(listOf("algoritmalar"), SubjectPickerLogic.matches(subjects, "algo").map { it.id })
        assertEquals(emptyList<AdminSubject>(), SubjectPickerLogic.matches(subjects, "yok"))
    }

    @Test
    fun `the picker offers a new subject only for a name no subject already has`() {
        assertNull(SubjectPickerLogic.newName(subjects, "   "))
        assertNull("Same comparable name", SubjectPickerLogic.newName(subjects, "VERİ YAPILARI"))
        assertEquals("Kuantum Fiziği", SubjectPickerLogic.newName(subjects, "  Kuantum Fiziği "))
    }

    @Test
    fun `courses are grouped by university, universities and courses by name`() {
        fun course(id: String, name: String, university: String, universityName: String) =
            SubjectCourse(id, name, university, universityName, "F", "B", 1)
        val groups = universityGroups(
            listOf(
                course("1", "Fizik", "itu", "İstanbul Teknik"),
                course("2", "Çizge", "ege", "Ege"),
                course("3", "Algebra", "ege", "Ege"),
            ),
        )

        assertEquals(listOf("ege", "itu"), groups.map { it.universityId })
        assertEquals(listOf("Algebra", "Çizge"), groups.first().courses.map { it.name })
    }
}
