package com.salticusteam.vifi.feature.browse

import androidx.lifecycle.SavedStateHandle
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val itu = item("itu", "İSTANBUL TEKNİK ÜNİVERSİTESİ")
    private val kocaeli = item("kocaeli", "KOCAELİ ÜNİVERSİTESİ")
    private val dumlupinar = item("dpu", "DUMLUPINAR ÜNİVERSİTESİ")

    // --- load ---

    @Test
    fun `init when repository succeeds exposes items and clears loading`() = runTest {
        val repo = ControllableArchiveRepository().apply { result = { listOf(itu, kocaeli) } }

        val sut = BrowseViewModel(SavedStateHandle(), repo)
        assertTrue(sut.uiState.value.isLoading)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(itu, kocaeli), state.items)
        assertNull(state.error)
        assertEquals(ArchivePath.ROOT, repo.requestedPaths.single())
    }

    @Test
    fun `init when repository throws domain error shows its userMessage`() = runTest {
        val repo = ControllableArchiveRepository().apply { result = { throw ArchiveError.Offline } }

        val sut = BrowseViewModel(SavedStateHandle(), repo)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.items)
        assertEquals(ArchiveError.Offline.userMessage, state.error)
    }

    @Test
    fun `init when repository throws unexpected error shows generic message`() = runTest {
        val repo = ControllableArchiveRepository().apply { result = { throw IllegalStateException("boom") } }

        val sut = BrowseViewModel(SavedStateHandle(), repo)
        advanceUntilIdle()

        assertEquals(GENERIC_ERROR_MESSAGE, sut.uiState.value.error)
    }

    @Test
    fun `load after failure retries and recovers`() = runTest {
        val repo = ControllableArchiveRepository().apply { result = { throw ArchiveError.Offline } }
        val sut = BrowseViewModel(SavedStateHandle(), repo)
        advanceUntilIdle()

        repo.result = { listOf(itu) }
        sut.load()
        assertTrue(sut.uiState.value.isLoading)
        assertNull(sut.uiState.value.error)
        advanceUntilIdle()

        assertEquals(listOf(itu), sut.uiState.value.items)
        assertNull(sut.uiState.value.error)
        assertEquals(2, repo.requestedPaths.size)
    }

    @Test
    fun `load while a request is in flight does not start a second one`() = runTest {
        val gate = CompletableDeferred<List<ArchiveItem>>()
        val repo = ControllableArchiveRepository().apply { result = { gate.await() } }
        val sut = BrowseViewModel(SavedStateHandle(), repo)
        advanceUntilIdle()

        sut.load()
        sut.refresh()
        gate.complete(listOf(itu))
        advanceUntilIdle()

        assertEquals(1, repo.requestedPaths.size)
        assertEquals(listOf(itu), sut.uiState.value.items)
    }

    // --- refresh ---

    @Test
    fun `refresh when it fails keeps the items already shown`() = runTest {
        val repo = ControllableArchiveRepository().apply { result = { listOf(itu) } }
        val sut = BrowseViewModel(SavedStateHandle(), repo)
        advanceUntilIdle()

        repo.result = { throw ArchiveError.Offline }
        sut.refresh()
        assertFalse(sut.uiState.value.isLoading)
        advanceUntilIdle()

        assertEquals(listOf(itu), sut.uiState.value.items)
        assertNull(sut.uiState.value.error)
    }

    @Test
    fun `archive change after load triggers a refresh`() = runTest {
        val repo = ControllableArchiveRepository().apply { result = { listOf(itu) } }
        val sut = BrowseViewModel(SavedStateHandle(), repo)
        advanceUntilIdle()

        repo.result = { listOf(itu, kocaeli) }
        repo.changes.emit(Unit)
        advanceUntilIdle()

        assertEquals(listOf(itu, kocaeli), sut.uiState.value.items)
        assertEquals(2, repo.requestedPaths.size)
    }

    // --- search & remove ---

    @Test
    fun `search folds Turkish case and diacritics`() = runTest {
        val repo = ControllableArchiveRepository().apply { result = { listOf(itu, kocaeli, dumlupinar) } }
        val sut = BrowseViewModel(SavedStateHandle(), repo)
        advanceUntilIdle()

        sut.onSearchTextChange("  kocaeli ")
        assertEquals(listOf(kocaeli), sut.uiState.value.visibleItems)
        assertTrue(sut.uiState.value.isFiltering)

        sut.onSearchTextChange("dumlupinar")
        assertEquals(listOf(dumlupinar), sut.uiState.value.visibleItems)

        sut.onSearchTextChange("istanbul teknik")
        assertEquals(listOf(itu), sut.uiState.value.visibleItems)

        sut.onSearchTextChange("")
        assertEquals(3, sut.uiState.value.visibleItems.size)
        assertFalse(sut.uiState.value.isFiltering)
    }

    @Test
    fun `remove drops the item from the loaded list`() = runTest {
        val repo = ControllableArchiveRepository().apply { result = { listOf(itu, kocaeli) } }
        val sut = BrowseViewModel(SavedStateHandle(), repo)
        advanceUntilIdle()

        sut.remove(itu)

        assertEquals(listOf(kocaeli), sut.uiState.value.items)
    }

    // --- helpers ---

    private fun item(id: String, name: String) = ArchiveItem(
        path = ArchivePath.ROOT.appending(id = id, displayName = name),
        level = ArchiveLevel.UNIVERSITY,
        childCount = 1,
        exam = null,
    )

    /** Test double whose next result (or suspension) is set per test. */
    private class ControllableArchiveRepository : ArchiveRepository {
        var result: suspend () -> List<ArchiveItem> = { emptyList() }
        val requestedPaths = mutableListOf<ArchivePath>()
        val changes = MutableSharedFlow<Unit>()

        override suspend fun items(path: ArchivePath): List<ArchiveItem> {
            requestedPaths += path
            return result()
        }

        override suspend fun document(path: ArchivePath): ExamDocument = throw ArchiveError.NotFound

        override fun archiveChanges(): Flow<Unit> = changes
    }
}
