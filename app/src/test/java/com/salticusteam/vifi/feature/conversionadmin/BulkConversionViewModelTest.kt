package com.salticusteam.vifi.feature.conversionadmin

import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.feature.conversionadmin.StubExamConversionService.Call
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `BulkConversionViewModelTests`. */
@OptIn(ExperimentalCoroutinesApi::class)
class BulkConversionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val service = StubExamConversionService()

    @Test
    fun `estimating needs a scope and a provider, and never queues`() = runTest {
        service.bulkEstimate = BulkConversionEstimate(examCount = 12, estimatedCostUsd = 5.0, jobIds = emptyList())
        val sut = BulkConversionViewModel(service)
        advanceUntilIdle()
        assertFalse(sut.uiState.value.canEstimate)
        sut.onScopeSelected(BulkConversionScope.Department("d1"), "A › B › C")
        assertFalse(sut.uiState.value.canEstimate)
        sut.onProviderSelected(AIProvider.CLAUDE)
        assertTrue(sut.uiState.value.canEstimate)

        sut.estimate()
        advanceUntilIdle()

        assertEquals(
            listOf<Call>(Call.RequestBulk(BulkConversionScope.Department("d1"), AIProvider.CLAUDE, dryRun = true)),
            service.calls,
        )
        assertEquals(BulkPhase.Estimated(service.bulkEstimate), sut.uiState.value.phase)
    }

    @Test
    fun `confirming queues for real with the same scope and provider`() = runTest {
        service.bulkEstimate = BulkConversionEstimate(examCount = 2, estimatedCostUsd = 1.0, jobIds = listOf("exam-1", "exam-2"))
        val sut = BulkConversionViewModel(service)
        sut.onScopeSelected(BulkConversionScope.Course("c1"), "A › B › C › D")
        sut.onProviderSelected(AIProvider.GEMINI)
        sut.estimate()
        advanceUntilIdle()

        sut.confirm()
        advanceUntilIdle()

        assertEquals(Call.RequestBulk(BulkConversionScope.Course("c1"), AIProvider.GEMINI, dryRun = false), service.calls.last())
        assertEquals(BulkPhase.Queued(service.bulkEstimate), sut.uiState.value.phase)
    }

    @Test
    fun `confirming without an estimate does nothing`() = runTest {
        val sut = BulkConversionViewModel(service)
        sut.onScopeSelected(BulkConversionScope.Course("c1"), "x")
        sut.onProviderSelected(AIProvider.CLAUDE)

        sut.confirm()
        advanceUntilIdle()

        assertTrue(service.calls.isEmpty())
        assertEquals(BulkPhase.Idle, sut.uiState.value.phase)
    }

    @Test
    fun `changing the provider or the scope drops a previous estimate`() = runTest {
        val sut = BulkConversionViewModel(service)
        sut.onScopeSelected(BulkConversionScope.Course("c1"), "x")
        sut.onProviderSelected(AIProvider.CLAUDE)
        sut.estimate()
        advanceUntilIdle()
        assertNotNull((sut.uiState.value.phase as? BulkPhase.Estimated))

        sut.onProviderSelected(AIProvider.GEMINI)
        assertEquals(BulkPhase.Idle, sut.uiState.value.phase)

        sut.estimate()
        advanceUntilIdle()
        sut.onScopeSelected(BulkConversionScope.Course("c2"), "y")
        assertEquals(BulkPhase.Idle, sut.uiState.value.phase)
    }

    @Test
    fun `an estimate that would pass the 50 dollar cap is flagged`() = runTest {
        service.usage = AIMonthlyUsage(month = "2026-11", costUsd = 45.0)
        service.bulkEstimate = BulkConversionEstimate(examCount = 30, estimatedCostUsd = 8.0, jobIds = emptyList())
        val sut = BulkConversionViewModel(service)
        advanceUntilIdle()
        sut.onScopeSelected(BulkConversionScope.Department("d1"), "x")
        sut.onProviderSelected(AIProvider.CLAUDE)

        sut.estimate()
        advanceUntilIdle()

        assertTrue(sut.uiState.value.estimateExceedsBudget)
        assertEquals(45.0, sut.uiState.value.usage?.costUsd ?: 0.0, 0.0)
    }

    @Test
    fun `a failed estimate returns to idle with the message`() = runTest {
        val sut = BulkConversionViewModel(service)
        sut.onScopeSelected(BulkConversionScope.Course("c1"), "x")
        sut.onProviderSelected(AIProvider.CLAUDE)
        service.error = ExamConversionError.PermissionDenied

        sut.estimate()
        advanceUntilIdle()

        assertEquals(BulkPhase.Idle, sut.uiState.value.phase)
        assertEquals(ExamConversionError.PermissionDenied.userMessage, sut.uiState.value.error)
    }

    @Test
    fun `a failed usage read only leaves the gauge out`() = runTest {
        service.error = ExamConversionError.Offline

        val sut = BulkConversionViewModel(service)
        advanceUntilIdle()

        assertEquals(null, sut.uiState.value.usage)
        assertEquals(null, sut.uiState.value.error)
    }
}
