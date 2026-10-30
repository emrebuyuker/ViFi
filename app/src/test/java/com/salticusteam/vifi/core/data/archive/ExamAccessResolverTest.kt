package com.salticusteam.vifi.core.data.archive

import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** iOS `ExamAccessResolverTests` (8) plus a deleted course. */
class ExamAccessResolverTest {

    private val repository = FakeArchiveRepository()
    private val sut = ExamAccessResolver(repository)

    // The fake's lesson, newest first: 2023-vize, 2022-final (locked), 2021-vize, 2020-final (the free two).
    private val lesson = ArchivePath.ROOT.appending("itu", "İTÜ").appending("muh", "Müh").appending("blg", "Blg")
        .appending("blg101", "BLG 101")
    private fun exam(id: String) = lesson.appending(id, id)

    @Test
    fun `the free two are the oldest, and subscribers and unlocked exams are never locked`() = runTest {
        assertEquals(ExamAccess.Open, sut.access(exam("2020-final"), isSubscribed = false, unlockedIds = emptySet()))
        assertEquals(ExamAccess.Open, sut.access(exam("2021-vize"), isSubscribed = false, unlockedIds = emptySet()))
        assertTrue(sut.access(exam("2023-vize"), isSubscribed = false, unlockedIds = emptySet()) is ExamAccess.Locked)
        assertEquals(ExamAccess.Open, sut.access(exam("2023-vize"), isSubscribed = true, unlockedIds = emptySet()))
        assertEquals(ExamAccess.Open, sut.access(exam("2023-vize"), isSubscribed = false, unlockedIds = setOf("2023-vize")))
    }

    @Test
    fun `a subscriber opens without loading the course`() = runTest {
        assertEquals(ExamAccess.Open, sut.access(exam("2023-vize"), isSubscribed = true, unlockedIds = emptySet()))

        assertEquals(0, repository.itemsCallCount)
    }

    @Test
    fun `an exam already unlocked opens without loading the course`() = runTest {
        assertEquals(ExamAccess.Open, sut.access(exam("2022-final"), isSubscribed = false, unlockedIds = setOf("2022-final")))

        assertEquals(0, repository.itemsCallCount)
    }

    @Test
    fun `a free exam opens, judged from the full course list`() = runTest {
        assertEquals(ExamAccess.Open, sut.access(exam("2020-final"), isSubscribed = false, unlockedIds = emptySet()))

        assertEquals(1, repository.itemsCallCount)
        assertEquals(lesson, repository.lastRequestedPath)
    }

    @Test
    fun `a locked exam returns the archive's own item`() = runTest {
        val access = sut.access(exam("2022-final"), isSubscribed = false, unlockedIds = emptySet())

        val locked = access as ExamAccess.Locked
        assertEquals(exam("2022-final"), locked.exam.path)
        assertEquals("2022 Final", locked.exam.path.displayName)
        assertEquals(ExamKind.IMAGES, locked.exam.exam?.kind)
    }

    @Test
    fun `a deleted or file-less exam is unavailable, and file-less exams still take free slots`() = runTest {
        val course = lesson
        val listed = StubArchive(
            mapOf(
                course to listOf(
                    examItem(course, "new", hasFiles = true),
                    examItem(course, "empty", hasFiles = false),
                    examItem(course, "old", hasFiles = true),
                ),
            ),
        )
        val resolver = ExamAccessResolver(listed)

        assertEquals(ExamAccess.Unavailable, resolver.access(exam("gone"), isSubscribed = false, unlockedIds = emptySet()))
        assertEquals(ExamAccess.Unavailable, resolver.access(exam("empty"), isSubscribed = false, unlockedIds = emptySet()))
        assertEquals(ExamAccess.Open, resolver.access(exam("old"), isSubscribed = false, unlockedIds = emptySet()))
        assertTrue(resolver.access(exam("new"), isSubscribed = false, unlockedIds = emptySet()) is ExamAccess.Locked)
    }

    @Test
    fun `a path that is not an exam is unavailable without a request`() = runTest {
        assertEquals(ExamAccess.Unavailable, sut.access(lesson, isSubscribed = false, unlockedIds = emptySet()))
        assertEquals(ExamAccess.Unavailable, sut.access(ArchivePath.ROOT, isSubscribed = true, unlockedIds = emptySet()))

        assertEquals(0, repository.itemsCallCount)
    }

    @Test
    fun `a failed course read throws the archive's error`() = runTest {
        repository.error = ArchiveError.Offline

        try {
            sut.access(exam("2023-vize"), isSubscribed = false, unlockedIds = emptySet())
            fail("Expected the read to fail")
        } catch (e: ArchiveError.Offline) {
            assertEquals(1, repository.itemsCallCount)
        }
    }

    @Test
    fun `a course that no longer exists is unavailable`() = runTest {
        val elsewhere = ArchivePath.ROOT.appending("x").appending("y").appending("z").appending("gone").appending("e1")

        assertEquals(ExamAccess.Unavailable, sut.access(elsewhere, isSubscribed = false, unlockedIds = emptySet()))
    }

    private fun examItem(course: ArchivePath, id: String, hasFiles: Boolean) = ArchiveItem(
        path = course.appending(id, id),
        level = ArchiveLevel.EXAM,
        childCount = null,
        exam = if (hasFiles) ExamSummary(ExamKind.PDF, 1) else null,
    )

    private class StubArchive(private val children: Map<ArchivePath, List<ArchiveItem>>) : ArchiveRepository {
        override suspend fun items(path: ArchivePath): List<ArchiveItem> = children[path] ?: throw ArchiveError.NotFound
        override suspend fun document(path: ArchivePath): ExamDocument = throw ArchiveError.NotFound
    }
}
