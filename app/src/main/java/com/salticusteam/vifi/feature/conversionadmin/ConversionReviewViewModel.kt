package com.salticusteam.vifi.feature.conversionadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.ExamListTitle
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the second-approval review of one ready job (iOS `ConversionReviewViewModel`): the draft questions, edits,
 * per-question and whole-exam regeneration (K8, K14), publishing with the course mapping and the exam's list title
 * (K28), and "Sınavı iptal et".
 *
 * [pollInterval] / [pollWindow]: after a single-question regeneration the drafts are re-read every interval for at most
 * the window (the server's lease is a few minutes; the admin should not have to pull to refresh).
 */
@HiltViewModel(assistedFactory = ConversionReviewViewModel.Factory::class)
class ConversionReviewViewModel internal constructor(
    private val jobId: String,
    private val service: ExamConversionAdminService,
    private val directory: ConversionJobDirectory,
    private val now: () -> Instant,
    private val pollInterval: Duration,
    private val pollWindow: Duration,
) : ViewModel() {

    @AssistedInject
    constructor(
        @Assisted jobId: String,
        service: ExamConversionAdminService,
        directory: ConversionJobDirectory,
    ) : this(jobId, service, directory, Instant::now, DEFAULT_POLL_INTERVAL, DEFAULT_POLL_WINDOW)

    // State
    private val _uiState = MutableStateFlow(ConversionReviewUiState())
    val uiState: StateFlow<ConversionReviewUiState> = _uiState.asStateFlow()

    // Internal state
    private var pollingJob: Job? = null

    @AssistedFactory
    interface Factory {
        fun create(jobId: String): ConversionReviewViewModel
    }

    // Intents
    init {
        viewModelScope.launch { load() }
    }

    /** Pull-to-refresh and retry. */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun onTitleYearChange(value: String) = _uiState.update { it.copy(titleYear = value) }

    fun onTitleEntryChange(entryId: String?) = _uiState.update { it.copy(titleEntryId = entryId) }

    fun onTitleNumberChange(value: String) = _uiState.update { it.copy(titleNumber = value) }

    fun onSubjectModeChange(mode: SubjectMode) = _uiState.update { it.copy(subjectMode = mode) }

    fun onSubjectIdChange(value: String) = _uiState.update { it.copy(subjectId = value) }

    fun onNewSubjectNameChange(value: String) = _uiState.update { it.copy(newSubjectName = value) }

    /** Saves an edited draft question; the list shows the saved version. Returns whether it was saved. */
    suspend fun save(question: ExamQuestion): Boolean {
        _uiState.update { it.copy(error = null) }
        return try {
            service.saveDraftQuestion(jobId, question)
            // Saving an edit clears the "look carefully" flag, as the server-side draft does.
            replace(question.copy(needsReview = false))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(error = ReviewError.Server(e.userMessage)) }
            false
        }
    }

    fun regenerateQuestion(order: Int, provider: AIProvider) {
        perform {
            service.regenerateQuestion(jobId, order, provider)
            _uiState.update { it.copy(info = ReviewInfo.RegeneratingQuestion(order)) }
            startPolling(order)
        }
    }

    /** K14: a second-approval rejection; the whole exam is generated again with the picked provider. */
    fun regenerateAll(provider: AIProvider) {
        perform {
            service.regenerate(jobId, provider)
            _uiState.update { it.copy(outcome = ReviewOutcome.Regenerating) }
        }
    }

    fun publish() {
        val state = _uiState.value
        if (!state.canPublish) return
        val subjectId: String?
        val newSubjectName: String?
        when (state.subjectMode) {
            SubjectMode.SUGGESTED -> {
                subjectId = null
                newSubjectName = null
            }
            SubjectMode.EXISTING -> {
                subjectId = state.subjectId.trim().ifEmpty {
                    _uiState.update { it.copy(error = ReviewError.SubjectIdMissing) }
                    return
                }
                newSubjectName = null
            }
            SubjectMode.NEW -> {
                subjectId = null
                newSubjectName = state.newSubjectName.trim().ifEmpty {
                    _uiState.update { it.copy(error = ReviewError.NewSubjectNameMissing) }
                    return
                }
            }
        }
        val examTitle = state.examTitle ?: return
        perform {
            val result = service.publish(jobId, subjectId, newSubjectName, examTitle)
            directory.forget(jobId)
            _uiState.update { it.copy(outcome = ReviewOutcome.Published(result.questionCount)) }
        }
    }

    fun discard() {
        perform {
            service.discard(jobId)
            directory.forget(jobId)
            _uiState.update { it.copy(outcome = ReviewOutcome.Discarded) }
        }
    }

    /** Stops the automatic refresh (the screen went away). */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Waits for the automatic refresh to end (tests). */
    suspend fun waitForPolling() {
        pollingJob?.join()
    }

    // Helpers
    private fun perform(action: suspend () -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ReviewError.Server(e.userMessage)) }
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    private suspend fun load() {
        _uiState.update { if (it.drafts == null) it.copy(isLoading = true, loadError = null) else it }
        try {
            val job = _uiState.value.job ?: resolveJob()
            val drafts = service.draftQuestions(jobId)
            _uiState.update { state ->
                // The title editor starts from the server's suggestion, once, when the job is first known.
                (if (state.job == null) state.withJob(job) else state).copy(isLoading = false, loadError = null)
            }
            setDrafts(drafts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { state ->
                if (state.drafts != null) state.copy(error = ReviewError.Server(e.userMessage))
                else state.copy(isLoading = false, loadError = e.userMessage)
            }
        }
    }

    /** The job a list showed, or (after process death) the ready list read again. */
    private suspend fun resolveJob(): ExamConversion {
        directory.job(jobId)?.let { return it }
        val ready = service.conversions(setOf(ConversionStatus.READY))
        directory.remember(ready)
        return ready.firstOrNull { it.id == jobId } ?: throw ExamConversionError.NotFound
    }

    private fun setDrafts(drafts: List<ExamQuestion>) {
        val at = now()
        _uiState.update { state ->
            state.copy(
                drafts = drafts,
                regeneratingOrders = drafts.filter { it.isRegenerating(at) }.map { it.order }.toSet(),
            )
        }
    }

    private fun replace(question: ExamQuestion) {
        val drafts = _uiState.value.drafts ?: return
        if (drafts.none { it.order == question.order }) return
        setDrafts(drafts.map { if (it.order == question.order) question else it })
    }

    private fun startPolling(order: Int) {
        pollingJob?.cancel()
        val baseline = _uiState.value.drafts?.firstOrNull { it.order == order }
        val attempts = (pollWindow / pollInterval).toInt().coerceAtLeast(1)
        pollingJob = viewModelScope.launch {
            var sawLease = false
            repeat(attempts) {
                delay(pollInterval)
                if (!isActive) return@launch
                reloadQuietly()
                val question = _uiState.value.drafts?.firstOrNull { it.order == order }
                if (question?.isRegenerating(now()) == true) sawLease = true
                if (pollShouldStop(question, baseline, sawLease, now())) return@launch
            }
        }
    }

    /** Re-reads the drafts without a loading state; a failed read keeps the list as it is. */
    private suspend fun reloadQuietly() {
        try {
            setDrafts(service.draftQuestions(jobId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keeps the list; the next round tries again.
        }
    }

    private fun ConversionReviewUiState.withJob(job: ExamConversion): ConversionReviewUiState {
        val suggestion = job.suggestedTitle?.let(ExamListTitle::parse)
        return copy(
            job = job,
            titleYear = suggestion?.year?.toString().orEmpty(),
            titleEntryId = suggestion?.entryId,
            titleNumber = suggestion?.number?.toString().orEmpty(),
        )
    }

    companion object {
        private val DEFAULT_POLL_INTERVAL = 5.seconds
        private val DEFAULT_POLL_WINDOW = 120.seconds

        /**
         * Whether the automatic refresh after a single-question regeneration can stop. Pure, so the rules are
         * testable: a running lease keeps it going; once a lease was seen it ends the run (finished or failed); before
         * any lease was seen (the task has not claimed the draft yet) only a new error or a rewritten draft ends it.
         */
        fun pollShouldStop(question: ExamQuestion?, baseline: ExamQuestion?, sawLease: Boolean, now: Instant): Boolean {
            if (question == null) return true
            if (question.isRegenerating(now)) return false
            if (sawLease) return true
            if (question.regenerationError != null && baseline?.regenerationError == null) return true
            return question.updatedAt != baseline?.updatedAt
        }
    }
}
