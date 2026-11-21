package com.salticusteam.vifi.feature.conversionadmin

import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.PublishedExamResult
import com.salticusteam.vifi.feature.conversionadmin.StubExamConversionService.Call
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `ConversionReviewViewModelTests` + `ConversionReviewPollingTests`. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversionReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.ofEpochSecond(1_000)
    private val lease = Instant.ofEpochSecond(2_000)
    private val service = StubExamConversionService()
    private val directory = ConversionJobDirectory()

    private fun viewModel(
        questions: List<ExamQuestion> = listOf(ConversionFixture.question(order = 1, needsReview = true), ConversionFixture.question(order = 2)),
        job: ExamConversion = ConversionFixture.job(),
        pollInterval: Duration = 5.milliseconds,
        pollWindow: Duration = 200.milliseconds,
    ): ConversionReviewViewModel {
        service.draftsByJob[job.id] = questions
        directory.remember(listOf(job))
        return ConversionReviewViewModel(job.id, service, directory, { now }, pollInterval, pollWindow)
    }

    // --- Review ---

    @Test
    fun `loading shows the drafts and counts the ones needing review`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        assertEquals(2, sut.uiState.value.drafts?.size)
        assertEquals(1, sut.uiState.value.needsReviewCount)
        assertFalse(sut.uiState.value.isLoading)
    }

    @Test
    fun `the job is found again from the ready list when the directory is empty`() = runTest {
        val job = ConversionFixture.job()
        service.jobs = listOf(job)
        service.draftsByJob[job.id] = listOf(ConversionFixture.question())
        val sut = ConversionReviewViewModel(job.id, service, ConversionJobDirectory(), { now }, 5.milliseconds, 200.milliseconds)

        advanceUntilIdle()

        assertEquals(job.id, sut.uiState.value.job?.id)
        assertEquals("VİZE", sut.uiState.value.titleEntryId?.let { com.salticusteam.vifi.core.model.ExamNameCatalog.entry(it)?.name })
    }

    @Test
    fun `an unknown job shows not found`() = runTest {
        val sut = ConversionReviewViewModel("sub-missing", service, ConversionJobDirectory(), { now }, 5.milliseconds, 200.milliseconds)

        advanceUntilIdle()

        assertEquals(ExamConversionError.NotFound.userMessage, sut.uiState.value.loadError)
        assertNull(sut.uiState.value.drafts)
    }

    @Test
    fun `saving an edit writes the draft and clears its review flag`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()
        val edited = ConversionFixture.question(order = 1, needsReview = true)

        val saved = sut.save(edited)

        assertTrue(saved)
        assertEquals(listOf<Call>(Call.SaveDraftQuestion("sub-s1", "001")), service.calls)
        assertEquals(0, sut.uiState.value.needsReviewCount)
    }

    @Test
    fun `a failed save keeps the draft and shows the message`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()
        service.error = ExamConversionError.PermissionDenied

        val saved = sut.save(ConversionFixture.question(order = 1, needsReview = true))

        assertFalse(saved)
        assertEquals(ReviewError.Server(ExamConversionError.PermissionDenied.userMessage), sut.uiState.value.error)
        assertEquals(1, sut.uiState.value.needsReviewCount)
    }

    @Test
    fun `regenerating one question sends the provider and the question order`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        sut.regenerateQuestion(order = 2, provider = AIProvider.GEMINI)
        runCurrent()

        assertEquals(listOf<Call>(Call.RegenerateQuestion("sub-s1", 2, AIProvider.GEMINI)), service.calls)
        assertEquals(ReviewInfo.RegeneratingQuestion(2), sut.uiState.value.info)
        sut.stopPolling()
    }

    @Test
    fun `regenerating everything ends the review as regenerating`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        sut.regenerateAll(AIProvider.CLAUDE)
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.Regenerate("sub-s1", AIProvider.CLAUDE)), service.calls)
        assertEquals(ReviewOutcome.Regenerating, sut.uiState.value.outcome)
    }

    @Test
    fun `publishing with the suggested subject sends no subject fields`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()
        service.publishResult = PublishedExamResult(examId = "e1", questionCount = 2)

        sut.publish()
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.Publish("sub-s1", null, null, "2023 VİZE")), service.calls)
        assertEquals(ReviewOutcome.Published(questionCount = 2), sut.uiState.value.outcome)
        assertNull("A published job leaves the directory", directory.job("sub-s1"))
    }

    @Test
    fun `publishing with an existing or a new subject sends it`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()
        sut.onSubjectModeChange(SubjectMode.EXISTING)
        sut.onSubjectIdChange(" calculus-1 ")

        sut.publish()
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.Publish("sub-s1", "calculus-1", null, "2023 VİZE")), service.calls)

        val second = StubExamConversionService()
        second.draftsByJob["sub-s1"] = listOf(ConversionFixture.question())
        directory.remember(listOf(ConversionFixture.job())) // the first publish removed the job
        val other = ConversionReviewViewModel("sub-s1", second, directory, { now }, 5.milliseconds, 200.milliseconds)
        advanceUntilIdle()
        other.onSubjectModeChange(SubjectMode.NEW)
        other.onNewSubjectNameChange("Kuantum Fiziği")

        other.publish()
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.Publish("sub-s1", null, "Kuantum Fiziği", "2023 VİZE")), second.calls)
    }

    @Test
    fun `publishing with an empty subject field is refused before any call`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()
        sut.onSubjectModeChange(SubjectMode.EXISTING)

        sut.publish()
        advanceUntilIdle()

        assertTrue(service.calls.isEmpty())
        assertEquals(ReviewError.SubjectIdMissing, sut.uiState.value.error)
        assertNull(sut.uiState.value.outcome)

        sut.onSubjectModeChange(SubjectMode.NEW)
        sut.publish()
        assertEquals(ReviewError.NewSubjectNameMissing, sut.uiState.value.error)
        assertTrue(service.calls.isEmpty())
    }

    @Test
    fun `a publish failure keeps the review open with the message`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()
        service.error = ExamConversionError.NotAllowed("Hazır değil")

        sut.publish()
        advanceUntilIdle()

        assertNull(sut.uiState.value.outcome)
        assertEquals(ReviewError.Server("Hazır değil"), sut.uiState.value.error)
        assertFalse(sut.uiState.value.isWorking)
    }

    @Test
    fun `discarding ends the review as discarded`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        sut.discard()
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.Discard("sub-s1")), service.calls)
        assertEquals(ReviewOutcome.Discarded, sut.uiState.value.outcome)
        assertFalse(sut.uiState.value.canPublish)
    }

    @Test
    fun `the title editor starts from the server's suggestion (K28)`() = runTest {
        val sut = viewModel(job = ConversionFixture.job(originalTitle = "2019 FİZİK QUIZ 5", suggestedTitle = "2019 KISA SINAV 5"))
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals("2019", state.titleYear)
        assertEquals("kisa-sinav", state.titleEntryId)
        assertEquals("5", state.titleNumber)
        assertEquals("2019 KISA SINAV 5", state.examTitle)
        assertNull(state.titleProblem)
        assertEquals("2019 FİZİK QUIZ 5", state.job?.originalTitle)
    }

    @Test
    fun `without a suggestion a list name must be picked before publishing (K28)`() = runTest {
        val sut = viewModel(job = ConversionFixture.job(originalTitle = "DERS NOTU", suggestedTitle = null))
        advanceUntilIdle()

        assertNull(sut.uiState.value.titleEntryId)
        assertNull(sut.uiState.value.examTitle)
        assertEquals(TitleProblem.PICK_NAME, sut.uiState.value.titleProblem)
        assertFalse(sut.uiState.value.canPublish)
        sut.publish()
        advanceUntilIdle()
        assertTrue(service.calls.isEmpty())

        sut.onTitleEntryChange("calisma-sorulari")
        assertTrue(sut.uiState.value.canPublish)
        sut.publish()
        advanceUntilIdle()
        assertEquals(listOf<Call>(Call.Publish("sub-s1", null, null, "ÇALIŞMA SORULARI")), service.calls)
    }

    @Test
    fun `the admin's edited title is validated and sent (K28, K29)`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        sut.onTitleYearChange("2017-2018")
        assertNull(sut.uiState.value.examTitle)
        assertEquals(TitleProblem.YEAR, sut.uiState.value.titleProblem)
        assertFalse(sut.uiState.value.canPublish)
        sut.onTitleYearChange("19")
        assertNull(sut.uiState.value.examTitle)
        sut.onTitleYearChange(" ")
        assertEquals("VİZE", sut.uiState.value.examTitle)
        sut.onTitleYearChange("2020")

        sut.onTitleNumberChange("100")
        assertNull(sut.uiState.value.examTitle)
        assertEquals(TitleProblem.NUMBER_RANGE, sut.uiState.value.titleProblem)
        sut.onTitleNumberChange("0")
        assertNull(sut.uiState.value.examTitle)
        sut.onTitleNumberChange("2")
        sut.onTitleEntryChange("yaz-okulu-final")
        assertEquals("2020 YAZ OKULU FİNAL 2", sut.uiState.value.examTitle)

        // "KISA SINAV" + 2 is the list's own "KISA SINAV 2"; a numbered name takes no extra number.
        sut.onTitleEntryChange("kisa-sinav")
        assertEquals("2020 KISA SINAV 2", sut.uiState.value.examTitle)
        sut.onTitleEntryChange("kisa-sinav-2")
        assertNull(sut.uiState.value.examTitle)
        assertEquals(TitleProblem.NUMBER_ON_NUMBERED_NAME, sut.uiState.value.titleProblem)
        sut.onTitleNumberChange("")
        assertEquals("2020 KISA SINAV 2", sut.uiState.value.examTitle)

        sut.publish()
        advanceUntilIdle()
        assertEquals(listOf<Call>(Call.Publish("sub-s1", null, null, "2020 KISA SINAV 2")), service.calls)
    }

    @Test
    fun `an exam without drafts cannot be published`() = runTest {
        val sut = viewModel(questions = emptyList())
        advanceUntilIdle()

        sut.publish()
        advanceUntilIdle()

        assertTrue(service.calls.isEmpty())
    }

    // --- Regeneration polling ---

    @Test
    fun `a draft is regenerating only while its lease is in the future`() = runTest {
        val sut = viewModel(questions = listOf(ConversionFixture.question(1, regeneratingUntil = lease), ConversionFixture.question(2)))
        advanceUntilIdle()

        assertEquals(setOf(1), sut.uiState.value.regeneratingOrders)

        service.draftResponses += listOf(ConversionFixture.question(1, regeneratingUntil = now.minusSeconds(1)))
        sut.refresh()
        advanceUntilIdle()
        assertEquals(emptySet<Int>(), sut.uiState.value.regeneratingOrders)
    }

    @Test
    fun `polling ends once the lease it saw is gone, and the list shows the new draft`() = runTest {
        val sut = viewModel(questions = listOf(ConversionFixture.question()))
        advanceUntilIdle()
        service.draftResponses += listOf(
            listOf(ConversionFixture.question(regeneratingUntil = lease)),
            listOf(ConversionFixture.question(regeneratingUntil = lease)),
            listOf(ConversionFixture.question(updatedAt = Instant.ofEpochSecond(5))),
        )

        sut.regenerateQuestion(order = 1, provider = AIProvider.CLAUDE)
        advanceUntilIdle()
        sut.waitForPolling()

        assertEquals(1 + 3, service.draftCallCount)
        assertNull(sut.uiState.value.drafts?.first()?.regeneratingUntil)
        assertEquals(Instant.ofEpochSecond(5), sut.uiState.value.drafts?.first()?.updatedAt)
    }

    @Test
    fun `polling ends on a new error even when no lease was seen`() = runTest {
        val sut = viewModel(questions = listOf(ConversionFixture.question()))
        advanceUntilIdle()
        service.draftResponses += listOf(
            listOf(ConversionFixture.question()),
            listOf(ConversionFixture.question(regenerationError = "Model yanıt vermedi")),
        )

        sut.regenerateQuestion(order = 1, provider = AIProvider.CLAUDE)
        advanceUntilIdle()
        sut.waitForPolling()

        assertEquals(1 + 2, service.draftCallCount)
        assertEquals("Model yanıt vermedi", sut.uiState.value.drafts?.first()?.regenerationError)
    }

    @Test
    fun `polling gives up after the window while the lease keeps running`() = runTest {
        val sut = viewModel(questions = listOf(ConversionFixture.question()), pollWindow = 40.milliseconds)
        advanceUntilIdle()
        service.draftsByJob["sub-s1"] = listOf(ConversionFixture.question(regeneratingUntil = lease))

        sut.regenerateQuestion(order = 1, provider = AIProvider.CLAUDE)
        advanceUntilIdle()
        sut.waitForPolling()

        assertEquals(1 + 8, service.draftCallCount)
    }

    @Test
    fun `leaving the screen cancels the polling`() = runTest {
        val sut = viewModel(questions = listOf(ConversionFixture.question()), pollWindow = 60.seconds)
        advanceUntilIdle()
        service.draftsByJob["sub-s1"] = listOf(ConversionFixture.question(regeneratingUntil = lease))

        sut.regenerateQuestion(order = 1, provider = AIProvider.CLAUDE)
        runCurrent()
        sut.stopPolling()
        advanceUntilIdle()
        val callsAfterStop = service.draftCallCount
        advanceUntilIdle()

        assertEquals(callsAfterStop, service.draftCallCount)
        assertTrue("Far fewer reads than the window allows", callsAfterStop < 5)
    }

    @Test
    fun `stop rules - running lease continues, seen then gone, new error and a rewritten draft stop`() {
        val baseline = ConversionFixture.question(updatedAt = Instant.ofEpochSecond(1))
        val stop = ConversionReviewViewModel.Companion::pollShouldStop

        assertFalse(stop(ConversionFixture.question(regeneratingUntil = lease), baseline, false, now))
        assertFalse(stop(baseline, baseline, false, now))
        assertTrue(stop(baseline, baseline, true, now))
        assertTrue(stop(ConversionFixture.question(regenerationError = "x", updatedAt = Instant.ofEpochSecond(1)), baseline, false, now))
        assertTrue(stop(ConversionFixture.question(updatedAt = Instant.ofEpochSecond(9)), baseline, false, now))
        assertTrue(stop(null, baseline, false, now))
        // A stale error from an earlier run does not end the next one before the server claims the draft.
        val stale = ConversionFixture.question(regenerationError = "eski", updatedAt = Instant.ofEpochSecond(1))
        assertFalse(stop(stale, stale, false, now))
        assertNotNull(stale)
    }
}
