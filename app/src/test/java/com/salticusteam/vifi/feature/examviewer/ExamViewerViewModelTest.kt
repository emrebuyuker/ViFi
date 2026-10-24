package com.salticusteam.vifi.feature.examviewer

import com.salticusteam.vifi.core.data.preferences.fake.FakeRecentExamsRepository
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.RecentExam
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExamViewerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val path = ArchivePath(
        listOf("bozok", "muh", "blg", "mat", "2019-final"),
        listOf("BOZOK ÜNİVERSİTESİ", "MÜHENDİSLİK", "BİLGİSAYAR", "MÜHENDİSLİK MATEMATİĞİ", "2019 FİNAL"),
    )
    private val document = ExamDocument(path, ExamKind.IMAGES, listOf("https://h/1.jpg", "https://h/2.jpg"))
    private val recents = FakeRecentExamsRepository()
    private val analytics = FakeAnalyticsTracker()

    @Test
    fun `init loads the document and records the visit once`() = runTest {
        val repo = ScriptedArchiveRepository({ document }, { document })
        val sut = viewModel(repo, ExamKind.IMAGES)
        assertTrue(sut.uiState.value.isLoading)
        advanceUntilIdle()

        sut.load()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertEquals(document, state.document)
        assertNull(state.error)
        assertEquals(listOf(AnalyticsEvent.ExamOpened(ExamKind.IMAGES)), analytics.events)
        assertEquals(listOf(path), recents.exams.first().map(RecentExam::path))
    }

    @Test
    fun `title and subtitle come from the path before the document loads`() = runTest {
        val sut = viewModel(ScriptedArchiveRepository({ document }), ExamKind.IMAGES)

        assertEquals("2019 FİNAL", sut.uiState.value.title)
        assertEquals("BOZOK ÜNİVERSİTESİ › MÜHENDİSLİK MATEMATİĞİ", sut.uiState.value.subtitle)
    }

    @Test
    fun `supportPath joins the full location for the Bize Ulas e-mail`() = runTest {
        val sut = viewModel(ScriptedArchiveRepository({ document }), ExamKind.IMAGES)

        assertEquals(
            "BOZOK ÜNİVERSİTESİ › MÜHENDİSLİK › BİLGİSAYAR › MÜHENDİSLİK MATEMATİĞİ › 2019 FİNAL",
            sut.uiState.value.supportPath,
        )
    }

    @Test
    fun `kind follows the loaded document when the list summary was stale`() = runTest {
        val pdf = document.copy(kind = ExamKind.PDF)
        val sut = viewModel(ScriptedArchiveRepository({ pdf }), ExamKind.IMAGES)
        assertEquals(ExamKind.IMAGES, sut.uiState.value.kind)

        advanceUntilIdle()

        assertEquals(ExamKind.PDF, sut.uiState.value.kind)
    }

    @Test
    fun `a failed load shows the userMessage and keeps the recent entry`() = runTest {
        recents.record(path, ExamKind.IMAGES, at = 1)
        val sut = viewModel(ScriptedArchiveRepository({ throw ArchiveError.Offline }), ExamKind.IMAGES)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.document)
        assertEquals(ArchiveError.Offline.userMessage, state.error)
        assertEquals(1, recents.exams.first().size)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `a missing exam is dropped from recents`() = runTest {
        recents.record(path, ExamKind.IMAGES, at = 1)
        viewModel(ScriptedArchiveRepository({ throw ArchiveError.NotFound }), ExamKind.IMAGES)
        advanceUntilIdle()

        assertTrue(recents.exams.first().isEmpty())
    }

    @Test
    fun `retry after a failure loads the document`() = runTest {
        val repo = ScriptedArchiveRepository({ throw ArchiveError.Offline }, { document })
        val sut = viewModel(repo, ExamKind.IMAGES)
        advanceUntilIdle()

        sut.load()
        assertTrue(sut.uiState.value.isLoading)
        advanceUntilIdle()

        assertEquals(document, sut.uiState.value.document)
        assertNull(sut.uiState.value.error)
    }

    @Test
    fun `a failed reload keeps the document on screen`() = runTest {
        val repo = ScriptedArchiveRepository({ document }, { throw ArchiveError.Offline })
        val sut = viewModel(repo, ExamKind.IMAGES)
        advanceUntilIdle()

        sut.load()
        advanceUntilIdle()

        assertEquals(document, sut.uiState.value.document)
        assertNull(sut.uiState.value.error)
    }

    @Test
    fun `screen views are reported per viewer`() = runTest {
        val sut = viewModel(ScriptedArchiveRepository({ document }), ExamKind.IMAGES)

        sut.onViewerShown(ExamKind.IMAGES)
        sut.onViewerShown(ExamKind.PDF)
        sut.onFullScreenShown()

        assertEquals(
            listOf(AnalyticsScreen.EXAM_IMAGES, AnalyticsScreen.EXAM_PDF, AnalyticsScreen.EXAM_VIEWER),
            analytics.events.filterIsInstance<AnalyticsEvent.ScreenView>().map { it.name },
        )
    }

    private fun viewModel(repo: ArchiveRepository, kind: ExamKind) = ExamViewerViewModel(
        path = path,
        routeKind = kind,
        archiveRepository = repo,
        recentExams = recents,
        analytics = analytics,
    )

    /** Answers successive `document` calls with [results] in order (the last one repeats). */
    private class ScriptedArchiveRepository(vararg results: () -> ExamDocument) : ArchiveRepository {
        private val results = results.toMutableList()

        override suspend fun items(path: ArchivePath): List<ArchiveItem> = emptyList()

        override suspend fun document(path: ArchivePath): ExamDocument =
            (if (results.size > 1) results.removeAt(0) else results.first()).invoke()
    }
}
