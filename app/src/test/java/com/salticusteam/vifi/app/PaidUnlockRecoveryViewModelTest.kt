package com.salticusteam.vifi.app

import com.salticusteam.vifi.core.data.billing.AppForegroundEvents
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamPurchaseService
import com.salticusteam.vifi.core.data.repository.fake.FakeUnlocksReader
import com.salticusteam.vifi.core.data.session.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaidUnlockRecoveryViewModelTest {

    private class ManualForegrounds : AppForegroundEvents {
        override val foregrounds = MutableSharedFlow<Unit>()
    }

    @Test
    fun `onArchiveShown when a paid unlock is pending marks it unlocked on the next foreground`() =
        runTest(UnconfinedTestDispatcher()) {
            val unlocks = ExamUnlocksStore(
                FakeUnlocksReader(ids = setOf("e1")),
                SessionStore(FakeAuthService(), backgroundScope),
                backgroundScope,
            )
            val foregrounds = ManualForegrounds()
            val sut = PaidUnlockRecoveryViewModel(FakeExamPurchaseService(pendingRecovery = "e9"), unlocks, foregrounds)
            backgroundScope.launch { sut.onArchiveShown() }

            foregrounds.foregrounds.emit(Unit)
            assertEquals(setOf("e1", "e9"), unlocks.unlockedIds.value)

            // Nothing left to recover: a later foreground changes nothing.
            foregrounds.foregrounds.emit(Unit)
            assertEquals(setOf("e1", "e9"), unlocks.unlockedIds.value)
        }

    @Test
    fun `onArchiveShown when nothing is pending leaves the unlocks as loaded`() = runTest(UnconfinedTestDispatcher()) {
        val unlocks = ExamUnlocksStore(
            FakeUnlocksReader(ids = setOf("e1")),
            SessionStore(FakeAuthService(), backgroundScope),
            backgroundScope,
        )
        val foregrounds = ManualForegrounds()
        val sut = PaidUnlockRecoveryViewModel(FakeExamPurchaseService(), unlocks, foregrounds)
        backgroundScope.launch { sut.onArchiveShown() }

        foregrounds.foregrounds.emit(Unit)

        assertEquals(setOf("e1"), unlocks.unlockedIds.value)
    }
}
