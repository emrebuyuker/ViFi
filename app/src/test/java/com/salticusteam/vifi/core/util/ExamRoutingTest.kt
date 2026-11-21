package com.salticusteam.vifi.core.util

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamLock
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.core.navigation.ViFiRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `ExamRoutingTests` (the router / view-model cases belong to SQS-01). */
class ExamRoutingTest {

    private val course = ArchivePath(listOf("u", "f", "d", "c"), listOf("U", "F", "D", "C"))
    private val path = course.appending("e", "2019 FİNAL")

    @Test
    fun `a structured exam always opens the question screen`() {
        assertEquals(ViFiRoute.StructuredExam(path, ExamKind.IMAGES), ExamRouting.route(path, ExamKind.IMAGES, 12))
    }

    @Test
    fun `no questions keeps the file viewer`() {
        for (count in listOf(null, 0)) {
            assertEquals(ViFiRoute.Exam(path, ExamKind.PDF), ExamRouting.route(path, ExamKind.PDF, count))
        }
    }

    @Test
    fun `a list item routes by its summary`() {
        val structured = ArchiveItem(path, ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 3, questionCount = 5))
        val plain = ArchiveItem(path, ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 3))
        val empty = ArchiveItem(path, ArchiveLevel.EXAM, null, null)

        assertEquals(ViFiRoute.StructuredExam(path, ExamKind.IMAGES), ExamRouting.route(structured))
        assertEquals(ViFiRoute.Exam(path, ExamKind.IMAGES), ExamRouting.route(plain))
        assertNull(ExamRouting.route(empty))
    }

    @Test
    fun `the file viewer redirects once it sees the exam is structured`() {
        val structured = ExamDocument(path, ExamKind.IMAGES, emptyList(), questionCount = 3)
        val plain = ExamDocument(path, ExamKind.IMAGES, listOf("https://example.com/1.jpg"))

        assertEquals(ViFiRoute.StructuredExam(path, ExamKind.IMAGES), ExamRouting.redirect(structured))
        assertNull(ExamRouting.redirect(plain))
    }

    @Test
    fun `the lock rule is unchanged - a structured exam is locked like any other`() {
        fun exam(id: String, questions: Int?) =
            ArchiveItem(course.appending(id, id), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 1, questionCount = questions))
        val exams = listOf(exam("new", 5), exam("mid", null), exam("old-1", 5), exam("old-2", 5))
        val free = ExamLock.freeExamIds(exams)

        assertTrue(ExamLock.isLocked(exams[0], free, unlockedIds = emptySet()))
        assertFalse(ExamLock.isLocked(exams[0], free, unlockedIds = setOf("new")))
        assertFalse(ExamLock.isLocked(exams[2], free, unlockedIds = emptySet()))
    }
}
