package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePathTest {

    private val lesson = ArchivePath.ROOT
        .appending(id = "itu", displayName = "İstanbul Teknik Üniversitesi")
        .appending(id = "muh", displayName = "Mühendislik")
        .appending(id = "blg", displayName = "Bilgisayar")
        .appending(id = "blg101", displayName = "BLG 101")

    @Test
    fun `depth and levels follow the drill-down order`() {
        assertEquals(4, lesson.depth)
        assertEquals(ArchiveLevel.LESSON, lesson.level)
        assertEquals(ArchiveLevel.EXAM, lesson.childLevel)
        assertNull(ArchivePath.ROOT.level)
        assertEquals(ArchiveLevel.UNIVERSITY, ArchivePath.ROOT.childLevel)
    }

    @Test
    fun `prefix and parent keep ids and display names aligned`() {
        val faculty = lesson.prefix(2)

        assertEquals(listOf("itu", "muh"), faculty.components)
        assertEquals(listOf("İstanbul Teknik Üniversitesi", "Mühendislik"), faculty.displayNames)
        assertEquals(lesson.prefix(3), lesson.parent)
        assertNull(ArchivePath.ROOT.parent)
        assertTrue(lesson.prefix(0).isRoot)
    }

    @Test
    fun `equality uses ids only`() {
        val renamed = ArchivePath(listOf("itu"), listOf("İTÜ"))

        assertEquals(ArchivePath(listOf("itu"), listOf("İstanbul Teknik Üniversitesi")), renamed)
        assertEquals(ArchivePath(listOf("itu")).hashCode(), renamed.hashCode())
        assertNotEquals(ArchivePath(listOf("odtu")), renamed)
    }

    @Test
    fun `display names fall back to ids when lengths differ`() {
        val path = ArchivePath(listOf("itu", "muh"), listOf("İTÜ"))

        assertEquals(listOf("itu", "muh"), path.displayNames)
        assertEquals("muh", path.displayName)
    }

    @Test
    fun `exam kind maps backend raw values`() {
        assertEquals(ExamKind.PDF, ExamKind.fromRaw("PDF"))
        assertEquals(ExamKind.IMAGES, ExamKind.fromRaw("JPG"))
        assertNull(ExamKind.fromRaw("DOC"))
        assertNull(ExamKind.fromRaw(null))
    }
}
