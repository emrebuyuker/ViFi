package com.salticusteam.vifi.feature.update

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.AppUpdateChecker
import com.salticusteam.vifi.core.model.AppUpdate
import com.salticusteam.vifi.core.model.AppUpdateStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun update(version: String) = AppUpdate(version, "https://store", null)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `required status exposes the required update only`() = runTest {
        status.value = AppUpdateStatus.Required(update("4.0"))
        val sut = AppUpdateViewModel(checker)
        assertEquals("4.0", sut.uiState.value.required?.version)
        assertNull(sut.uiState.value.optional)
    }

    @Test
    fun `optional dismissed is hidden for that version only`() = runTest {
        status.value = AppUpdateStatus.Optional(update("3.2"))
        val sut = AppUpdateViewModel(checker)
        assertEquals("3.2", sut.uiState.value.optional?.version)

        sut.onOptionalDismissed()
        assertNull(sut.uiState.value.optional)

        status.value = AppUpdateStatus.Optional(update("3.3"))
        assertEquals("3.3", sut.uiState.value.optional?.version)
    }

    @Test
    fun `required update appearing later replaces the optional prompt`() = runTest {
        status.value = AppUpdateStatus.Optional(update("3.2"))
        val sut = AppUpdateViewModel(checker)
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
        assertEquals(AppUpdateUiState(), AppUpdateViewModel(checker).uiState.value)
    }
}
