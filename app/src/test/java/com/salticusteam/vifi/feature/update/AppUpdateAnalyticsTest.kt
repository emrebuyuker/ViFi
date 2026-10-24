package com.salticusteam.vifi.feature.update

import com.salticusteam.vifi.core.data.repository.AppUpdateChecker
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AppUpdate
import com.salticusteam.vifi.core.model.AppUpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateAnalyticsTest {

    private val status = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.UpToDate)
    private val checker = object : AppUpdateChecker {
        override fun statusChanges(): Flow<AppUpdateStatus> = status
    }
    private val analytics = FakeAnalyticsTracker()

    private fun update(version: String) = AppUpdate(version, "https://store", null)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `up to date reports nothing`() = runTest {
        AppUpdateViewModel(checker, analytics)

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `required prompt is counted once per version`() = runTest {
        status.value = AppUpdateStatus.Required(update("4.0"))
        AppUpdateViewModel(checker, analytics)

        status.value = AppUpdateStatus.Required(update("4.0"))
        status.value = AppUpdateStatus.Required(update("4.1"))

        assertEquals(
            listOf(AnalyticsEvent.UpdatePromptShown(true), AnalyticsEvent.UpdatePromptShown(true)),
            analytics.events,
        )
    }

    @Test
    fun `optional prompt is counted when shown, not again once dismissed`() = runTest {
        status.value = AppUpdateStatus.Optional(update("3.2"))
        val sut = AppUpdateViewModel(checker, analytics)

        sut.onOptionalLaterChosen()
        sut.onOptionalDismissed()
        status.value = AppUpdateStatus.Optional(update("3.2"))

        assertEquals(
            listOf(AnalyticsEvent.UpdatePromptShown(false), AnalyticsEvent.UpdateAction("later")),
            analytics.events,
        )
    }

    @Test
    fun `optional update choice reports the update action`() = runTest {
        status.value = AppUpdateStatus.Optional(update("3.2"))
        val sut = AppUpdateViewModel(checker, analytics)

        sut.onOptionalUpdateChosen()
        sut.onOptionalDismissed()

        assertEquals(AnalyticsEvent.UpdateAction("update"), analytics.events.last())
    }
}
