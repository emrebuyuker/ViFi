package com.salticusteam.vifi.feature.practice

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakePoolIndexReader
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.PracticeSelection
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The setup's choice logic (iOS `PracticeSetupViewModelTests`) and its view model over the index reader. */
class PracticeSetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val index = PracticeFixture.index(
        mapOf(
            ExamType.FINAL to mapOf("tr" to 40, "u-bozok" to 12, "c-top1" to 3),
            ExamType.VIZE to mapOf("tr" to 1),
            ExamType.ARA_SINAV to mapOf("u-bozok" to 2, "tr" to 6),
            ExamType.TEK_DERS to mapOf("tr" to 4),
            // Study materials never get a pool in the setup, even if an index listed one.
        ),
    )

    private fun choices(universityId: String? = "bozok") = PracticeSetupChoices(index, "top1", universityId)

    // --- Choices ---

    @Test
    fun `types are listed in the display order with their largest scope's count`() {
        val rows = choices().typeRows

        assertEquals(listOf(ExamType.VIZE, ExamType.ARA_SINAV, ExamType.FINAL, ExamType.TEK_DERS), rows.map { it.type })
        assertEquals(listOf(1, 6, 40, 4), rows.map { it.count })
    }

    @Test
    fun `every scope that holds questions is listed with its count, even a single question (P4)`() {
        val sut = choices()

        assertEquals(
            listOf(PracticeScope.COURSE, PracticeScope.UNIVERSITY, PracticeScope.TURKEY),
            sut.scopeRows(ExamType.FINAL).map { it.scope },
        )
        assertEquals(listOf(3, 12, 40), sut.scopeRows(ExamType.FINAL).map { it.count })
        assertEquals(listOf(PracticeScope.TURKEY), sut.scopeRows(ExamType.VIZE).map { it.scope })
        assertEquals(listOf(1), sut.scopeRows(ExamType.VIZE).map { it.count })
    }

    @Test
    fun `without a known university the Üniversitem scope is hidden`() {
        for (universityId in listOf(null, "")) {
            val sut = choices(universityId)

            assertNull(sut.universityId)
            assertEquals(
                listOf(PracticeScope.COURSE, PracticeScope.TURKEY),
                sut.scopeRows(ExamType.FINAL).map { it.scope },
            )
            assertEquals(listOf(PracticeScope.TURKEY), sut.scopeRows(ExamType.ARA_SINAV).map { it.scope })
        }
    }

    @Test
    fun `a scope whose pool is empty is not offered`() {
        val sut = PracticeSetupChoices(PracticeFixture.index(mapOf(ExamType.FINAL to mapOf("tr" to 5))), "top1", "unknown-uni")

        assertEquals(listOf(PracticeScope.TURKEY), sut.scopeRows(ExamType.FINAL).map { it.scope })
    }

    @Test
    fun `the selection carries the subject, the ids and the pool count`() {
        val selection = choices().selection(ExamType.FINAL, PracticeScope.UNIVERSITY)

        assertEquals(
            PracticeSelection("topoloji-1", ExamType.FINAL, PracticeScope.UNIVERSITY, "top1", "bozok", poolCount = 12),
            selection,
        )
    }

    // --- View model ---

    private fun profileStore(isCustom: Boolean = false, signedIn: Boolean = true): ProfileStore {
        val profile = FakeProfileService.SAMPLE_PROFILE.copy(
            universityId = "bozok",
            universityIsCustom = isCustom,
        )
        return ProfileStore(FakeProfileService(if (signedIn) mapOf("uid" to profile) else emptyMap()))
    }

    private suspend fun loadedProfileStore(isCustom: Boolean = false, signedIn: Boolean = true) =
        profileStore(isCustom, signedIn).also { it.load("uid") }

    private fun makeViewModel(
        reader: FakePoolIndexReader = FakePoolIndexReader(mapOf("topoloji-1" to index)),
        store: ProfileStore,
        analytics: FakeAnalyticsTracker = FakeAnalyticsTracker(),
    ) = PracticeSetupViewModel("topoloji-1", "top1", reader, store, analytics)

    @Test
    fun `loading reads the subject's index once and lists its types`() = runTest {
        val reader = FakePoolIndexReader(mapOf("topoloji-1" to index))
        val sut = makeViewModel(reader, loadedProfileStore())

        assertTrue(sut.uiState.value.isLoading)
        advanceUntilIdle()

        assertFalse(sut.uiState.value.isLoading)
        assertEquals(listOf(ExamType.VIZE, ExamType.ARA_SINAV, ExamType.FINAL, ExamType.TEK_DERS), sut.uiState.value.typeRows.map { it.type })
        assertEquals(1, reader.readCount)
    }

    @Test
    fun `picking a type lists its scopes with the profile's university, and Back returns to the types`() = runTest {
        val sut = makeViewModel(store = loadedProfileStore())
        advanceUntilIdle()

        sut.onTypeSelected(ExamType.FINAL)
        assertEquals(ExamType.FINAL, sut.uiState.value.selectedType)
        assertEquals(
            listOf(PracticeScope.COURSE, PracticeScope.UNIVERSITY, PracticeScope.TURKEY),
            sut.uiState.value.scopeRows.map { it.scope },
        )

        sut.onBackToTypes()
        assertNull(sut.uiState.value.selectedType)
        assertTrue(sut.uiState.value.scopeRows.isEmpty())
    }

    @Test
    fun `a university typed by hand has no Üniversitem scope`() = runTest {
        val sut = makeViewModel(store = loadedProfileStore(isCustom = true))
        advanceUntilIdle()

        sut.onTypeSelected(ExamType.FINAL)

        assertEquals(listOf(PracticeScope.COURSE, PracticeScope.TURKEY), sut.uiState.value.scopeRows.map { it.scope })
    }

    @Test
    fun `no profile means no Üniversitem scope`() = runTest {
        val sut = makeViewModel(store = loadedProfileStore(signedIn = false))
        advanceUntilIdle()

        sut.onTypeSelected(ExamType.FINAL)

        assertEquals(listOf(PracticeScope.COURSE, PracticeScope.TURKEY), sut.uiState.value.scopeRows.map { it.scope })
    }

    @Test
    fun `picking a scope starts the session over that pool`() = runTest {
        val sut = makeViewModel(store = loadedProfileStore())
        advanceUntilIdle()
        sut.onTypeSelected(ExamType.VIZE)

        sut.events.test {
            sut.onScopeSelected(PracticeScope.TURKEY)
            assertEquals(
                PracticeSetupEvent.StartSession(
                    PracticeSelection("topoloji-1", ExamType.VIZE, PracticeScope.TURKEY, "top1", "bozok", poolCount = 1),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `a failed read shows the message, and retrying recovers`() = runTest {
        val reader = FakePoolIndexReader(mapOf("topoloji-1" to index), error = ArchiveError.Offline)
        val sut = makeViewModel(reader, loadedProfileStore())
        advanceUntilIdle()

        assertEquals(ArchiveError.Offline.userMessage, sut.uiState.value.error)

        reader.error = null
        sut.onRetry()
        advanceUntilIdle()

        assertNull(sut.uiState.value.error)
        assertEquals(4, sut.uiState.value.typeRows.size)
    }

    @Test
    fun `a subject without an index is empty, not an error`() = runTest {
        val sut = makeViewModel(FakePoolIndexReader(emptyMap()), loadedProfileStore())
        advanceUntilIdle()

        assertTrue(sut.uiState.value.isEmpty)
        assertNull(sut.uiState.value.error)
    }

    @Test
    fun `showing the screen reports the PracticeSetup screen view`() = runTest {
        val analytics = FakeAnalyticsTracker()
        val sut = makeViewModel(store = loadedProfileStore(), analytics = analytics)

        sut.onScreenShown()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView(AnalyticsScreen.PRACTICE_SETUP)), analytics.events)
    }
}
