package com.salticusteam.vifi.feature.structuredexam

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.salticusteam.vifi.core.data.preferences.fake.FakeRecentExamsRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeExamQuestionReader
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.QuestionAnswer
import com.salticusteam.vifi.core.model.RecentExam
import com.salticusteam.vifi.testing.MainDispatcherRule
import com.salticusteam.vifi.testing.afterProcessDeath
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `StructuredExamViewModelTests`, case by case, plus the Android-only saved-state and loading-flow checks. */
class StructuredExamViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val path = ArchivePath(
        listOf("bozok", "muh", "blg", "mat", "2019-final"),
        listOf("BOZOK ÜNİVERSİTESİ", "MÜHENDİSLİK", "BİLGİSAYAR", "MÜHENDİSLİK MATEMATİĞİ", "2019 FİNAL"),
    )
    private val examId = "2019-final"
    private val recents = FakeRecentExamsRepository()
    private val analytics = FakeAnalyticsTracker()

    // --- Loading ---

    @Test
    fun `a new view model is loading and reads nothing yet`() = runTest {
        val reader = FakeExamQuestionReader()
        val sut = viewModel(reader)

        assertTrue(sut.uiState.value.isLoading)
        assertNull(sut.uiState.value.questions)
        assertTrue(reader.requestedExamIds.isEmpty())
    }

    @Test
    fun `loading reads only this exam's questions, and records the visit once`() = runTest {
        val reader = FakeExamQuestionReader(questionsByExam = mapOf(examId to questions(3)))
        val sut = viewModel(reader)
        advanceUntilIdle()

        sut.load()
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), sut.uiState.value.questions?.map(ExamQuestion::order))
        assertEquals(listOf(examId, examId), reader.requestedExamIds)
        val recent = recents.exams.first()
        assertEquals(listOf(path), recent.map(RecentExam::path))
        assertTrue(recent.single().isStructured)
        assertEquals(ExamKind.IMAGES, recent.single().kind)
        assertEquals(listOf(AnalyticsEvent.StructuredExamOpened(questionCount = 3)), analytics.events)
    }

    @Test
    fun `loading goes from loading to the questions`() = runTest {
        val sut = viewModel(FakeExamQuestionReader(questionsByExam = mapOf(examId to questions(2))))

        sut.uiState.test {
            val loading = awaitItem()
            assertTrue(loading.isLoading)
            assertNull(loading.questions)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(2, loaded.questions?.size)
            assertNull(loaded.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an exam without questions loads empty and records no visit`() = runTest {
        val sut = viewModel(FakeExamQuestionReader(questionsByExam = mapOf(examId to emptyList())))
        advanceUntilIdle()

        assertEquals(emptyList<ExamQuestion>(), sut.uiState.value.questions)
        assertNull(sut.uiState.value.error)
        assertTrue(recents.exams.first().isEmpty())
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `a failed read shows the error and records nothing - a retry can still succeed`() = runTest {
        val reader = FakeExamQuestionReader(questionsByExam = mapOf(examId to questions(1)), error = ArchiveError.Offline)
        val sut = viewModel(reader)
        advanceUntilIdle()

        assertFalse(sut.uiState.value.isLoading)
        assertNull(sut.uiState.value.questions)
        assertEquals(ArchiveError.Offline.userMessage, sut.uiState.value.error)
        assertTrue(recents.exams.first().isEmpty())
        assertTrue(analytics.events.isEmpty())

        reader.error = null
        sut.load()
        assertTrue(sut.uiState.value.isLoading)
        advanceUntilIdle()

        assertEquals(1, sut.uiState.value.questions?.size)
        assertNull(sut.uiState.value.error)
    }

    @Test
    fun `questions on screen stay when a reload fails`() = runTest {
        val reader = FakeExamQuestionReader(questionsByExam = mapOf(examId to questions(2)))
        val sut = viewModel(reader)
        advanceUntilIdle()

        reader.error = ArchiveError.Offline
        sut.load()
        advanceUntilIdle()

        assertEquals(2, sut.uiState.value.questions?.size)
        assertNull(sut.uiState.value.error)
        assertFalse(sut.uiState.value.isLoading)
    }

    @Test
    fun `a second load while one is in flight is ignored`() = runTest {
        val reader = FakeExamQuestionReader(questionsByExam = mapOf(examId to questions(2)), latency = 1.seconds)
        val sut = viewModel(reader)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()

        sut.load()
        advanceUntilIdle()

        assertEquals(listOf(examId), reader.requestedExamIds)
        assertEquals(2, sut.uiState.value.questions?.size)
    }

    // --- Answers ---

    @Test
    fun `revealing an answer shows it and counts it once, however often it is toggled`() = runTest {
        val sut = viewModel(FakeExamQuestionReader())

        sut.onToggleAnswer(2)
        assertTrue(sut.uiState.value.isAnswerRevealed(2))
        assertFalse(sut.uiState.value.isAnswerRevealed(1))

        sut.onToggleAnswer(2)
        assertFalse(sut.uiState.value.isAnswerRevealed(2))
        sut.onToggleAnswer(2)
        sut.onToggleAnswer(1)

        assertEquals(
            listOf(AnalyticsEvent.AnswerRevealed(questionOrder = 2), AnalyticsEvent.AnswerRevealed(questionOrder = 1)),
            analytics.events,
        )
    }

    @Test
    fun `revealed answers survive a process death on the device`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(FakeExamQuestionReader(), handle)
        first.onToggleAnswer(1)
        first.onToggleAnswer(3)

        val restored = viewModel(FakeExamQuestionReader(), handle.afterProcessDeath())

        assertEquals(setOf(1, 3), restored.uiState.value.revealedOrders)
    }

    @Test
    fun `support path names the exam`() = runTest {
        val state = viewModel(FakeExamQuestionReader()).uiState.value

        assertEquals("BOZOK ÜNİVERSİTESİ › MÜHENDİSLİK › BİLGİSAYAR › MÜHENDİSLİK MATEMATİĞİ › 2019 FİNAL", state.supportPath)
        assertEquals("BOZOK ÜNİVERSİTESİ › MÜHENDİSLİK MATEMATİĞİ", state.subtitle)
        assertEquals("2019 FİNAL", state.title)
    }

    @Test
    fun `the screen view is reported each time the screen appears`() = runTest {
        val sut = viewModel(FakeExamQuestionReader())

        sut.onScreenShown()
        sut.onScreenShown()

        assertEquals(
            listOf(AnalyticsScreen.STRUCTURED_EXAM, AnalyticsScreen.STRUCTURED_EXAM),
            analytics.events.filterIsInstance<AnalyticsEvent.ScreenView>().map { it.name },
        )
    }

    // --- Helpers ---

    private fun viewModel(reader: FakeExamQuestionReader, handle: SavedStateHandle = SavedStateHandle()) =
        StructuredExamViewModel(
            path = path,
            kind = ExamKind.IMAGES,
            savedStateHandle = handle,
            questionReader = reader,
            recentExams = recents,
            analytics = analytics,
        )

    /** iOS `StructuredExamFixture.questions(count:examId:)`. */
    private fun questions(count: Int): List<ExamQuestion> = (1..count).map { order ->
        ExamQuestion(
            id = "$examId-${"%03d".format(order)}",
            examId = examId,
            order = order,
            label = "$order",
            text = "\$x\$ kaçtır?",
            speech = "x kaçtır?",
            choices = emptyList(),
            figures = emptyList(),
            answer = QuestionAnswer(text = "\$1\$", choice = null, speech = "bir"),
            solution = listOf("\$x = 1\$"),
            topics = emptyList(),
            difficulty = 1,
        )
    }
}
