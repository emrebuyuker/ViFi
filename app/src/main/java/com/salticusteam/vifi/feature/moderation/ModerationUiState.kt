package com.salticusteam.vifi.feature.moderation

import com.salticusteam.vifi.core.model.ApprovalResult
import com.salticusteam.vifi.core.model.ExamSubmissionRecord

/**
 * The moderation queue (iOS `ModerationView`): loading until the first list arrives, then the records (kept on
 * screen while a pull-to-refresh runs), or the failure when nothing could be shown.
 */
data class ModerationUiState(
    val isLoading: Boolean = true,
    /** A pull-to-refresh is running over a list already shown. */
    val isRefreshing: Boolean = false,
    /** `null` until the first successful load. */
    val records: List<ExamSubmissionRecord>? = null,
    val error: String? = null,
)

/**
 * One submission under review (iOS `ModerationDetailView`). [record] is `null` when the queue no longer holds it;
 * the screen then offers to go back. After process death the queue is empty: [isLoading] is set while it is
 * reloaded to find the record again.
 */
data class ModerationDetailUiState(
    val record: ExamSubmissionRecord? = null,
    /** The queue is being reloaded to restore [record] (the process was recreated). */
    val isLoading: Boolean = false,
    /** An approval or rejection is running: every control and the back navigation are locked. */
    val isWorking: Boolean = false,
    /** The last failure's message, shown under the actions. */
    val error: String? = null,
    /** The rejection confirmation (with its optional reason) is shown. */
    val isConfirmingReject: Boolean = false,
    val rejectReason: String = "",
    /** Set once approved: the result dialog is shown until acknowledged. */
    val approval: ApprovalResult? = null,
)

/** Results of the detail's actions that end the screen; every branch must be handled (no `else`). */
sealed interface ModerationDetailEvent {
    /** The submission was approved (dialog acknowledged) or rejected: back to the queue. */
    data object Finished : ModerationDetailEvent
}
