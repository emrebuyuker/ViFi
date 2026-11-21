package com.salticusteam.vifi.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.PracticeSelection
import com.salticusteam.vifi.feature.examviewer.ScreenCaptureProtection
import com.salticusteam.vifi.feature.structuredexam.QuestionCard
import com.salticusteam.vifi.feature.structuredexam.QuestionFigureImages
import com.salticusteam.vifi.feature.structuredexam.rememberQuestionFigureImages

/**
 * The "Sınava gir" session (4.2.0, iOS `PracticeSessionView`): one [QuestionCard] at a time with "Cevabı Göster",
 * "Sıradaki soru" and "Bitir". Free users see how many questions are left today; once the daily limit is used up the
 * paywall is offered ([onOpenPaywall]). The whole screen is capture-protected like the structured exam screen.
 *
 * @param onFinish "Bitir": leaves the session and its setup (iOS closes the practice sheet).
 */
@Composable
fun PracticeSessionScreen(
    selection: PracticeSelection,
    onFinish: () -> Unit,
    onOpenPaywall: () -> Unit,
    onBack: () -> Unit,
    viewModel: PracticeSessionViewModel = hiltViewModel<PracticeSessionViewModel, PracticeSessionViewModel.Factory> {
        it.create(selection)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val figureImages = rememberQuestionFigureImages()

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
        viewModel.onStart()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PracticeSessionEvent.OpenPaywall -> onOpenPaywall()
            }
        }
    }

    ScreenCaptureProtection {
        PracticeSessionContent(
            state = state,
            figureImages = figureImages,
            onToggleAnswer = viewModel::onToggleAnswer,
            onNext = viewModel::onNext,
            onRestart = viewModel::onRestart,
            onPaywall = viewModel::onPaywallClicked,
            onFinish = onFinish,
            onBack = onBack,
        )
    }
}

/** Stateless session content; previews and UI tests drive this. "Tekrar Dene" after a failure asks again ([onNext]). */
@Composable
fun PracticeSessionContent(
    state: PracticeSessionUiState,
    figureImages: QuestionFigureImages,
    onToggleAnswer: () -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onPaywall: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.current
    val showsQuestion = state.phase == PracticePhase.Question && current != null
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(title = stringResource(state.selection.examType.practiceTitleRes()), onBack = onBack)
        },
        bottomBar = {
            if (showsQuestion) ActionBar(onNext = onNext, onFinish = onFinish)
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (val phase = state.phase) {
            PracticePhase.Loading -> LoadingView(contentModifier)
            is PracticePhase.Failed -> ErrorView(message = phase.message, onRetry = onNext, modifier = contentModifier)
            PracticePhase.Question -> if (current != null) {
                QuestionScreen(
                    state = state,
                    current = current,
                    figureImages = figureImages,
                    onToggleAnswer = onToggleAnswer,
                    modifier = contentModifier,
                )
            } else {
                LoadingView(contentModifier)
            }
            PracticePhase.Exhausted -> EndState(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.practice_session_exhausted_title),
                message = stringResource(R.string.practice_session_exhausted_message),
                tag = "practice.exhausted",
                primaryLabel = stringResource(R.string.practice_session_restart),
                primaryIcon = Icons.Filled.Refresh,
                primaryTag = "practice.restart",
                onPrimary = onRestart,
                onFinish = onFinish,
                modifier = contentModifier,
            )
            PracticePhase.LimitReached -> EndState(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.practice_session_limit_title),
                message = stringResource(R.string.practice_session_limit_message),
                tag = "practice.limit",
                primaryLabel = stringResource(R.string.practice_session_paywall),
                primaryIcon = Icons.Filled.Star,
                primaryTag = "practice.paywall",
                onPrimary = onPaywall,
                onFinish = onFinish,
                modifier = contentModifier,
            )
        }
    }
}

