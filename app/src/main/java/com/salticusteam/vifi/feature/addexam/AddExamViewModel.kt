package com.salticusteam.vifi.feature.addexam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.catalog.CatalogSource
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.ExamSubmissionService
import com.salticusteam.vifi.core.data.repository.PickedPdf
import com.salticusteam.vifi.core.data.repository.RewardsConfigReader
import com.salticusteam.vifi.core.data.repository.SubmissionFileSource
import com.salticusteam.vifi.core.data.submission.SubmissionTierResolver
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
import com.salticusteam.vifi.core.util.trackScreen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Collects and submits a user-contributed exam (iOS AddExamViewModel + AddExamScreen's reward/estimate tasks).
 *
 * Picked images are transcoded to JPEG pages and a picked PDF is size-checked through [SubmissionFileSource];
 * the novelty tier is re-estimated against the archive whenever the placement changes (the previous estimate
 * is cancelled) and, together with the live reward values, drives the coin note. A successful submit emits
 * [AddExamEvent.Submitted] and stays busy while the screen closes.
 */
@HiltViewModel(assistedFactory = AddExamViewModel.Factory::class)
class AddExamViewModel @AssistedInject constructor(
    /** The signed-in user's uid; owns the submission. */
    @Assisted ownerId: String,
    /** The signed-in user's profile, which pre-fills the school rows. */
    @Assisted profile: UserProfile?,
    private val submissionService: ExamSubmissionService,
    private val rewardsConfigReader: RewardsConfigReader,
    private val tierResolver: SubmissionTierResolver,
    private val fileSource: SubmissionFileSource,
    private val catalogSource: CatalogSource,
    private val analytics: AnalyticsTracker,
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

    // Intents
    init {
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

    /** Switching the kind clears the already-picked files, which belong to the other kind. */
    fun onKindSelected(kind: ExamKind) = edit { state ->
        if (state.kind == kind) state else state.copy(kind = kind, files = emptyList(), error = null)
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
            _uiState.update { current ->
                // The kind may have been switched meanwhile; images then no longer belong to the form.
                val files = if (current.kind == ExamKind.IMAGES) {
                    renumbered(current.files.map { it.data } + added)
                } else {
                    current.files
                }
                current.copy(files = files, isPreparingFiles = false, error = error?.userMessage)
            }
        }
    }

    /** Sets the single PDF file (a content URI) after checking it is non-empty and within the size limit. */
    fun onPdfPicked(uri: String) {
        val state = _uiState.value
        if (state.kind != ExamKind.PDF || !state.isEditable || state.isPreparingFiles) return
        _uiState.update { it.copy(isPreparingFiles = true, error = null) }
        viewModelScope.launch {
            val picked = fileSource.pdf(uri, ExamSubmission.MAX_FILE_BYTES)
            _uiState.update { current ->
                val idle = current.copy(isPreparingFiles = false)
                when (picked) {
                    is PickedPdf.Data -> if (current.kind == ExamKind.PDF) {
                        idle.copy(files = listOf(SubmissionFile(picked.bytes, order = 1)))
                    } else {
                        idle
                    }
                    PickedPdf.Empty -> idle.copy(error = SubmissionFileError.EmptyFile.userMessage)
                    PickedPdf.TooLarge -> idle.copy(error = SubmissionFileError.PdfTooLarge.userMessage)
                    PickedPdf.Unreadable -> idle.copy(error = SubmissionFileError.Unreadable.userMessage)
                }
            }
        }
    }

    fun onClearFiles() = edit { it.copy(files = emptyList(), error = null) }

    /** Sends the submission for moderation. Ignored while busy or while the form is incomplete. */
    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val submission = state.builtSubmission ?: return
        val estimatedTier = state.estimatedTier
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                submissionService.submit(submission, state.ownerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, error = e.userMessage) }
                analytics.track(
                    AnalyticsEvent.ExamSubmitFailed((e as? ExamSubmissionError)?.analyticsReason ?: UNKNOWN_REASON),
                )
                return@launch
            }
            analytics.track(AnalyticsEvent.ExamSubmitted(submission.kind, submission.files.size, estimatedTier))
            _events.send(AddExamEvent.Submitted)
        }
    }

    // Helpers
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
    }
}
