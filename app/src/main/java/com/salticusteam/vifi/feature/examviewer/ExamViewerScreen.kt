package com.salticusteam.vifi.feature.examviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind

/**
 * The exam viewer destination: the image viewer (page list + full-screen pager) or the PDF viewer, chosen
 * by the exam's kind (VIEW-06). The whole screen is capture-protected (VIEW-10..12).
 */
@Composable
fun ExamViewerScreen(
    path: ArchivePath,
    routeKind: ExamKind,
    onBack: () -> Unit,
    viewModel: ExamViewerViewModel = hiltViewModel<ExamViewerViewModel, ExamViewerViewModel.Factory> {
        it.create(path, routeKind)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val imageLoader = rememberExamImageLoader()

    LaunchedEffect(state.kind) { viewModel.onViewerShown(state.kind) }

    ScreenCaptureProtection {
        when (state.kind) {
            ExamKind.IMAGES -> ExamImagesScreen(
                state = state,
                imageLoader = imageLoader,
                onBack = onBack,
                onRetry = viewModel::load,
                onFullScreenShown = viewModel::onFullScreenShown,
            )
            ExamKind.PDF -> ExamPdfScreen(state = state, onBack = onBack, onRetryDocument = viewModel::load)
        }
    }
}

@Composable
private fun ExamImagesScreen(
    state: ExamViewerUiState,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFullScreenShown: () -> Unit,
) {
    var openedPage by rememberSaveable { mutableStateOf<Int?>(null) }
    val urls = state.document?.fileUrls.orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { ViFiTopAppBar(title = state.title, onBack = onBack) },
        ) { padding ->
            ExamImagesContent(
                state = state,
                imageLoader = imageLoader,
                onRetry = onRetry,
                onOpenPage = { openedPage = it },
                modifier = Modifier.padding(padding),
            )
        }
        val page = openedPage
        if (page != null && urls.isNotEmpty()) {
            LaunchedEffect(Unit) { onFullScreenShown() }
            FullScreenPager(urls = urls, initialPage = page, imageLoader = imageLoader, onClose = { openedPage = null })
        }
    }
}

@Composable
private fun ExamPdfScreen(
    state: ExamViewerUiState,
    onBack: () -> Unit,
    onRetryDocument: () -> Unit,
    pdfViewModel: ExamPdfViewModel = hiltViewModel(),
) {
    val pdf by pdfViewModel.uiState.collectAsStateWithLifecycle()
    val document = state.document
    LaunchedEffect(document) {
        if (document != null && document.kind == ExamKind.PDF) pdfViewModel.onDocumentLoaded(document)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = state.title,
                onBack = onBack,
                actions = {
                    PdfFilePicker(
                        fileCount = pdf.fileCount,
                        selectedFile = pdf.selectedFile,
                        onFileSelected = pdfViewModel::onFileSelected,
                    )
                },
            )
        },
    ) { padding ->
        ExamPdfContent(
            state = state,
            pdf = pdf,
            onRetryDocument = onRetryDocument,
            onRetryFile = pdfViewModel::onRetry,
            modifier = Modifier.padding(padding),
        )
    }
}
