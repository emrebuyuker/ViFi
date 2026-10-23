package com.salticusteam.vifi.feature.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.ModerationService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.userMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One submission under review (iOS `ModerationDetailView` + `ModerationViewModel.approve/reject`): approving
 * publishes the exam and credits the owner, rejecting records an optional reason. Either removes the submission
 * from the [ModerationQueue] on success; a failure keeps it, with the Turkish message under the actions.
 */
@HiltViewModel(assistedFactory = ModerationDetailViewModel.Factory::class)
class ModerationDetailViewModel @AssistedInject constructor(
    @Assisted submissionId: String,
    private val queue: ModerationQueue,
    private val moderationService: ModerationService,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ModerationDetailUiState(record = queue.record(submissionId)))
    val uiState: StateFlow<ModerationDetailUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<ModerationDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Intents
    /** "Onayla ve Yayınla": publishes and credits; the result dialog follows. Ignored while working. */
    fun approve() {
        val state = _uiState.value
        val record = state.record ?: return
        if (state.isWorking || state.approval != null) return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                val result = moderationService.approve(record)
                queue.remove(record.id)
                analytics.track(AnalyticsEvent.SubmissionApproved(result.tier))
                _uiState.update { it.copy(isWorking = false, approval = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, error = e.userMessage) }
            }
        }
    }

    /** The approval dialog was acknowledged: back to the queue. */
    fun onApprovalAcknowledged() {
        if (_uiState.value.approval == null) return
        viewModelScope.launch { _events.send(ModerationDetailEvent.Finished) }
    }

    /** "Reddet": asks for confirmation with an optional reason. */
    fun onRejectRequested() {
        val state = _uiState.value
        if (state.record == null || state.isWorking) return
        _uiState.update { it.copy(isConfirmingReject = true, rejectReason = "", error = null) }
    }

    fun onRejectReasonChange(text: String) {
        _uiState.update { it.copy(rejectReason = text) }
    }

    fun onRejectDismissed() {
        _uiState.update { it.copy(isConfirmingReject = false) }
    }

    /** Rejects with the typed reason (blank means none); back to the queue on success. */
    fun confirmReject() {
        val state = _uiState.value
        val record = state.record ?: return
        if (state.isWorking) return
        val reason = state.rejectReason.trim().ifEmpty { null }
        _uiState.update { it.copy(isConfirmingReject = false, isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                moderationService.reject(record, reason)
                queue.remove(record.id)
                analytics.track(AnalyticsEvent.SubmissionRejected)
                _uiState.update { it.copy(isWorking = false) }
                _events.send(ModerationDetailEvent.Finished)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, error = e.userMessage) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(submissionId: String): ModerationDetailViewModel
    }
}
