package com.salticusteam.vifi.feature.addexam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.catalog.CatalogSource
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.PickedPdf
import com.salticusteam.vifi.core.data.repository.RewardsConfigReader
import com.salticusteam.vifi.core.data.repository.SubmissionFileSource
import com.salticusteam.vifi.core.data.submission.PendingSubmission
import com.salticusteam.vifi.core.data.submission.SubmissionDraftStore
import com.salticusteam.vifi.core.data.submission.SubmissionQueue
import com.salticusteam.vifi.core.data.submission.SubmissionStatus
import com.salticusteam.vifi.core.data.submission.SubmissionTierResolver
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.ExamSubmissionError
import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.core.model.SubmissionFileError
import com.salticusteam.vifi.core.model.SubmissionLocation
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.LogCategory
import com.salticusteam.vifi.core.util.trackScreen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collects and submits a user-contributed exam (iOS AddExamViewModel + AddExamScreen's reward/estimate tasks).
 *
 * Picked images are transcoded to JPEG pages and a picked PDF is size-checked through [SubmissionFileSource];
 * the novelty tier is re-estimated against the archive whenever the placement changes (the previous estimate
 * is cancelled) and, together with the live reward values, drives the coin note.
 *
 * Survives process death: the picks are kept in the [SavedStateHandle] and the prepared pages in a draft directory
 * ([SubmissionDraftStore]) whose id is saved with them. Submitting hands the draft to the background
 * [SubmissionQueue], so the upload completes even if the user leaves or the process dies; while the form is open
 * it follows that upload — [AddExamEvent.Submitted] once stored (staying busy while the screen closes), the
 * message when it was given up. Leaving the form unsubmitted discards its draft.
 */
@HiltViewModel(assistedFactory = AddExamViewModel.Factory::class)
class AddExamViewModel @AssistedInject constructor(
    /** The signed-in user's uid; owns the submission. */
    @Assisted ownerId: String,
    /** The signed-in user's profile, which pre-fills the school rows. */
    @Assisted profile: UserProfile?,
    private val savedStateHandle: SavedStateHandle,
    private val draftStore: SubmissionDraftStore,
    private val submissionQueue: SubmissionQueue,
    private val rewardsConfigReader: RewardsConfigReader,
    private val tierResolver: SubmissionTierResolver,
    private val fileSource: SubmissionFileSource,
    private val catalogSource: CatalogSource,
    private val analytics: AnalyticsTracker,
    /** Outlives the form, to discard its draft after it is closed. */
    @ApplicationScope private val appScope: CoroutineScope,
    clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(ownerId: String, profile: UserProfile?): AddExamViewModel
    }

    // State
    private val _uiState = MutableStateFlow(
        AddExamUiState.initial(ownerId, profile, currentYear = clock.instant().atZone(ZoneId.systemDefault()).year),
    )
    val uiState: StateFlow<AddExamUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<AddExamEvent>(Channel.BUFFERED)
    val events: Flow<AddExamEvent> = _events.receiveAsFlow()

    // Internal state
    private var estimateJob: Job? = null

    /** The placement the current (or running) estimate is for, so an unrelated edit does not re-resolve. */
    private var estimatedLocation: SubmissionLocation? = null

    /** The form saved before process death, `null` for a fresh form. */
    private val restored: AddExamSavedForm? = AddExamSavedForm.decode(savedStateHandle[KEY_FORM])

    /** The on-disk draft holding this form's pages. */
    private val draftId: String = restored?.draftId ?: draftStore.newDraftId()

    /** The queued submit the form follows; `null` while none is queued or running. */
    private val activeSubmissionId = MutableStateFlow(restored?.submissionId)

    /** Serializes the draft's page writes (FIFO), so a clear never overtakes an earlier append. */
    private val pagesMutex = Mutex()

    /** Set when a page write failed: the draft is rewritten in full before it is submitted. */
    private var pagesOutOfSync = false

    private var observeJob: Job? = null

    // Intents
    init {
        restored?.let { saved -> _uiState.update { saved.applyTo(it) } }
        if (restored == null) {
            appScope.launch { draftStore.pruneStale(keep = draftId) }
        } else {
            restorePages(restored)
        }
        viewModelScope.launch {
            combine(_uiState, activeSubmissionId) { state, submissionId ->
                AddExamSavedForm.of(state, draftId, submissionId)
            }.distinctUntilChanged().collect { savedStateHandle[KEY_FORM] = it.encode() }
        }
        activeSubmissionId.value?.let(::follow)
        viewModelScope.launch {
            val catalog = catalogSource.catalog()
            _uiState.update { it.copy(catalog = catalog) }
        }
        viewModelScope.launch {
            rewardsConfigReader.rewardsChanges().collect { config -> _uiState.update { it.copy(rewards = config) } }
        }
        refreshEstimate()
    }

    /** Logged on every appearance of the screen. */
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.ADD_EXAM)
    }

    fun onPickerOpen(picker: AddExamPicker) {
        val state = _uiState.value
        if (!state.isEditable || (picker == AddExamPicker.LESSON && !state.canPickLesson)) return
        _uiState.update { it.copy(picker = picker) }
    }

    fun onPickerDismiss() = _uiState.update { it.copy(picker = null) }

    /** Takes the value chosen in the open picker and returns to the form. */
    fun onSelection(selection: CatalogSelection) {
        _uiState.update { state ->
            when (state.picker) {
                AddExamPicker.UNIVERSITY -> state.copy(university = selection, picker = null)
                AddExamPicker.FACULTY -> state.copy(faculty = selection, picker = null)
                AddExamPicker.DEPARTMENT -> state.copy(department = selection, picker = null)
                AddExamPicker.LESSON -> state.copy(lesson = selection, picker = null)
                AddExamPicker.EXAM_NAME -> state.copy(examName = selection, picker = null)
                null -> state
            }
        }
        refreshEstimate()
    }

    fun onYearSelected(year: Int) {
        edit { it.copy(year = year) }
        refreshEstimate()
    }

    fun onTermsAcceptedChange(accepted: Boolean) = edit { it.copy(termsAccepted = accepted) }

    /** Switching the kind clears the already-picked files, which belong to the other kind. */
    fun onKindSelected(kind: ExamKind) {
        val state = _uiState.value
        if (!state.isEditable || state.kind == kind) return
        _uiState.update { it.copy(kind = kind, files = emptyList(), error = null) }
        persistPages { draftStore.clearPages(draftId) }
    }

    /**
     * Transcodes the picked images (content URIs, in picking order) to JPEG pages and appends them, dropping any
     * that cannot be encoded or would exceed the page limit.
     */
    fun onImagesPicked(uris: List<String>) {
        val state = _uiState.value
        if (uris.isEmpty() || state.kind != ExamKind.IMAGES || !state.isEditable || state.isPreparingFiles) return
        _uiState.update { it.copy(isPreparingFiles = true, error = null) }
        viewModelScope.launch {
            val existing = _uiState.value.files.size
            val added = mutableListOf<ByteArray>()
            var error: SubmissionFileError? = null
            for (uri in uris) {
                if (existing + added.size >= ExamSubmission.MAX_FILE_COUNT) {
                    error = SubmissionFileError.TooManyPages
                    break
                }
                val jpeg = fileSource.jpegPage(uri, ExamSubmission.MAX_FILE_BYTES)
                if (jpeg != null) added += jpeg else error = SubmissionFileError.ImageUnusable
            }
            var appended = emptyList<SubmissionFile>()
            _uiState.update { current ->
                // The kind may have been switched meanwhile; images then no longer belong to the form.
                val files = if (current.kind == ExamKind.IMAGES) {
                    renumbered(current.files.map { it.data } + added)
                } else {
                    current.files
                }
                appended = files.drop(current.files.size)
                current.copy(files = files, isPreparingFiles = false, error = error?.userMessage)
            }
            if (appended.isNotEmpty()) persistPages { draftStore.writePages(draftId, appended) }
        }
    }

    /** Sets the single PDF file (a content URI) after checking it is non-empty and within the size limit. */
    fun onPdfPicked(uri: String) {
        val state = _uiState.value
        if (state.kind != ExamKind.PDF || !state.isEditable || state.isPreparingFiles) return
        _uiState.update { it.copy(isPreparingFiles = true, error = null) }
        viewModelScope.launch {
            val picked = fileSource.pdf(uri, ExamSubmission.MAX_FILE_BYTES)
            var stored: SubmissionFile? = null
            _uiState.update { current ->
                val idle = current.copy(isPreparingFiles = false)
                stored = null
                when (picked) {
                    is PickedPdf.Data -> if (current.kind == ExamKind.PDF) {
                        val file = SubmissionFile(picked.bytes, order = 1)
                        stored = file
                        idle.copy(files = listOf(file))
                    } else {
                        idle
                    }
                    PickedPdf.Empty -> idle.copy(error = SubmissionFileError.EmptyFile.userMessage)
                    PickedPdf.TooLarge -> idle.copy(error = SubmissionFileError.PdfTooLarge.userMessage)
                    PickedPdf.Unreadable -> idle.copy(error = SubmissionFileError.Unreadable.userMessage)
                }
            }
            stored?.let { file ->
                persistPages {
                    draftStore.clearPages(draftId)
                    draftStore.writePages(draftId, listOf(file))
                }
            }
        }
    }

    fun onClearFiles() {
        if (!_uiState.value.isEditable) return
        _uiState.update { it.copy(files = emptyList(), error = null) }
        persistPages { draftStore.clearPages(draftId) }
    }

    /**
     * Queues the submission for a background upload and follows it (see the class doc). Ignored while busy or while
     * the form is incomplete. Each submit gets a fresh submission id; its retries reuse it.
     */
    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val submission = state.builtSubmission ?: return
        val pending = PendingSubmission.of(submission, state.ownerId, state.estimatedTier)
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                // Behind every queued page write, so the worker reads exactly the submitted pages.
                pagesMutex.withLock {
                    if (pagesOutOfSync) rewritePages(submission.files)
                    submissionQueue.enqueue(draftId, pending)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, error = e.userMessage) }
                analytics.track(
                    AnalyticsEvent.ExamSubmitFailed((e as? ExamSubmissionError)?.analyticsReason ?: UNKNOWN_REASON),
                )
                return@launch
            }
            activeSubmissionId.value = pending.submissionId
            follow(pending.submissionId)
        }
    }

    override fun onCleared() {
        // The form is closed for good (not on process death): an unsubmitted draft is not needed any more.
        appScope.launch { draftStore.discardIfNotQueued(draftId) }
    }

    // Helpers
    /**
     * Follows the queued submit until it ends: stored → [AddExamEvent.Submitted] (still busy while the screen
     * closes); given up → the message, and the form is editable again for another try.
     */
    private fun follow(submissionId: String) {
        observeJob?.cancel()
        _uiState.update { it.copy(isWorking = true, isSendingInBackground = true) }
        observeJob = viewModelScope.launch {
            when (val status = submissionQueue.status(draftId, submissionId).first { it != SubmissionStatus.Sending }) {
                SubmissionStatus.Sent -> _events.send(AddExamEvent.Submitted)
                is SubmissionStatus.Failed -> {
                    activeSubmissionId.value = null
                    _uiState.update {
                        it.copy(isWorking = false, isSendingInBackground = false, error = status.error.userMessage)
                    }
                }
                // Nothing recorded for it (never queued, or long pruned): the form is simply editable again.
                SubmissionStatus.Unknown -> {
                    activeSubmissionId.value = null
                    _uiState.update { it.copy(isWorking = false, isSendingInBackground = false) }
                }
                SubmissionStatus.Sending -> Unit
            }
        }
    }

    /** Reads the restored draft's pages back, unless the kind was switched (and the pages cleared) meanwhile. */
    private fun restorePages(saved: AddExamSavedForm) {
        _uiState.update { it.copy(isPreparingFiles = true) }
        viewModelScope.launch {
            val pages = pagesMutex.withLock { draftStore.readPages(draftId) }
            _uiState.update { current ->
                val restoredFiles = if (current.kind.raw == saved.kind && current.files.isEmpty()) {
                    when (current.kind) {
                        ExamKind.IMAGES -> renumbered(pages.take(ExamSubmission.MAX_FILE_COUNT).map { it.data })
                        ExamKind.PDF -> pages.take(1).map { SubmissionFile(it.data, order = 1) }
                    }
                } else {
                    current.files
                }
                current.copy(files = restoredFiles, isPreparingFiles = false)
            }
        }
    }

    /** Runs a draft write after the earlier ones; a failure is logged and the draft rewritten when submitted. */
    private fun persistPages(write: suspend () -> Unit) {
        viewModelScope.launch {
            pagesMutex.withLock {
                try {
                    write()
                } catch (e: IOException) {
                    LogCategory.FILES.log.w(e, "Could not store the draft pages")
                    pagesOutOfSync = true
                }
            }
        }
    }

    /** Replaces the draft's pages with [files]; throws `IOException`. Call with [pagesMutex] held. */
    private suspend fun rewritePages(files: List<SubmissionFile>) {
        draftStore.clearPages(draftId)
        draftStore.writePages(draftId, files)
        pagesOutOfSync = false
    }

    /** Applies a field edit unless a submit is running. */
    private fun edit(transform: (AddExamUiState) -> AddExamUiState) {
        _uiState.update { state -> if (state.isEditable) transform(state) else state }
    }

    /**
     * Re-estimates the novelty tier when the placement changed: cancels the previous resolve and clears the tier
     * while the placement is incomplete. Non-binding: an unreadable archive simply leaves the tier unknown.
     */
    private fun refreshEstimate() {
        val location = _uiState.value.location
        if (location == estimatedLocation) return
        estimatedLocation = location
        estimateJob?.cancel()
        _uiState.update { it.copy(estimatedTier = null) }
        if (location == null) return
        estimateJob = viewModelScope.launch {
            val tier = try {
                tierResolver.tier(location)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _uiState.update { it.copy(estimatedTier = tier) }
        }
    }

    /** Reassigns 1-based orders by list position, so the file positions stay contiguous. */
    private fun renumbered(data: List<ByteArray>): List<SubmissionFile> =
        data.mapIndexed { index, bytes -> SubmissionFile(bytes, order = index + 1) }

    private companion object {
        /** `exam_submit_failed.reason` for an error without its own reason (iOS "unknown"). */
        const val UNKNOWN_REASON = "unknown"

        /** The [SavedStateHandle] key of the encoded [AddExamSavedForm]. */
        const val KEY_FORM = "addExam.form"
    }
}