/** The source line and the free counter above the card, then the card; scrolled to the top for each new question. */
@Composable
private fun QuestionScreen(
    state: PracticeSessionUiState,
    current: PracticeQuestion,
    figureImages: QuestionFigureImages,
    onToggleAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(current.id) { scrollState.scrollTo(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("practice.session"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = READABLE_WIDTH).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(sourceLine = current.sourceLine, remainingToday = state.remainingToday)
            QuestionCard(
                question = current.question,
                isAnswerRevealed = state.isAnswerRevealed,
                onToggleAnswer = onToggleAnswer,
                figureImages = figureImages,
                title = stringResource(R.string.practice_session_question_title, state.questionNumber),
            )
        }
    }
}

/** "BOZOK ÜNİVERSİTESİ · 2019 FİNAL" and, for free users only, "Bugün kalan: N soru". */
@Composable
private fun Header(sourceLine: String?, remainingToday: Int?) {
    if (sourceLine == null && remainingToday == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (sourceLine != null) {
            Text(
                text = sourceLine,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("practice.source"),
            )
        }
        if (remainingToday != null) {
            Text(
                text = pluralStringResource(R.plurals.practice_session_remaining_today, remainingToday, remainingToday),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("practice.remaining"),
            )
        }
    }
}

/** "Sıradaki soru" and "Bitir", pinned under the card. */
@Composable
private fun ActionBar(onNext: () -> Unit, onFinish: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = READABLE_WIDTH).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(
                    label = stringResource(R.string.practice_session_next),
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    tag = "practice.next",
                    onClick = onNext,
                )
                FinishButton(onClick = onFinish)
            }
        }
    }
}

/** A centred message with a primary action and "Bitir" (pool exhausted, daily limit reached). */
@Composable
private fun EndState(
    icon: ImageVector,
    title: String,
    message: String,
    tag: String,
    primaryLabel: String,
    primaryIcon: ImageVector,
    primaryTag: String,
    onPrimary: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
        Column(
            modifier = Modifier
                .widthIn(max = READABLE_WIDTH)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.widthIn(max = READABLE_WIDTH).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(label = primaryLabel, icon = primaryIcon, tag = primaryTag, onClick = onPrimary)
            FinishButton(onClick = onFinish)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text = label)
    }
}

@Composable
private fun FinishButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("practice.finish"),
    ) {
        Text(text = stringResource(R.string.practice_session_finish))
    }
}

/** Cards wider than this are hard to read; tablets and landscape centre them (iOS `Layout.readableWidth`). */
private val READABLE_WIDTH = 700.dp

private val PreviewSelection = PracticeSelection(
    subjectId = "topoloji-1",
    examType = ExamType.FINAL,
    scope = PracticeScope.TURKEY,
    courseId = "TOPOLOJİ 1",
    universityId = null,
    poolCount = 3,
)

@ViFiThemePreviews
@Composable
private fun PracticeSessionQuestionPreview() {
    ViFiTheme {
        PracticeSessionContent(
            state = PracticeSessionUiState(
                selection = PreviewSelection,
                phase = PracticePhase.Question,
                current = PracticeQuestion(
                    SampleQuestions.questions("mock-exam").first(),
                    examTitle = "2019 FİNAL",
                    universityName = "Bozok Üniversitesi",
                ),
                questionNumber = 1,
                remainingToday = 4,
            ),
            figureImages = QuestionFigureImages.Unavailable,
            onToggleAnswer = {}, onNext = {}, onRestart = {}, onPaywall = {}, onFinish = {}, onBack = {},
        )
    }
}

@ViFiThemePreviews
@Composable
private fun PracticeSessionLimitPreview() {
    ViFiTheme {
        PracticeSessionContent(
            state = PracticeSessionUiState(selection = PreviewSelection, phase = PracticePhase.LimitReached),
            figureImages = QuestionFigureImages.Unavailable,
            onToggleAnswer = {}, onNext = {}, onRestart = {}, onPaywall = {}, onFinish = {}, onBack = {},
        )
    }
}
