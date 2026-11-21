package com.salticusteam.vifi.feature.conversionadmin

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.conversion.ConversionFileUrls
import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.feature.examviewer.ExamImagesScreen
import com.salticusteam.vifi.feature.examviewer.ExamPdfScreen
import com.salticusteam.vifi.feature.examviewer.ExamViewerUiState
import com.salticusteam.vifi.feature.examviewer.rememberExamImageLoader
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A conversion job's original pages for the side-by-side review (iOS `SubmissionFilePreview(files:kind:)`), shaped as
 * the exam viewer's state so its image list and PDF renderer are reused as is. Downloads go through the same
 * credentialled file loader as the archive (an admin may read the job's files).
 */
@HiltViewModel(assistedFactory = ConversionOriginalViewModel.Factory::class)
class ConversionOriginalViewModel @AssistedInject constructor(
    @Assisted private val jobId: String,
    private val directory: ConversionJobDirectory,
    private val service: ExamConversionAdminService,
    private val fileUrls: ConversionFileUrls,
) : ViewModel() {

    // State
    /** `null` while the job is being found, then empty of files when it has none. */
    private val _uiState = MutableStateFlow(directory.job(jobId)?.let(::previewState))
    val uiState: StateFlow<ExamViewerUiState?> = _uiState.asStateFlow()

    @AssistedFactory
    interface Factory {
        fun create(jobId: String): ConversionOriginalViewModel
    }

    init {
        // After process death the directory is empty: the ready list is read again (as the review does).
        if (_uiState.value == null) {
            viewModelScope.launch {
                try {
                    val ready = service.conversions(setOf(ConversionStatus.READY))
                    directory.remember(ready)
                    ready.firstOrNull { it.id == jobId }?.let { _uiState.value = previewState(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The screen keeps showing "no files"; going back and in again retries.
                }
            }
        }
    }

    // Helpers
    private fun previewState(job: ExamConversion): ExamViewerUiState {
        // A display-only stand-in path: the job is not in the archive tree yet.
        val names = job.placementNames.ifEmpty { listOf(job.id) }
        val path = ArchivePath(components = names, displayNames = names)
        val urls = fileUrls.urls(job.files).let { if (job.kind == ExamKind.PDF) it.take(1) else it }
        return ExamViewerUiState(
            path = path,
            routeKind = job.kind,
            isLoading = false,
            document = ExamDocument(path = path, kind = job.kind, fileUrls = urls),
        )
    }
}

/** The original pages of the job [jobId]: images as the exam viewer's page list, a PDF in its page renderer. */
@Composable
fun ConversionOriginalScreen(
    jobId: String,
    onBack: () -> Unit,
    viewModel: ConversionOriginalViewModel = hiltViewModel<ConversionOriginalViewModel, ConversionOriginalViewModel.Factory>(
        key = jobId,
    ) { it.create(jobId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preview = state

    if (preview == null || preview.document?.fileUrls.isNullOrEmpty()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { ViFiTopAppBar(title = stringResource(R.string.conversion_admin_original_title), onBack = onBack) },
        ) { padding ->
            EmptyView(
                title = stringResource(R.string.conversion_admin_original_empty),
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                modifier = Modifier.padding(padding).testTag("conversion.original.empty"),
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
        ExamKind.PDF -> ExamPdfScreen(state = preview, onBack = onBack, onRetryDocument = {})
    }
}
