package com.salticusteam.vifi.feature.moderation

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.repository.ModerationService
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.feature.examviewer.ExamViewerUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A submission's pending files for the admin preview (iOS `SubmissionFilePreview`), shaped as the exam viewer's
 * state so its image list and PDF renderer are reused as is. Downloads go through the same credentialled file
 * loader as the archive (an admin may read `pending/…`).
 */
@HiltViewModel(assistedFactory = SubmissionPreviewViewModel.Factory::class)
class SubmissionPreviewViewModel @AssistedInject constructor(
    @Assisted submissionId: String,
    queue: ModerationQueue,
    moderationService: ModerationService,
) : ViewModel() {

    // State
    /** `null` when the queue no longer holds the submission. */
    private val _uiState = MutableStateFlow(
        queue.record(submissionId)?.let { previewState(it, moderationService.fileUrls(it)) },
    )
    val uiState: StateFlow<ExamViewerUiState?> = _uiState.asStateFlow()

    @AssistedFactory
    interface Factory {
        fun create(submissionId: String): SubmissionPreviewViewModel
    }

    internal companion object {
        /**
         * The viewer state for [record]'s files: titled with the exam, "University › Lesson" as its context.
         * The path is a display-only stand-in (submissions are not in the archive tree yet).
         */
        fun previewState(record: ExamSubmissionRecord, fileUrls: List<String>): ExamViewerUiState {
            val path = ArchivePath(
                components = listOf(record.university, record.lesson, record.id),
                displayNames = listOf(record.university, record.lesson, record.exam),
            )
            return ExamViewerUiState(
                path = path,
                routeKind = record.kind,
                isLoading = false,
                document = ExamDocument(path = path, kind = record.kind, fileUrls = fileUrls.previewFiles(record.kind)),
            )
        }

        /** A PDF submission is a single file (iOS previews only the first). */
        private fun List<String>.previewFiles(kind: ExamKind): List<String> =
            if (kind == ExamKind.PDF) take(1) else this
    }
}
