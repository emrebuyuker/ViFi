package com.salticusteam.vifi.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.designsystem.theme.VifiSystemOrange
import com.salticusteam.vifi.core.model.AnalyticsValues
import kotlinx.coroutines.launch

/**
 * The tour as a full-screen layer over whatever is beneath (iOS `fullScreenCover`). [onFinish] runs after the
 * tour reported its completion; the system back button skips the tour, so the user never lands back on the
 * archive without the first-launch flag being set. [source] is `first_launch` or `replay`.
 */
@Composable
fun OnboardingScreen(
    source: String,
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.onTourShown()
    }

    val finish: (String) -> Unit = { method ->
        viewModel.onTourFinished(method, source)
        onFinish()
    }

    Dialog(
        onDismissRequest = { finish(AnalyticsValues.ONBOARDING_SKIPPED) },
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            OnboardingContent(pages = OnboardingPage.tour, onFinish = finish, modifier = Modifier.safeDrawingPadding())
        }
    }
}

/** Stateless tour: skip bar, paged content with dots, primary button. Previews and UI tests drive this. */
@Composable
fun OnboardingContent(
    pages: List<OnboardingPage>,
    onFinish: (method: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage by remember { derivedStateOf { pagerState.currentPage >= pages.size - 1 } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Skip bar: fixed height so the layout does not jump when "Atla" disappears on the last page.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isLastPage) {
                TextButton(
                    onClick = { onFinish(AnalyticsValues.ONBOARDING_SKIPPED) },
                    modifier = Modifier.testTag("onboarding.skip"),
                ) {
                    Text(stringResource(R.string.onboarding_skip), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("onboarding.pager"),
        ) { index ->
            OnboardingPageContent(page = pages[index])
        }

        PageDots(
            count = pages.size,
            current = pagerState.currentPage,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        Button(
            onClick = {
                if (isLastPage) {
                    onFinish(AnalyticsValues.ONBOARDING_FINISHED)
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            // Black on the brand orange comes from onPrimary: white text would fall below 3:1 contrast.
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .height(52.dp)
                .testTag(if (isLastPage) "onboarding.start" else "onboarding.next"),
        ) {
            Text(
                text = stringResource(if (isLastPage) R.string.onboarding_start else R.string.onboarding_next),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** One page: hero badge, title, message — read by TalkBack as a single element (iOS `.combine`). */
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    // iOS ScaledMetric(96) capped at 140: the hero follows the font scale but never dominates the page.
    val fontScale = LocalDensity.current.fontScale
    val heroSize: Dp = minOf((96 * fontScale).dp, 140.dp)
    val tint = when (page.tint) {
        OnboardingTint.ACCENT -> MaterialTheme.colorScheme.primary
        OnboardingTint.ORANGE -> VifiSystemOrange
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("onboarding.page.${page.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        IconBadge(icon = page.icon, tint = tint, size = heroSize)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(page.title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(page.message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
    }
}

/** The page dots (the foundation pager ships none); one spoken element "Sayfa 2 / 4". */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(
        modifier = modifier
            .semantics { contentDescription = description }
            .testTag("onboarding.dots"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            val color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clearAndSetSemantics {},
            )
        }
    }
}

@ViFiThemePreviews
@Composable
private fun OnboardingContentPreview() {
    ViFiTheme {
        OnboardingContent(pages = OnboardingPage.tour, onFinish = {})
    }
}
