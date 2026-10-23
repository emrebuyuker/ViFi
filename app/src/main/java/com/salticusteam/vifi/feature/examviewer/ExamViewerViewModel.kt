package com.salticusteam.vifi.feature.examviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.LogCategory
import com.salticusteam.vifi.core.util.trackScreen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads the files of one exam for the viewers and records the visit (iOS `ExamDocumentViewModel`).
 * The screens own their file downloads; this owns the document, the recents entry and the analytics events.
 */
@HiltViewModel(assistedFactory = ExamViewerViewModel.Factory::class)
class ExamViewerViewModel @AssistedInject constructor(
    @Assisted path: ArchivePath,
    @Assisted routeKind: ExamKind,
    private val archiveRepository: ArchiveRepository,
    private val recentExams: RecentExamsRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ExamViewerUiState(path = path, routeKind = routeKind))
    val uiState: StateFlow<ExamViewerUiState> = _uiState.asStateFlow()

    // Internal state
    private var isFetching = false
    private var hasRecordedVisit = false

    // Intents
    init {
        load()
    }

    /**
     * Fetches the exam's files. A document already on screen stays visible while reloading, and when the
     * reload fails; the visit is recorded once, after the first successful load.
     */
    fun load() {
        if (isFetching) return
        isFetching = true
        _uiState.update { if (it.document == null) it.copy(isLoading = true, error = null) else it }

        viewModelScope.launch {
            try {
                val document = archiveRepository.document(_uiState.value.path)
                _uiState.update { it.copy(isLoading = false, document = document, error = null) }
                recordVisitIfNeeded(document)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleLoadFailure(e)
            } finally {
                isFetching = false
            }
        }
    }

    /** Logged each time a viewer of [kind] appears (iOS `screenView` "ExamImages" / "ExamPDF"). */
    fun onViewerShown(kind: ExamKind) {
        analytics.trackScreen(if (kind == ExamKind.PDF) AnalyticsScreen.EXAM_PDF else AnalyticsScreen.EXAM_IMAGES)
    }

    /** Logged when the full-screen image pager opens. */
    fun onFullScreenShown() {
        analytics.trackScreen(AnalyticsScreen.EXAM_VIEWER)
    }

    /** The route's arguments: the exam's path and the kind its list entry (or recent card) showed. */
    @AssistedFactory
    interface Factory {
        fun create(path: ArchivePath, routeKind: ExamKind): ExamViewerViewModel
    }

    // Helpers
    private suspend fun recordVisitIfNeeded(document: ExamDocument) {
        if (hasRecordedVisit) return
        hasRecordedVisit = true
        analytics.track(AnalyticsEvent.ExamOpened(document.kind))
        recentExams.record(document.path, document.kind)
    }

    private suspend fun handleLoadFailure(error: Exception) {
        val path = _uiState.value.path
        LogCategory.ARCHIVE.log.e(error, "Loading exam %s failed", path.components.joinToString("/"))
        // Only a definitive "not found" drops the exam from recents, never a transient offline or server error.
        if (error == ArchiveError.NotFound) recentExams.remove(path)
        _uiState.update {
            if (it.document != null) it.copy(isLoading = false)
            else it.copy(isLoading = false, error = error.userMessage)
        }
    }
}
