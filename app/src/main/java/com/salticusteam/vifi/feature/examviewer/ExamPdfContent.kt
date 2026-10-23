package com.salticusteam.vifi.feature.examviewer

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.files.PdfPages
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.theme.VifiAlwaysWhite
import com.salticusteam.vifi.core.model.ExamKind
import kotlinx.coroutines.delay

/**
 * The viewer of a PDF exam (iOS `PDFExamView`, VIEW-07/08): the selected file's pages in one continuous,
 * fit-width vertical scroll with pinch zoom, and a floating "3 / 12" page indicator.
 */
@Composable
fun ExamPdfContent(
    state: ExamViewerUiState,
    pdf: ExamPdfUiState,
    onRetryDocument: () -> Unit,
    onRetryFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = state.document
    val pages = pdf.pages
    when {
        document == null && state.error != null ->
            ErrorView(message = state.error, onRetry = onRetryDocument, modifier = modifier)
        document == null || document.kind != ExamKind.PDF -> LoadingView(modifier = modifier)
        document.fileUrls.isEmpty() -> EmptyView(
            title = stringResource(R.string.exam_pdf_empty),
            icon = Icons.Filled.Description,
            modifier = modifier,
        )
        pdf.error != null -> ErrorView(message = pdf.error, onRetry = onRetryFile, modifier = modifier)
        pages == null -> LoadingView(modifier = modifier, title = stringResource(R.string.exam_pdf_downloading))
        else -> PdfPagesView(pages = pages, modifier = modifier)
    }
}

/** Top bar action listing the exam's files ("Dosya 1", "Dosya 2", …); shown only when there are several. */
@Composable
fun PdfFilePicker(fileCount: Int, selectedFile: Int, onFileSelected: (Int) -> Unit) {
    if (fileCount <= 1) return
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }, modifier = Modifier.testTag("exam.files")) {
            Icon(Icons.Filled.FileCopy, contentDescription = stringResource(R.string.cd_exam_pdf_files))
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            repeat(fileCount) { index ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.exam_pdf_file_item, index + 1),
                            fontWeight = if (index == selectedFile) FontWeight.SemiBold else null,
                        )
                    },
                    onClick = {
                        isExpanded = false
                        onFileSelected(index)
                    },
                    modifier = Modifier.testTag("exam.files.$index"),
                )
            }
        }
    }
}

@Composable
private fun PdfPagesView(pages: PdfPages, modifier: Modifier = Modifier) {
    // A new file starts at its first page, unzoomed.
    val listState = remember(pages) { LazyListState() }
    var scale by remember(pages) { mutableFloatStateOf(1f) }
    var offset by remember(pages) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun applyZoom(newScale: Float, centroid: Offset, pan: Offset = Offset.Zero) {
        val clamped = newScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        // Keep the content point under the fingers in place, then follow the pan; never reveal past the edges.
        val zoomed = centroid - (centroid - offset) * (clamped / scale) + pan
        val minX = viewport.width - viewport.width * clamped
        val minY = viewport.height - viewport.height * clamped
        offset = Offset(zoomed.x.coerceIn(minX, 0f), zoomed.y.coerceIn(minY, 0f))
        scale = clamped
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pages) { detectTwoFingerTransform { zoom, pan, centroid -> applyZoom(scale * zoom, centroid, pan) } }
                .pointerInput(pages) {
                    detectTapGestures(
                        onDoubleTap = { point -> applyZoom(if (scale > 1f) MIN_ZOOM else DOUBLE_TAP_ZOOM, point) },
                    )
                }
                .testTag("exam.pdf"),
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            viewport = IntSize(widthPx, with(LocalDensity.current) { maxHeight.roundToPx() })
            // Sharper pages once zoomed in, without re-rendering on every pinch step.
            val renderWidth = if (scale > SHARP_RENDER_THRESHOLD) widthPx * 2 else widthPx
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                items(count = pages.pageCount, key = { it }) { index ->
                    PdfPage(pages = pages, index = index, renderWidthPx = renderWidth)
                }
            }
        }
        val currentPage by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex } }
        PageIndicator(
            currentPage = currentPage,
            pageCount = pages.pageCount,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** One page, fit to the width; a white sheet with a spinner until its bitmap is rendered. */
@Composable
private fun PdfPage(pages: PdfPages, index: Int, renderWidthPx: Int) {
    var bitmap by remember(pages, index) { mutableStateOf<Bitmap?>(null) }
    // A sharper render replaces the shown one only once it is ready (no flash back to the placeholder).
    LaunchedEffect(pages, index, renderWidthPx) {
        pages.render(index, renderWidthPx)?.let { bitmap = it }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / pages.heightToWidthRatio(index))
            .background(VifiAlwaysWhite),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null, // the page indicator announces the position
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "3 / 12" floating above the pages; fades out after 2 s without a page change (always shown with TalkBack). */
@Composable
private fun PageIndicator(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    val context = LocalContext.current
    val isTouchExploring = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    var isVisible by remember { mutableStateOf(true) }
    LaunchedEffect(currentPage) {
        isVisible = true
        delay(PAGE_INDICATOR_MILLIS)
        isVisible = false
    }
    val description = stringResource(R.string.cd_exam_pdf_page_indicator, currentPage + 1, pageCount)
    AnimatedVisibility(
        visible = isVisible || isTouchExploring,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(400)),
        modifier = modifier.navigationBarsPadding().padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.exam_pdf_page_indicator, currentPage + 1, pageCount),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { contentDescription = description }
                .testTag("exam.pdf.page"),
        )
    }
}

/**
 * Pinch-zoom and two-finger pan only: one finger is left to the list's own vertical scroll. Runs in the
 * initial pass so a pinch never also scrolls the list.
 */
private suspend fun PointerInputScope.detectTwoFingerTransform(
    onTransform: (zoom: Float, pan: Offset, centroid: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                onTransform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(useCurrent = true))
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val SHARP_RENDER_THRESHOLD = 1.5f
private const val PAGE_INDICATOR_MILLIS = 2_000L
