package com.salticusteam.vifi.feature.examviewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.size.Dimension
import coil.size.Size
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.contentText
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import kotlin.math.ceil

/**
 * The viewer of an image exam (iOS `ExamImagesView`, VIEW-01): its pages as cards in a vertical list.
 * Tapping a page calls [onOpenPage] with its index (the full-screen pager).
 */
@Composable
fun ExamImagesContent(
    state: ExamViewerUiState,
    imageLoader: ImageLoader,
    onRetry: () -> Unit,
    onOpenPage: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = state.document
    when {
        document == null && state.error != null -> ErrorView(message = state.error, onRetry = onRetry, modifier = modifier)
        document == null || document.kind != ExamKind.IMAGES -> LoadingView(modifier = modifier)
        document.fileUrls.isEmpty() -> EmptyView(
            title = stringResource(R.string.exam_images_empty),
            icon = Icons.Filled.Description,
            modifier = modifier,
        )
        else -> PageList(document, state.subtitle, imageLoader, onOpenPage, modifier)
    }
}

@Composable
private fun PageList(
    document: ExamDocument,
    subtitle: String,
    imageLoader: ImageLoader,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = document.fileUrls.size
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("exam.images"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            ContextHeader(subtitle = subtitle, pageCount = pageCount, modifier = Modifier.widthIn(max = READABLE_WIDTH))
        }
        itemsIndexed(document.fileUrls, key = { index, _ -> index }) { index, url ->
            ExamPageCard(
                url = url,
                pageNumber = index + 1,
                pageCount = pageCount,
                imageLoader = imageLoader,
                onOpen = { onOpenPage(index) },
                modifier = Modifier.widthIn(max = READABLE_WIDTH).testTag("exam.page.$index"),
            )
        }
    }
}

/** "BOZOK ÜNİVERSİTESİ › MÜHENDİSLİK MATEMATİĞİ · 3 sayfa", above the first page. */
@Composable
private fun ContextHeader(subtitle: String, pageCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = ExamKind.IMAGES.icon, size = 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = subtitle, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                text = ExamKind.IMAGES.contentText(pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One page as a paper-like card with a "Sayfa 1 / 3" caption (iOS `ExamPageView`). Decoded for the card's
 * width (rounded up to 256 px steps, so small width changes don't decode again); an A4 placeholder keeps the
 * layout stable while loading, and a failed page retries on tap.
 */
@Composable
private fun ExamPageCard(
    url: String,
    pageNumber: Int,
    pageCount: Int,
    imageLoader: ImageLoader,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val failure = phase as? AsyncImagePainter.State.Error
    val primaryAction: () -> Unit = { if (failure != null) attempt++ else onOpen() }
    val accessibilityLabel = if (failure != null) {
        stringResource(R.string.cd_exam_page_failed, pageNumber, pageCount)
    } else {
        stringResource(R.string.exam_page_caption, pageNumber, pageCount)
    }
    val actionLabel = stringResource(if (failure != null) R.string.cd_exam_page_retry else R.string.cd_exam_page_open)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = primaryAction)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                onClick(label = actionLabel) { primaryAction(); true }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            // A hairline keeps the card's edge visible in dark mode, where the shadow disappears.
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                val targetPx = (ceil(widthPx / PIXEL_STEP) * PIXEL_STEP).toInt().coerceAtLeast(PIXEL_STEP.toInt())
                val context = LocalContext.current
                val request = remember(url, targetPx, attempt) {
                    pageRequest(context, url, attempt) {
                        size(Size(Dimension(targetPx), Dimension.Undefined))
                        memoryCacheKey(PageCacheKeys.list(url))
                    }
                }
                val isLoaded = phase is AsyncImagePainter.State.Success
                AsyncImage(
                    model = request,
                    contentDescription = null, // the card's semantics describe the page
                    imageLoader = imageLoader,
                    contentScale = ContentScale.FillWidth,
                    onState = { phase = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isLoaded) Modifier else Modifier.aspectRatio(A4_ASPECT_RATIO)),
                )
                when {
                    failure != null -> PageFailure(message = failure.result.throwable.pageErrorMessage())
                    !isLoaded -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(
            text = stringResource(R.string.exam_page_caption, pageNumber, pageCount),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageFailure(message: String) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Text(text = stringResource(R.string.exam_page_failed), style = MaterialTheme.typography.titleSmall)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.common_try_again),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Width / height of an A4 page: the placeholder's shape while a page loads. */
private const val A4_ASPECT_RATIO = 1f / 1.4142f
private const val PIXEL_STEP = 256.0
private val READABLE_WIDTH = 700.dp
