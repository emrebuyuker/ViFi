package com.salticusteam.vifi.feature.structuredexam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.ExamQuestionReader
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchivePath
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
 * Loads the questions of one structured exam, tracks which answers are revealed and records the visit (4.2.0, iOS
 * `StructuredExamViewModel`).
 *
 * Only an exam the archive's lock rule let through reaches this screen (`ExamLock` / `ExamAccessResolver` decide
 * before the route is pushed), so there is no extra gate here (K4: whoever can open the exam can reveal its
 * answers). The revealed answers live on the device only — in the [SavedStateHandle], so they survive rotation and
 * process death — and are never written to Firestore.
 */
@HiltViewModel(assistedFactory = StructuredExamViewModel.Factory::class)
class StructuredExamViewModel @AssistedInject constructor(
    @Assisted path: ArchivePath,
    @Assisted kind: ExamKind,
    private val savedStateHandle: SavedStateHandle,
    private val questionReader: ExamQuestionReader,
    private val recentExams: RecentExamsRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(
        StructuredExamUiState(
            path = path,
            kind = kind,
            revealedOrders = savedStateHandle.get<IntArray>(KEY_REVEALED)?.toSet().orEmpty(),
        ),
    )
    val uiState: StateFlow<StructuredExamUiState> = _uiState.asStateFlow()

    // Internal state
    private var isFetching = false
    private var hasRecordedVisit = false

    /** Questions whose first reveal was already counted (`answer_revealed` is once per question per screen visit). */
    private val trackedReveals = mutableSetOf<Int>()

    // Intents
    init {
        load()
    }

    /**
     * Fetches the questions. Questions already on screen stay visible while reloading and when the reload fails
     * (no automatic fallback to the original pages, K23). The visit is recorded (recents + `exam_open`) once, after
     * the first load that has questions.
     */
    fun load() {
        if (isFetching) return
        val examId = _uiState.value.path.components.lastOrNull()
        if (examId == null) {
            _uiState.update { it.copy(isLoading = false, error = ArchiveError.NotFound.userMessage) }
            return
        }
        isFetching = true
        _uiState.update { if (it.questions == null) it.copy(isLoading = true, error = null) else it }

        viewModelScope.launch {
            try {
                val questions = questionReader.questions(examId)
                _uiState.update { it.copy(isLoading = false, questions = questions, error = null) }
                recordVisitIfNeeded(questions.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleLoadFailure(e)
            } finally {
                isFetching = false
            }
        }
    }

    /** Shows or hides a question's answer. The first reveal of each question is counted once. */
    fun onToggleAnswer(order: Int) {
        val revealed = _uiState.value.revealedOrders
        val updated = if (order in revealed) revealed - order else revealed + order
        _uiState.update { it.copy(revealedOrders = updated) }
        savedStateHandle[KEY_REVEALED] = updated.toIntArray()
        if (order in updated && trackedReveals.add(order)) {
            analytics.track(AnalyticsEvent.AnswerRevealed(order))
        }
    }

    /** Logged each time the screen appears (iOS `screenView(name: "StructuredExam")`). */
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.STRUCTURED_EXAM)
    }

    /** The route's arguments: the exam's path and its original file kind. */
    @AssistedFactory
    interface Factory {
        fun create(path: ArchivePath, kind: ExamKind): StructuredExamViewModel
    }

    // Helpers
    private suspend fun recordVisitIfNeeded(questionCount: Int) {
        if (hasRecordedVisit || questionCount <= 0) return
        hasRecordedVisit = true
        val state = _uiState.value
        analytics.track(AnalyticsEvent.StructuredExamOpened(questionCount))
        recentExams.record(state.path, state.kind, isStructured = true)
    }

    private fun handleLoadFailure(error: Exception) {
        val path = _uiState.value.path
        LogCategory.ARCHIVE.log.e(error, "Loading questions of %s failed", path.components.joinToString("/"))
        _uiState.update {
            if (it.questions != null) it.copy(isLoading = false) else it.copy(isLoading = false, error = error.userMessage)
        }
    }

    private companion object {
        /** The [SavedStateHandle] key of the revealed questions' orders. */
        const val KEY_REVEALED = "structuredExam.revealedOrders"
    }
}
