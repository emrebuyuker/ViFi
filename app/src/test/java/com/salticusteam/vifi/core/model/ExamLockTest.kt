package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamLockTest {

    private val lesson = ArchivePath.ROOT.appending("u").appending("f").appending("d").appending("l")

    private fun exam(id: String) =
        ArchiveItem(lesson.appending(id), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1))

    // Newest first: e4 is newest, e1 is oldest.
    private val ordered = listOf("e4", "e3", "e2", "e1").map(::exam)

    @Test
    fun `freeExamIds returns the two oldest exams`() {
        assertEquals(setOf("e2", "e1"), ExamLock.freeExamIds(ordered))
    }

    @Test
    fun `freeExamIds when fewer exams than free count returns all`() {
        assertEquals(setOf("e1"), ExamLock.freeExamIds(listOf(exam("e1"))))
    }

    @Test
    fun `isAccessible when locked and not unlocked returns false`() {
        val free = ExamLock.freeExamIds(ordered)

        assertFalse(ExamLock.isAccessible(exam("e4"), free, emptySet()))
        assertTrue(ExamLock.isAccessible(exam("e4"), free, setOf("e4")))
        assertTrue(ExamLock.isAccessible(exam("e1"), free, emptySet()))
    }

    @Test
    fun `isAccessible when item is not an exam returns true`() {
        val lessonItem = ArchiveItem(lesson, ArchiveLevel.LESSON, 2, null)

        assertTrue(ExamLock.isAccessible(lessonItem, emptySet(), emptySet()))
    }

    @Test
    fun `unlock cost matches the cloud function`() {
        assertEquals(100, ExamLock.UNLOCK_COST)
    }
}
