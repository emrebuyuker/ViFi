package com.salticusteam.vifi.feature.moderation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.feature.examviewer.ExamImagesScreen
import com.salticusteam.vifi.feature.examviewer.ExamPdfScreen
import com.salticusteam.vifi.feature.examviewer.rememberExamImageLoader

/**
 * Previews a submission's pending files before approving (iOS `SubmissionFilePreview`): images as the exam
 * viewer's page list (tap for full screen), a PDF in its page renderer. Not capture-protected, like iOS.
 */
@Composable
fun SubmissionPreviewScreen(
    submissionId: String,
    onBack: () -> Unit,
    viewModel: SubmissionPreviewViewModel = hiltViewModel<SubmissionPreviewViewModel, SubmissionPreviewViewModel.Factory>(
        key = submissionId,
    ) { it.create(submissionId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preview = state

    if (preview == null || preview.document?.fileUrls.isNullOrEmpty()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { ViFiTopAppBar(title = stringResource(R.string.moderation_preview_title), onBack = onBack) },
        ) { padding ->
            EmptyView(
                title = stringResource(R.string.moderation_preview_empty),
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                modifier = Modifier.padding(padding).testTag("moderation.preview.empty"),
            )
        }
        return
    }

    when (preview.kind) {
        ExamKind.IMAGES -> ExamImagesScreen(
            state = preview,
            imageLoader = rememberExamImageLoader(),
            onBack = onBack,
            onRetry = {},
            onFullScreenShown = {},
        )
        // The URLs are known up front, so only the file download can fail (and be retried).
        ExamKind.PDF -> ExamPdfScreen(state = preview, onBack = onBack, onRetryDocument = {})
    }
}
