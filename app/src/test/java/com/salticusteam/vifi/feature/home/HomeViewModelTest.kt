package com.salticusteam.vifi.feature.home

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.preferences.fake.FakeRecentExamsRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.RecentExam
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analytics = FakeAnalyticsTracker()
    private val first = RecentExam(ArchivePath(listOf("a", "b", "c", "d", "e")), ExamKind.PDF, openedAt = 2)
    private val second = RecentExam(ArchivePath(listOf("a", "b", "c", "d", "f")), ExamKind.IMAGES, openedAt = 1)

    private fun viewModel(vararg recents: RecentExam) =
        HomeViewModel(FakeRecentExamsRepository(recents.toList()), analytics)

    @Test
    fun `uiState exposes the stored recents`() = runTest {
        val sut = viewModel(first, second)
        advanceUntilIdle()

        assertEquals(listOf(first, second), sut.uiState.value.recents)
    }

    @Test
    fun `onRemove drops only that exam`() = runTest {
        val sut = viewModel(first, second)
        advanceUntilIdle()

        sut.onRemove(first)
        advanceUntilIdle()

        assertEquals(listOf(second), sut.uiState.value.recents)
    }

    @Test
    fun `clear asks for confirmation and empties the list only once confirmed`() = runTest {
        val sut = viewModel(first, second)
        advanceUntilIdle()

        sut.uiState.test {
            awaitItem()
            sut.onClearRequested()
            assertTrue(awaitItem().isClearConfirmationShown)
            sut.onClearDismissed()
            val dismissed = awaitItem()
            assertFalse(dismissed.isClearConfirmationShown)
            assertEquals(2, dismissed.recents.size)

            sut.onClearRequested()
            awaitItem()
            sut.onClearConfirmed()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()

        assertTrue(sut.uiState.value.recents.isEmpty())
        assertFalse(sut.uiState.value.isClearConfirmationShown)
    }

    @Test
    fun `onRecentOpened reports the recent exam event`() = runTest {
        val sut = viewModel(first)

        sut.onRecentOpened()

        assertEquals(listOf(AnalyticsEvent.RecentExamOpened), analytics.events)
    }
}
