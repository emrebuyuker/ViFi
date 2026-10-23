package com.salticusteam.vifi.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.ModerationService
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An admin's exam deletion from an archive list (iOS `BrowseView` swipe action + "Sınav silinsin mi?"). */
data class ExamDeletionUiState(
    /** The user carries the `admin` claim; only then is the delete action offered. */
    val isAdmin: Boolean = false,
    /** The exam awaiting the deletion confirmation. */
    val examToDelete: ArchiveItem? = null,
    /** A deletion is running. */
    val isDeleting: Boolean = false,
    /** Why the last deletion failed ("Silinemedi"), until dismissed. */
    val error: String? = null,
)

sealed interface ExamDeletionEvent {
    /** The exam is gone from the archive: drop its row. */
    data class Deleted(val exam: ArchiveItem) : ExamDeletionEvent
}

/**
 * Lets an admin delete an exam listed under a course: confirm, then remove `exams/<id>`, decrement the course's
 * count and best-effort delete the files ([ModerationService.deleteExam]). Visibility is UI gating only; the
 * rules' `isAdmin()` is what authorizes the write.
 */
@HiltViewModel
class ExamDeletionViewModel @Inject constructor(
    private val authService: AuthService,
    private val moderationService: ModerationService,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ExamDeletionUiState())
    val uiState: StateFlow<ExamDeletionUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<ExamDeletionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Intents
    init {
        viewModelScope.launch {
            val isAdmin = authService.isCurrentUserAdmin()
            _uiState.update { it.copy(isAdmin = isAdmin) }
        }
    }

    /** Whether [item] offers the delete action: exams only, for admins only. */
    fun canDelete(item: ArchiveItem): Boolean = _uiState.value.isAdmin && item.level == ArchiveLevel.EXAM

    /** The swipe action: asks for confirmation. */
    fun onDeleteRequested(item: ArchiveItem) {
        val state = _uiState.value
        if (!canDelete(item) || state.isDeleting) return
        _uiState.update { it.copy(examToDelete = item, error = null) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(examToDelete = null) }
    }

    /** Deletes the exam awaiting confirmation; its row is dropped on success. */
    fun confirmDelete() {
        val state = _uiState.value
        val exam = state.examToDelete ?: return
        if (state.isDeleting) return
        _uiState.update { it.copy(examToDelete = null, isDeleting = true) }
        viewModelScope.launch {
            try {
                moderationService.deleteExam(exam.path)
                _uiState.update { it.copy(isDeleting = false) }
                _events.send(ExamDeletionEvent.Deleted(exam))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeleting = false, error = e.userMessage) }
            }
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }
}
