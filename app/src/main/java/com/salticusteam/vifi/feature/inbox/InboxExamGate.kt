package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.data.archive.ExamAccess
import com.salticusteam.vifi.core.data.archive.ExamAccessResolver
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.InboxError
import com.salticusteam.vifi.core.model.NotificationExamTarget
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.feature.browse.ExamUnlockEvent
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the inbox does next once the unlock sheet of a gated exam closed (iOS `InboxExamGate` follow-up). */
sealed interface InboxExamFollowUp {
    /** Unlocked (or already was): open the exam with its archive hierarchy on the stack. */
    data class Open(val target: NotificationExamTarget) : InboxExamFollowUp

    /** "Sınav Ekle" to earn coins, pre-filled with the exam's lesson. */
    data class AddExam(val lesson: ArchivePath?) : InboxExamFollowUp

    /** "Premium'a geç". */
    data object Paywall : InboxExamFollowUp
}

data class InboxExamGateState(
    /** The exam being checked: its button shows progress and every further exam tap is ignored. */
    val checkingExamId: String? = null,
    /** A locked exam: the archive's unlock sheet is shown for this item. */
    val lockedExam: ArchiveItem? = null,
    /** Why it could not open, shown under "Sınav açılamadı". */
    val errorMessage: String? = null,
)

/**
 * Runs an exam action of the inbox through the archive's lock rule (iOS `InboxExamGate`): a free, unlocked or
 * subscribed exam opens; a locked one offers the existing unlock sheet (coins, money, Premium or earning coins); a
 * missing one or a failed read says why. Owned by the inbox's ViewModel, so main-thread confined.
 */
class InboxExamGate @Inject constructor(
    private val resolver: ExamAccessResolver,
    private val unlocksStore: ExamUnlocksStore,
    private val subscriptionStore: SubscriptionStore,
) {

    // State
    private val _state = MutableStateFlow(InboxExamGateState())
    val state: StateFlow<InboxExamGateState> = _state.asStateFlow()

    // Internal state
    private var lockedTarget: NotificationExamTarget? = null

    // Intents
    /** Whether [target] may open now. A tap while another check runs is ignored (`false`). */
    suspend fun check(target: NotificationExamTarget): Boolean {
        if (_state.value.checkingExamId != null) return false
        _state.update { it.copy(checkingExamId = target.examId, errorMessage = null) }
        return try {
            val access = resolver.access(
                examPath = target.path,
                isSubscribed = subscriptionStore.isSubscribed.value,
                unlockedIds = unlocksStore.unlockedIds.value,
            )
            when (access) {
                ExamAccess.Open -> true
                is ExamAccess.Locked -> {
                    lockedTarget = target
                    _state.update { it.copy(lockedExam = access.exam) }
                    false
                }
                ExamAccess.Unavailable -> {
                    _state.update { it.copy(errorMessage = InboxError.ExamMissing.userMessage) }
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = e.userMessage) }
            false
        } finally {
            _state.update { it.copy(checkingExamId = null) }
        }
    }

    /** The unlock sheet's outcome after it closed: where to go next, at most once per locked exam. */
    fun followUp(event: ExamUnlockEvent): InboxExamFollowUp? {
        val target = lockedTarget ?: return null
        clearLocked()
        return when (event) {
            is ExamUnlockEvent.Unlocked -> InboxExamFollowUp.Open(target)
            is ExamUnlockEvent.EarnCoins -> InboxExamFollowUp.AddExam(event.lesson)
            ExamUnlockEvent.GoPremium -> InboxExamFollowUp.Paywall
        }
    }

    /** The sheet was closed without unlocking: nothing follows. */
    fun onSheetDismissed() = clearLocked()

    fun onErrorShown() {
        _state.update { it.copy(errorMessage = null) }
    }

    // Helpers
    private fun clearLocked() {
        lockedTarget = null
        _state.update { it.copy(lockedExam = null) }
    }
}
