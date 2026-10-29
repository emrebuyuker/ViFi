package com.salticusteam.vifi.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.CoinWalletStore
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import com.salticusteam.vifi.core.data.repository.ExamUnlockService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ExamLock
import com.salticusteam.vifi.core.model.ExamUnlockError
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The exam lock of an archive list (iOS `BrowseView` lock wiring + `ExamUnlockSheet`): mirrors the shared balance,
 * unlocks and Premium state, presents the unlock sheet for a locked exam, and unlocks it either with coins (the
 * server-side `unlockExam` callable) or with money (Google Play + `purchaseExamUnlock`). Opening the exam afterwards
 * is the caller's existing "open" action; "Premium'a geç" hands over to the paywall.
 */
@HiltViewModel
class ExamUnlockViewModel @Inject constructor(
    walletStore: CoinWalletStore,
    private val unlocksStore: ExamUnlocksStore,
    subscriptionStore: SubscriptionStore,
    private val unlockService: ExamUnlockService,
    private val purchaseService: ExamPurchaseService,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ExamUnlockUiState())
    val uiState: StateFlow<ExamUnlockUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<ExamUnlockEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Intents
    init {
        viewModelScope.launch {
            combine(walletStore.wallet, unlocksStore.unlockedIds, subscriptionStore.isSubscribed, ::Triple)
                .collect { (wallet, ids, isSubscribed) ->
                    _uiState.update { it.copy(balance = wallet.balance, unlockedIds = ids, isSubscribed = isSubscribed) }
                    openCompletedPurchase(ids)
                }
        }
    }

    /** Shows the unlock sheet for a locked exam, recording whether the user can currently afford it. */
    fun onLockedExamTap(exam: ArchiveItem) {
        _uiState.update { it.copy(examToUnlock = exam, error = null, isPurchasePending = false) }
        analytics.track(AnalyticsEvent.ExamUnlockSheetShown(canAfford = _uiState.value.canAfford))
    }

    /** Spends coins to unlock the exam in the sheet; on success the sheet closes and the exam opens. */
    fun onUnlockConfirm() {
        val state = _uiState.value
        val exam = state.examToUnlock ?: return
        if (state.isBusy) return
        val examId = exam.path.components.lastOrNull() ?: return

        _uiState.update { it.copy(isUnlocking = true, error = null, isPurchasePending = false) }
        viewModelScope.launch {
            try {
                val result = unlockService.unlock(examId)
                // markUnlocked before opening, so the row is unlocked when the user comes back (T9/T10 order).
                unlocksStore.markUnlocked(examId)
                exam.exam?.let { summary ->
                    analytics.track(
                        AnalyticsEvent.ExamUnlocked(summary.kind, ExamLock.UNLOCK_COST, result.alreadyUnlocked),
                    )
                }
                _uiState.update { it.copy(isUnlocking = false, examToUnlock = null) }
                _events.send(ExamUnlockEvent.Unlocked(exam))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analytics.track(AnalyticsEvent.ExamUnlockFailed((e as? ExamUnlockError)?.analyticsReason ?: UNKNOWN))
                _uiState.update { it.copy(isUnlocking = false, error = e.userMessage) }
            }
        }
    }

    /**
     * Buys the exam in the sheet with money (always offered, iOS "Parayla bu sınavı aç"): a recorded purchase closes
     * the sheet and opens the exam, a cancel stays, a pending payment says the exam opens once it completes.
     */
    fun onPurchaseWithMoney() {
        val state = _uiState.value
        val exam = state.examToUnlock ?: return
        if (state.isBusy) return
        val examId = exam.path.components.lastOrNull() ?: return

        _uiState.update { it.copy(isPurchasing = true, error = null, isPurchasePending = false) }
        viewModelScope.launch {
            try {
                when (purchaseService.purchaseUnlock(examId)) {
                    PurchaseResult.SUCCESS -> {
                        // The server recorded the unlock; mark it before opening, like the coin path.
                        unlocksStore.markUnlocked(examId)
                        _uiState.update { it.copy(isPurchasing = false, examToUnlock = null) }
                        _events.send(ExamUnlockEvent.Unlocked(exam))
                    }
                    PurchaseResult.CANCELLED -> _uiState.update { it.copy(isPurchasing = false) }
                    PurchaseResult.PENDING -> _uiState.update { it.copy(isPurchasing = false, isPurchasePending = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isPurchasing = false, error = e.userMessage) }
            }
        }
    }

    /** The low-balance branch: closes the sheet and hands over to the add-exam flow, with the exam's lesson. */
    fun onEarnCoins() {
        if (_uiState.value.isBusy) return
        analytics.track(AnalyticsEvent.EarnCoinsTapped)
        val lesson = _uiState.value.examToUnlock?.path?.parent
        _uiState.update { it.copy(examToUnlock = null, error = null, isPurchasePending = false) }
        viewModelScope.launch { _events.send(ExamUnlockEvent.EarnCoins(lesson)) }
    }

    /** "Tek tek açmak yerine Premium'a geç": closes the sheet and hands over to the paywall. */
    fun onGoPremium() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(examToUnlock = null, error = null, isPurchasePending = false) }
        viewModelScope.launch { _events.send(ExamUnlockEvent.GoPremium) }
    }

    /** Closes the sheet; ignored while an unlock or a purchase is in flight (iOS `interactiveDismissDisabled`). */
    fun onSheetDismiss() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(examToUnlock = null, error = null, isPurchasePending = false) }
    }

    // Helpers

    /**
     * The payment the sheet is waiting on completed and its unlock was recovered (on returning to the app): the sheet
     * closes and the exam opens, as its "onaylanınca sınav açılır" notice promised.
     */
    private suspend fun openCompletedPurchase(unlockedIds: Set<String>) {
        val state = _uiState.value
        val exam = state.examToUnlock ?: return
        if (!state.isPurchasePending) return
        if (exam.path.components.lastOrNull() !in unlockedIds) return
        _uiState.update { it.copy(examToUnlock = null, isPurchasePending = false) }
        _events.send(ExamUnlockEvent.Unlocked(exam))
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
