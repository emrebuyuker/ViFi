package com.salticusteam.vifi.core.data.repository.fake

import app.cash.turbine.test
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeArchiveRepositoryTest {

    private val sut = FakeArchiveRepository()

    @Test
    fun `items when root returns universities`() = runTest {
        val items = sut.items(ArchivePath.ROOT)

        assertEquals(2, items.size)
        assertTrue(items.all { it.level == ArchiveLevel.UNIVERSITY })
        assertEquals("İstanbul Teknik Üniversitesi", items.first().name)
        assertEquals(1, sut.itemsCallCount)
        assertEquals(ArchivePath.ROOT, sut.lastRequestedPath)
    }

    @Test
    fun `items when drilling down reaches lesson with four exams`() = runTest {
        var path = ArchivePath.ROOT
        repeat(4) { path = sut.items(path).first().path }

        val exams = sut.items(path)

        assertEquals(listOf(ExamKind.PDF, ExamKind.IMAGES, ExamKind.PDF, ExamKind.PDF), exams.map { it.exam?.kind })
        assertTrue(exams.all { it.level == ArchiveLevel.EXAM && it.childCount == null })
    }

    @Test
    fun `document when exam path returns its files`() = runTest {
        var path = ArchivePath.ROOT
        repeat(4) { path = sut.items(path).first().path }
        val exams = sut.items(path)

        val pdf = sut.document(exams[0].path)
        val images = sut.document(exams[1].path)

        assertEquals(1, pdf.fileUrls.size)
        assertEquals(3, images.fileUrls.size)
        assertEquals("2023 Vize", pdf.title)
        assertEquals(2, sut.documentCallCount)
    }

    @Test
    fun `items when unknown path throws NotFound`() = runTest {
        try {
            sut.items(ArchivePath.ROOT.appending("nope"))
            fail("expected NotFound")
        } catch (e: ArchiveError) {
            assertEquals(ArchiveError.NotFound, e)
        }
    }

    @Test
    fun `calls when error configured throw it`() = runTest {
        sut.error = ArchiveError.Offline

        try {
            sut.items(ArchivePath.ROOT)
            fail("expected Offline")
        } catch (e: ArchiveError) {
            assertEquals(ArchiveError.Offline, e)
        }
        try {
            sut.prefetchArchive()
            fail("expected Offline")
        } catch (e: ArchiveError) {
            assertEquals(ArchiveError.Offline, e)
        }
    }

    @Test
    fun `prefetchArchive when called counts the call`() = runTest {
        sut.prefetchArchive()

        assertEquals(1, sut.prefetchCallCount)
    }

    @Test
    fun `archiveChanges when sample source never emits and completes`() = runTest {
        sut.archiveChanges().test {
            awaitComplete()
        }
    }
}
