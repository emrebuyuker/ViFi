package com.salticusteam.vifi.feature.conversionadmin

import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `ConversionJobsViewModelTests`. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversionJobsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val service = StubExamConversionService()
    private val directory = ConversionJobDirectory()

    private fun viewModel(kind: ConversionListKind) = ConversionJobsViewModel(kind, service, directory)

    @Test
    fun `the ready list loads only ready jobs`() = runTest {
        service.jobs = listOf(ConversionFixture.job(id = "a"), ConversionFixture.job(id = "b", status = ConversionStatus.FAILED))
        val sut = viewModel(ConversionListKind.READY)
        assertTrue(sut.uiState.value.isLoading)

        advanceUntilIdle()

        assertEquals(listOf("a"), sut.uiState.value.jobs?.map { it.id })
        assertNotNull("The loaded job is findable by the review", directory.job("a"))
    }

    @Test
    fun `a failed job restarts with the picked provider and leaves the list`() = runTest {
        service.jobs = listOf(ConversionFixture.job(id = "a", status = ConversionStatus.FAILED, error = "x"))
        val sut = viewModel(ConversionListKind.FAILED)
        advanceUntilIdle()

        sut.restart(service.jobs[0], AIProvider.GEMINI)
        advanceUntilIdle()

        assertEquals(listOf<StubExamConversionService.Call>(StubExamConversionService.Call.Regenerate("a", AIProvider.GEMINI)), service.calls)
        assertEquals(emptyList<Any>(), sut.uiState.value.jobs)
        assertTrue(!sut.uiState.value.isWorking)
    }

    @Test
    fun `a failed restart keeps the job and shows the error`() = runTest {
        service.jobs = listOf(ConversionFixture.job(id = "a", status = ConversionStatus.FAILED))
        val sut = viewModel(ConversionListKind.FAILED)
        advanceUntilIdle()
        service.error = ExamConversionError.BudgetExceeded

        sut.restart(service.jobs[0], AIProvider.CLAUDE)
        advanceUntilIdle()

        assertEquals(ExamConversionError.BudgetExceeded.userMessage, sut.uiState.value.actionError)
        assertEquals(listOf("a"), sut.uiState.value.jobs?.map { it.id })
        assertTrue(!sut.uiState.value.isWorking)
    }

    @Test
    fun `a failed load surfaces an error and a refresh recovers`() = runTest {
        service.error = ExamConversionError.Offline
        val sut = viewModel(ConversionListKind.READY)

        advanceUntilIdle()

        assertEquals(ExamConversionError.Offline.userMessage, sut.uiState.value.loadError)
        assertNull(sut.uiState.value.jobs)

        service.error = null
        sut.refresh()
        advanceUntilIdle()

        assertNull(sut.uiState.value.loadError)
        assertEquals(emptyList<Any>(), sut.uiState.value.jobs)
    }

    @Test
    fun `refreshIfLoaded does nothing before the first list and reads again after`() = runTest {
        service.jobs = listOf(ConversionFixture.job(id = "a"))
        val sut = viewModel(ConversionListKind.READY)

        sut.refreshIfLoaded() // the first load is still pending
        advanceUntilIdle()
        assertEquals(1, service.conversionListCount)

        sut.refreshIfLoaded()
        advanceUntilIdle()
        assertEquals(2, service.conversionListCount)
    }

    @Test
    fun `a failed refresh keeps the shown list`() = runTest {
        service.jobs = listOf(ConversionFixture.job(id = "a"))
        val sut = viewModel(ConversionListKind.READY)
        advanceUntilIdle()
        service.error = ExamConversionError.Offline

        sut.refresh()
        advanceUntilIdle()

        assertEquals(listOf("a"), sut.uiState.value.jobs?.map { it.id })
        assertEquals(ExamConversionError.Offline.userMessage, sut.uiState.value.actionError)
        assertTrue(!sut.uiState.value.isRefreshing)
    }
}
