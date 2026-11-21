package com.salticusteam.vifi.feature.practice

import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakePoolIndexReader
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a course offers "Sınava gir" (iOS `PracticeEntryViewModelTests`). */
class PracticeEntryViewModelTest {

    private val course = ArchivePath(listOf("u", "f", "d", "c1"))

    /** An archive that only knows one course's subject. */
    private class SubjectRepository(private val subjectId: String?) : ArchiveRepository {
        override suspend fun items(path: ArchivePath): List<ArchiveItem> = emptyList()
        override suspend fun document(path: ArchivePath): ExamDocument = throw ArchiveError.NotFound
        override suspend fun subjectId(forCourse: ArchivePath): String? = subjectId
    }

    private fun makeViewModel(subjectId: String?, reader: FakePoolIndexReader) =
        PracticeEntryViewModel(SubjectRepository(subjectId), reader)

    @Test
    fun `a course with a subject and questions shows the entry, reading the index once`() = runTest {
        val reader = FakePoolIndexReader(mapOf("s1" to PracticeFixture.index(mapOf(ExamType.VIZE to mapOf("tr" to 1)))))
        val sut = makeViewModel("s1", reader)

        sut.load(course)
        sut.load(course)

        assertTrue(sut.uiState.value.isAvailable)
        assertEquals("s1", sut.uiState.value.subjectId)
        assertEquals(1, reader.readCount)
    }

    @Test
    fun `no subject, no index or a failing read hides the entry`() = runTest {
        val noSubject = FakePoolIndexReader(emptyMap())
        val noIndex = FakePoolIndexReader(emptyMap())
        val failing = FakePoolIndexReader(emptyMap(), error = ArchiveError.Offline)

        val first = makeViewModel(null, noSubject)
        val second = makeViewModel("s1", noIndex)
        val third = makeViewModel("s1", failing)
        listOf(first, second, third).forEach { it.load(course) }

        assertFalse(first.uiState.value.isAvailable)
        assertFalse(second.uiState.value.isAvailable)
        assertFalse(third.uiState.value.isAvailable)
        assertEquals(0, noSubject.readCount)
        assertNull(third.uiState.value.subjectId)
    }

    @Test
    fun `only a course reads anything`() = runTest {
        val reader = FakePoolIndexReader(mapOf("s1" to PracticeFixture.index(mapOf(ExamType.VIZE to mapOf("tr" to 1)))))
        val sut = makeViewModel("s1", reader)

        sut.load(ArchivePath(listOf("u", "f")))

        assertFalse(sut.uiState.value.isAvailable)
        assertEquals(0, reader.readCount)
    }

    @Test
    fun `a failed read is tried again the next time`() = runTest {
        val reader = FakePoolIndexReader(
            mapOf("s1" to PracticeFixture.index(mapOf(ExamType.VIZE to mapOf("tr" to 1)))),
            error = ArchiveError.Offline,
        )
        val sut = makeViewModel("s1", reader)

        sut.load(course)
        reader.error = null
        sut.load(course)

        assertTrue(sut.uiState.value.isAvailable)
        assertEquals(2, reader.readCount)
    }
}
