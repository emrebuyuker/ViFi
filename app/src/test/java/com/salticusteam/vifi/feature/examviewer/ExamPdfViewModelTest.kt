package com.salticusteam.vifi.feature.examviewer

import android.graphics.Bitmap
import com.salticusteam.vifi.core.data.files.PdfDocumentFactory
import com.salticusteam.vifi.core.data.files.PdfPages
import com.salticusteam.vifi.core.data.repository.RemoteFileLoader
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamFileError
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExamPdfViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val path = ArchivePath(listOf("u", "f", "d", "l", "2018-vize"), listOf("U", "F", "D", "L", "2018 VİZE"))
    private val twoFiles = ExamDocument(path, ExamKind.PDF, listOf("https://h/1.pdf", "https://h/2.pdf"))
    private val loader = FakeFileLoader()
    private val factory = FakePdfFactory()

    @Test
    fun `onDocumentLoaded downloads and opens the first file`() = runTest {
        val sut = ExamPdfViewModel(loader, factory)

        sut.onDocumentLoaded(twoFiles)
        assertTrue(sut.uiState.value.isLoading)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.fileCount)
        assertEquals(0, state.selectedFile)
        assertSame(factory.opened.single(), state.pages)
        assertEquals(listOf("https://h/1.pdf" to "2018 VİZE - Dosya 1.pdf"), loader.requests)
    }

    @Test
    fun `the same document twice does not reload`() = runTest {
        val sut = ExamPdfViewModel(loader, factory)
        sut.onDocumentLoaded(twoFiles)
        advanceUntilIdle()

        sut.onDocumentLoaded(twoFiles)
        advanceUntilIdle()

        assertEquals(1, loader.requests.size)
    }

    @Test
    fun `selecting another file loads it and closes the previous one`() = runTest {
        val sut = ExamPdfViewModel(loader, factory)
        sut.onDocumentLoaded(twoFiles)
        advanceUntilIdle()
        val first = factory.opened.single()

        sut.onFileSelected(1)
        advanceUntilIdle()

        assertTrue(first.isClosed)
        assertEquals(1, sut.uiState.value.selectedFile)
        assertSame(factory.opened.last(), sut.uiState.value.pages)
        assertEquals("https://h/2.pdf" to "2018 VİZE - Dosya 2.pdf", loader.requests.last())
    }

    @Test
    fun `selecting the current or an out of range file does nothing`() = runTest {
        val sut = ExamPdfViewModel(loader, factory)
        sut.onDocumentLoaded(twoFiles)
        advanceUntilIdle()

        sut.onFileSelected(0)
        sut.onFileSelected(5)
        advanceUntilIdle()

        assertEquals(1, loader.requests.size)
    }

    @Test
    fun `a download failure shows its userMessage and retry loads again`() = runTest {
        loader.error = ArchiveError.Offline
        val sut = ExamPdfViewModel(loader, factory)
        sut.onDocumentLoaded(twoFiles)
        advanceUntilIdle()
        assertEquals(ArchiveError.Offline.userMessage, sut.uiState.value.error)
        assertNull(sut.uiState.value.pages)

        loader.error = null
        sut.onRetry()
        advanceUntilIdle()

        assertNull(sut.uiState.value.error)
        assertSame(factory.opened.single(), sut.uiState.value.pages)
    }

    @Test
    fun `an unreadable pdf shows the unreadable message`() = runTest {
        factory.error = ExamFileError.UnreadablePdf
        val sut = ExamPdfViewModel(loader, factory)

        sut.onDocumentLoaded(twoFiles)
        advanceUntilIdle()

        assertEquals("PDF belgesi açılamadı. Dosya bozuk olabilir.", sut.uiState.value.error)
    }

    @Test
    fun `pdfFileName is readable and numbered only for several files`() {
        assertEquals("2018 VİZE - Dosya 2.pdf", twoFiles.pdfFileName(1))
        assertEquals("2018 VİZE.pdf", twoFiles.copy(fileUrls = listOf("a")).pdfFileName(0))
        val slashed = ExamDocument(ArchivePath(listOf("x"), listOf("1/2 VİZE")), ExamKind.PDF, listOf("a"))
        assertEquals("1-2 VİZE.pdf", slashed.pdfFileName(0))
        assertEquals("Sınav.pdf", ExamDocument(ArchivePath(listOf("x"), listOf(" ")), ExamKind.PDF, listOf("a")).pdfFileName(0))
    }

    private class FakeFileLoader : RemoteFileLoader {
        var error: ArchiveError? = null
        val requests = mutableListOf<Pair<String, String>>()

        override suspend fun localFile(url: String, fileName: String): File {
            requests += url to fileName
            error?.let { throw it }
            return File(fileName)
        }

        override suspend fun pruneDiskCache() = Unit

        override suspend fun purge() = Unit
    }

    private class FakePdfFactory : PdfDocumentFactory {
        var error: Exception? = null
        val opened = mutableListOf<FakePages>()

        override suspend fun open(file: File): PdfPages {
            error?.let { throw it }
            return FakePages().also { opened += it }
        }
    }

    private class FakePages : PdfPages {
        var isClosed = false
        override val pageCount = 3
        override fun heightToWidthRatio(index: Int) = 1.414f
        override suspend fun render(index: Int, widthPx: Int): Bitmap? = null
        override fun close() {
            isClosed = true
        }
    }
}
