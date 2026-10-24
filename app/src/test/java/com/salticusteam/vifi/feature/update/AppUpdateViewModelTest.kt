package com.salticusteam.vifi.feature.update

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.preferences.fake.FakeUserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.AppUpdateChecker
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.AppUpdate
import com.salticusteam.vifi.core.model.AppUpdateStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    private val status = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.UpToDate)
    private val checker = object : AppUpdateChecker {
        override fun statusChanges(): Flow<AppUpdateStatus> = status
    }

    private val analytics = FakeAnalyticsTracker()
    private val preferences = FakeUserPreferencesRepository()

    private fun update(version: String) = AppUpdate(version, "https://store", null)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `required status exposes the required update only`() = runTest {
        status.value = AppUpdateStatus.Required(update("4.0"))
        val sut = AppUpdateViewModel(checker, analytics, preferences)
        assertEquals("4.0", sut.uiState.value.required?.version)
        assertNull(sut.uiState.value.optional)
    }

    @Test
    fun `optional dismissed is hidden for that version only`() = runTest {
        status.value = AppUpdateStatus.Optional(update("3.2"))
        val sut = AppUpdateViewModel(checker, analytics, preferences)
        assertEquals("3.2", sut.uiState.value.optional?.version)

        sut.onOptionalDismissed()
        assertNull(sut.uiState.value.optional)

        status.value = AppUpdateStatus.Optional(update("3.3"))
        assertEquals("3.3", sut.uiState.value.optional?.version)
    }

    @Test
    fun `required update appearing later replaces the optional prompt`() = runTest {
        status.value = AppUpdateStatus.Optional(update("3.2"))
        val sut = AppUpdateViewModel(checker, analytics, preferences)
        sut.uiState.test {
            assertEquals("3.2", awaitItem().optional?.version)
            status.value = AppUpdateStatus.Required(update("4.0"))
            val gated = awaitItem()
            assertEquals("4.0", gated.required?.version)
            assertNull(gated.optional)
        }
    }

    @Test
    fun `up to date shows nothing`() = runTest {
        assertEquals(AppUpdateUiState(), AppUpdateViewModel(checker, analytics, preferences).uiState.value)
    }

    @Test
    fun `skip hides that version at once and persists it`() = runTest {
        status.value = AppUpdateStatus.Optional(update("3.2"))
        val sut = AppUpdateViewModel(checker, analytics, preferences)

        sut.onOptionalSkipChosen()

        assertNull(sut.uiState.value.optional)
        assertEquals("3.2", preferences.skippedUpdateVersion.first())
    }

    @Test
    fun `skipped version stays hidden on the next launch while later does not`() = runTest {
        status.value = AppUpdateStatus.Optional(update("3.2"))
        AppUpdateViewModel(checker, analytics, preferences).onOptionalSkipChosen()

        // A fresh ViewModel is a relaunch: in-memory dismissals are gone, the persisted skip is not.
        assertNull(AppUpdateViewModel(checker, analytics, preferences).uiState.value.optional)

        val other = FakeUserPreferencesRepository()
        AppUpdateViewModel(checker, analytics, other).run {
            onOptionalLaterChosen()
            onOptionalDismissed()
        }
        assertEquals("3.2", AppUpdateViewModel(checker, analytics, other).uiState.value.optional?.version)
        assertNull(other.skippedUpdateVersion.first())
    }

    @Test
    fun `a newer version than the skipped one is suggested again`() = runTest {
        preferences.setSkippedUpdateVersion("3.2")
        status.value = AppUpdateStatus.Optional(update("3.3"))

        assertEquals("3.3", AppUpdateViewModel(checker, analytics, preferences).uiState.value.optional?.version)
    }

    @Test
    fun `skipping an optional version never hides a required update`() = runTest {
        preferences.setSkippedUpdateVersion("4.0")
        status.value = AppUpdateStatus.Required(update("4.0"))

        assertEquals("4.0", AppUpdateViewModel(checker, analytics, preferences).uiState.value.required?.version)
    }

    @Test
    fun `skip without a pending optional update does nothing`() = runTest {
        val sut = AppUpdateViewModel(checker, analytics, preferences)

        sut.onOptionalSkipChosen()

        assertNull(preferences.skippedUpdateVersion.first())
        assertEquals(emptyList<Any>(), analytics.events)
    }
}
