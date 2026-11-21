package com.salticusteam.vifi.feature.structuredexam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.structured.QuestionFigureEntryPoint
import com.salticusteam.vifi.core.data.structured.QuestionFigureUrlResolver
import com.salticusteam.vifi.core.designsystem.theme.VifiAlwaysBlack
import com.salticusteam.vifi.core.designsystem.theme.VifiAlwaysWhite
import com.salticusteam.vifi.core.model.QuestionFigure
import dagger.hilt.android.EntryPointAccessors

/**
 * How question figures are loaded: the shared exam image loader (credentialled client, `cacheDir/ExamFiles/Images`)
 * and the figure → URL mapping. A `null` [imageLoader] (previews, tests) shows every figure as unavailable.
 */
@Immutable
class QuestionFigureImages(
    val imageLoader: ImageLoader?,
    private val urlResolver: QuestionFigureUrlResolver,
) {
    fun url(figure: QuestionFigure): String? = urlResolver.url(figure)

    companion object {
        /** No loader and no URLs: every figure shows "Şekil yüklenemedi" (previews). */
        val Unavailable = QuestionFigureImages(imageLoader = null) { null }
    }
}

/** The app's figure loading (shared exam image loader + the flavor's figure URLs). */
@Composable
fun rememberQuestionFigureImages(): QuestionFigureImages {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val entryPoint = EntryPointAccessors.fromApplication(context, QuestionFigureEntryPoint::class.java)
        QuestionFigureImages(entryPoint.examImageLoader(), entryPoint.questionFigureUrlResolver())
    }
}

/**
 * A figure cropped from the exam page (iOS `QuestionFigureView`): downloaded only once it is composed (a `LazyColumn`
 * composes the cards that reach the screen), keyed by its `storagePath`, decoded at most 1600 px on its long side.
 * The placeholder keeps the figure's aspect ratio (height / width clamped to 0.1…2, 2/3 when unknown) so the card does
 * not jump on load. Opaque white with 8 dp corners like the page it came from; the TalkBack text is the card's
 * ("Şekil var."), so the image itself carries none.
 */
@Composable
internal fun QuestionFigureImage(
    figure: QuestionFigure,
    images: QuestionFigureImages,
    modifier: Modifier = Modifier,
) {
    val url = remember(figure.storagePath, images) { images.url(figure) }
    val loader = images.imageLoader
    val context = LocalContext.current
    var phase by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val failed = url == null || loader == null || phase is AsyncImagePainter.State.Error

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f / figure.heightRatio())
            .clip(FIGURE_SHAPE)
            .background(VifiAlwaysWhite),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null && loader != null) {
            val request = remember(url, figure.storagePath) {
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(figure.storagePath)
                    .diskCacheKey(figure.storagePath)
                    .size(MAX_FIGURE_PIXELS)
                    .precision(Precision.INEXACT)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                imageLoader = loader,
                contentScale = ContentScale.Fit,
                onState = { phase = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        when {
            failed -> Text(
                text = stringResource(R.string.structured_exam_figure_failed),
                style = MaterialTheme.typography.bodySmall,
                // Dark grey on the always-white paper, in both themes.
                color = VifiAlwaysBlack.copy(alpha = FAILURE_TEXT_ALPHA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp).testTag("questions.figure.failed"),
            )
            phase !is AsyncImagePainter.State.Success -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = VifiAlwaysBlack.copy(alpha = FAILURE_TEXT_ALPHA),
            )
        }
    }
}

/** Height / width for the placeholder: the stored size clamped to 0.1…2, or 2/3 (3:2) when unknown. */
internal fun QuestionFigure.heightRatio(): Float {
    val w = width
    val h = height
    val ratio = if (w != null && h != null && w > 0 && h > 0) h.toFloat() / w else DEFAULT_HEIGHT_RATIO
    return ratio.coerceIn(MIN_HEIGHT_RATIO, MAX_HEIGHT_RATIO)
}

private val FIGURE_SHAPE = RoundedCornerShape(8.dp)
private const val MAX_FIGURE_PIXELS = 1600
private const val DEFAULT_HEIGHT_RATIO = 2f / 3f
private const val MIN_HEIGHT_RATIO = 0.1f
private const val MAX_HEIGHT_RATIO = 2f
private const val FAILURE_TEXT_ALPHA = 0.66f
