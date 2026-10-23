package com.salticusteam.vifi.feature.addexam

import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeArchiveRepository()

    private fun viewModel(university: String = "İSTANBUL TEKNİK ÜNİVERSİTESİ", department: String = "Bilgisayar Mühendisliği") =
        LessonPickerViewModel(university, department, repository)

    @Test
    fun `load lists the matched department's lessons`() = runTest {
        val sut = viewModel()
        assertTrue(sut.uiState.value.isLoading)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("BLG 101"), state.matches.map { it.name })
    }

    @Test
    fun `load when the department is not in the archive offers no lessons`() = runTest {
        val sut = viewModel(department = "Tıp")
        advanceUntilIdle()

        assertFalse(sut.uiState.value.isLoading)
        assertNull(sut.uiState.value.error)
        assertTrue(sut.uiState.value.matches.isEmpty())
    }

    @Test
    fun `onQueryChange filters Turkish-insensitively`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        sut.onQueryChange("blg")
        assertEquals(1, sut.uiState.value.matches.size)
        sut.onQueryChange("fizik")
        assertTrue(sut.uiState.value.matches.isEmpty())
    }

    @Test
    fun `load when the archive fails shows the error and retry recovers`() = runTest {
        repository.error = ArchiveError.Offline
        val sut = viewModel()
        advanceUntilIdle()
        assertEquals(ArchiveError.Offline.userMessage, sut.uiState.value.error)

        repository.error = null
        sut.onRetry()
        advanceUntilIdle()

        assertNull(sut.uiState.value.error)
        assertEquals(1, sut.uiState.value.matches.size)
    }
}
