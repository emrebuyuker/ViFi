package com.salticusteam.vifi.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.coins.CoinWalletStore
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.ExamUnlockService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ExamLock
import com.salticusteam.vifi.core.model.ExamUnlockError
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
 * The coin lock of an archive list (iOS `BrowseView` lock wiring + `ExamUnlockSheet.performUnlock`): mirrors the
 * shared balance and unlocks, presents the unlock sheet for a locked exam, and spends coins through the
 * server-side `unlockExam` callable. Opening the exam afterwards is the caller's existing "open" action.
 */
@HiltViewModel
class ExamUnlockViewModel @Inject constructor(
    walletStore: CoinWalletStore,
    private val unlocksStore: ExamUnlocksStore,
    private val unlockService: ExamUnlockService,
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
            combine(walletStore.wallet, unlocksStore.unlockedIds) { wallet, ids -> wallet.balance to ids }
                .collect { (balance, ids) -> _uiState.update { it.copy(balance = balance, unlockedIds = ids) } }
        }
    }

    /** Shows the unlock sheet for a locked exam, recording whether the user can currently afford it. */
    fun onLockedExamTap(exam: ArchiveItem) {
        _uiState.update { it.copy(examToUnlock = exam, error = null) }
        analytics.track(AnalyticsEvent.ExamUnlockSheetShown(canAfford = _uiState.value.canAfford))
    }

    /** Spends coins to unlock the exam in the sheet; on success the sheet closes and the exam opens. */
    fun onUnlockConfirm() {
        val state = _uiState.value
        val exam = state.examToUnlock ?: return
        if (state.isUnlocking) return
        val examId = exam.path.components.lastOrNull() ?: return

        _uiState.update { it.copy(isUnlocking = true, error = null) }
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

    /** The low-balance branch: closes the sheet and hands over to the add-exam flow, with the exam's lesson. */
    fun onEarnCoins() {
        analytics.track(AnalyticsEvent.EarnCoinsTapped)
        val lesson = _uiState.value.examToUnlock?.path?.parent
        _uiState.update { it.copy(examToUnlock = null, error = null) }
        viewModelScope.launch { _events.send(ExamUnlockEvent.EarnCoins(lesson)) }
    }

    /** Closes the sheet; ignored while an unlock is in flight (iOS `interactiveDismissDisabled`). */
    fun onSheetDismiss() {
        if (_uiState.value.isUnlocking) return
        _uiState.update { it.copy(examToUnlock = null, error = null) }
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
