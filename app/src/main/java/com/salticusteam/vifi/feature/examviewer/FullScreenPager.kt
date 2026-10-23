package com.salticusteam.vifi.feature.examviewer

import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.VifiAlwaysBlack
import com.salticusteam.vifi.core.designsystem.theme.VifiAlwaysWhite
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * Full-screen, horizontally paged, zoomable viewer of an image exam (iOS `FullScreenPagerView`, VIEW-02/03/04).
 *
 * Drawn over the exam screen in the same window (so FLAG_SECURE covers it), black and immersive. A single tap
 * hides or shows the close button and the "2 / 5" counter; with TalkBack on they never hide.
 */
@Composable
fun FullScreenPager(
    urls: List<String>,
    initialPage: Int,
    imageLoader: ImageLoader,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) {
        urls.size
    }
    var isChromeHidden by rememberSaveable { mutableStateOf(false) }
    val isTouchExploring = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    val showsChrome = !isChromeHidden || isTouchExploring

    BackHandler(onBack = onClose)
    ImmersiveSystemBarsEffect(hidden = !showsChrome)
    PrefetchNeighboursEffect(urls, pagerState.settledPage, imageLoader)

    Box(modifier = modifier.fillMaxSize().background(VifiAlwaysBlack).testTag("viewer.pager")) {
        HorizontalPager(state = pagerState, key = { it }, modifier = Modifier.fillMaxSize()) { page ->
            ZoomablePage(
                url = urls[page],
                pageNumber = page + 1,
                isCurrent = pagerState.settledPage == page,
                imageLoader = imageLoader,
                onSingleTap = { isChromeHidden = !isChromeHidden },
            )
        }
        AnimatedVisibility(
            visible = showsChrome,
            enter = fadeIn(tween(CHROME_ANIMATION_MILLIS)),
            exit = fadeOut(tween(CHROME_ANIMATION_MILLIS)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopControls(page = pagerState.currentPage, pageCount = urls.size, onClose = onClose)
        }
    }
}

@Composable
private fun TopControls(page: Int, pageCount: Int, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .background(ChromeSurface, CircleShape)
                .testTag("viewer.close"),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_exam_viewer_close),
                tint = VifiAlwaysWhite,
            )
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "counter",
            modifier = Modifier
                .align(Alignment.Center)
                .heightIn(min = 36.dp)
                .background(ChromeSurface, CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("viewer.counter"),
        ) { current ->
            Text(
                text = stringResource(R.string.exam_viewer_counter, current + 1, pageCount),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = VifiAlwaysWhite,
            )
        }
    }
}

/**
 * One zoomable page: shows the list card's image at once (placeholder), then a sharp, sub-sampled version.
 * Pinch 1–5×, double tap toggles 2.5× at the tapped point; the zoom resets once the page is swiped away.
 */
@Composable
private fun ZoomablePage(
    url: String,
    pageNumber: Int,
    isCurrent: Boolean,
    imageLoader: ImageLoader,
    onSingleTap: () -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MAX_ZOOM))
    val imageState = rememberZoomableImageState(zoomableState)
    val request = remember(url, attempt) {
        pageRequest(context, url, attempt) {
            memoryCacheKey(PageCacheKeys.fullScreen(url))
            placeholderMemoryCacheKey(PageCacheKeys.list(url))
            listener(
                onStart = { errorMessage = null },
                onError = { _, result -> errorMessage = result.throwable.pageErrorMessage() },
            )
        }
    }

    LaunchedEffect(isCurrent) {
        if (!isCurrent) zoomableState.resetZoom(animationSpec = snap())
    }
    // A closed page stops holding its large bitmap; the bytes stay in the disk cache for a quick re-open.
    DisposableEffect(url) {
        onDispose { imageLoader.memoryCache?.remove(MemoryCache.Key(PageCacheKeys.fullScreen(url))) }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ZoomableAsyncImage(
            model = request,
            contentDescription = stringResource(R.string.cd_exam_page_image, pageNumber),
            imageLoader = imageLoader,
            state = imageState,
            onClick = { onSingleTap() },
            onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = DOUBLE_TAP_ZOOM),
            modifier = Modifier.fillMaxSize(),
        )
        val message = errorMessage
        when {
            imageState.isImageDisplayed || imageState.isPlaceholderDisplayed -> Unit
            message != null -> PageFailure(message = message, onRetry = { attempt++ }, onTap = onSingleTap)
            else -> CircularProgressIndicator(color = VifiAlwaysWhite)
        }
    }
}

/** Shown in place of a page that could not be loaded; tapping around it still toggles the controls. */
@Composable
private fun PageFailure(message: String, onRetry: () -> Unit, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = VifiAlwaysWhite, modifier = Modifier.size(40.dp))
            Text(
                text = stringResource(R.string.exam_page_failed),
                style = MaterialTheme.typography.titleMedium,
                color = VifiAlwaysWhite,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = VifiAlwaysWhite.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.common_try_again)) }
        }
    }
}

/** Hides the status and navigation bars while the controls are hidden; restores them when the pager closes. */
@Composable
private fun ImmersiveSystemBarsEffect(hidden: Boolean) {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window, hidden) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // White status bar icons on the black background.
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = wasLight
        }
    }
}

/**
 * Warms the disk cache with the pages next to [page] on unmetered networks only (iOS speculative loads). The
 * page swiped to always loads for real, prefetched or not.
 */
@Composable
private fun PrefetchNeighboursEffect(urls: List<String>, page: Int, imageLoader: ImageLoader) {
    val context = LocalContext.current
    LaunchedEffect(page) {
        if (!context.allowsSpeculativeDownloads()) return@LaunchedEffect
        listOf(page - 1, page + 1).mapNotNull(urls::getOrNull).forEach { url ->
            imageLoader.enqueue(
                pageRequest(context, url, attempt = 0) {
                    // Only the downloaded bytes matter; the decode stays small and out of the memory cache.
                    size(PREFETCH_DECODE_PX)
                    memoryCachePolicy(CachePolicy.DISABLED)
                },
            )
        }
    }
}

private val ChromeSurface = VifiAlwaysBlack.copy(alpha = 0.4f)
private const val CHROME_ANIMATION_MILLIS = 200
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val PREFETCH_DECODE_PX = 64
