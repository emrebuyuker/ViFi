package com.salticusteam.vifi.app

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.billing.AppForegroundEvents
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Finishes paid single-exam unlocks whose server record did not complete earlier — a dropped network, or a pending
 * payment that went through while the app was in the background — and shows them unlocked at once (iOS ProfileGate's
 * `recoverPendingUnlock()` task).
 */
@HiltViewModel
class PaidUnlockRecoveryViewModel @Inject constructor(
    private val examPurchaseService: ExamPurchaseService,
    private val unlocksStore: ExamUnlocksStore,
    private val foregroundEvents: AppForegroundEvents,
) : ViewModel() {

    /**
     * Recovers once now and again on every return to the foreground. Suspends while the signed-in archive is shown, so
     * the caller's scope owns it: the archive host runs it in a `LaunchedEffect`, which a sign-out cancels.
     */
    suspend fun onArchiveShown() {
        // `foregrounds` emits at once when collected in the foreground, which covers the launch.
        foregroundEvents.foregrounds.collect {
            examPurchaseService.recoverPendingUnlock()?.let(unlocksStore::markUnlocked)
        }
    }
}
