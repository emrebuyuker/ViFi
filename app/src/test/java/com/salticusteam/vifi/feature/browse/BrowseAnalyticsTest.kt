package com.salticusteam.vifi.feature.browse

import androidx.lifecycle.SavedStateHandle
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowseAnalyticsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analytics = FakeAnalyticsTracker()

    private fun viewModel() = BrowseViewModel(SavedStateHandle(), FakeArchiveRepository(), analytics)

    @Test
    fun `onScreenShown at root reports Home screen view without a browse level`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        sut.onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView("Home")), analytics.events)
    }

    @Test
    fun `onSearchTextChange reports one search after the pause, with zero results flag`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        sut.onSearchTextChange("z")
        advanceTimeBy(500)
        sut.onSearchTextChange("zzzz")
        advanceTimeBy(699)
        assertTrue(analytics.events.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf(AnalyticsEvent.SearchUsed(ArchiveLevel.UNIVERSITY, zeroResults = true)), analytics.events)
    }

    @Test
    fun `onSearchTextChange with empty text reports nothing`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        sut.onSearchTextChange("")
        advanceUntilIdle()

        assertTrue(analytics.events.isEmpty())
    }
}
