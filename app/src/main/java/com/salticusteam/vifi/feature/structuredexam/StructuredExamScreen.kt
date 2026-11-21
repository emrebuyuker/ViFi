package com.salticusteam.vifi.feature.structuredexam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.feature.examviewer.ContactSupportAction
import com.salticusteam.vifi.feature.examviewer.ScreenCaptureProtection

/**
 * The question screen of a structured exam (4.2.0, iOS `StructuredExamView`): its questions as cards, each with
 * "Cevabı Göster". Reached through `ExamRouting` only, after the archive's lock rule let the user in; the whole screen
 * (question content included) is capture-protected like the file viewers.
 *
 * @param questionAdminAction the admin "Yönetici" menu above each card (ADX-05); `null` for everyone else.
 */
@Composable
fun StructuredExamScreen(
    path: ArchivePath,
    kind: ExamKind,
    onBack: () -> Unit,
    questionAdminAction: (@Composable (ExamQuestion) -> Unit)? = null,
    viewModel: StructuredExamViewModel = hiltViewModel<StructuredExamViewModel, StructuredExamViewModel.Factory> {
        it.create(path, kind)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val figureImages = rememberQuestionFigureImages()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    ScreenCaptureProtection {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ViFiTopAppBar(
                    title = state.title,
                    onBack = onBack,
                    // "Bize Ulaş" (iOS toolbar envelope, `exam.contact`): the file viewer's e-mail, with this exam.
                    actions = { ContactSupportAction(supportPath = state.supportPath, testTag = "questions.contactSupport") },
                )
            },
        ) { padding ->
            StructuredExamContent(
                state = state,
                figureImages = figureImages,
                onRetry = viewModel::load,
                onToggleAnswer = viewModel::onToggleAnswer,
                questionAdminAction = questionAdminAction,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** The screen's body for a given state: loading, error with "Tekrar Dene", "Bu sınavda soru yok", or the cards. */
@Composable
fun StructuredExamContent(
    state: StructuredExamUiState,
    figureImages: QuestionFigureImages,
    onRetry: () -> Unit,
    onToggleAnswer: (order: Int) -> Unit,
    modifier: Modifier = Modifier,
    questionAdminAction: (@Composable (ExamQuestion) -> Unit)? = null,
) {
    val questions = state.questions
    when {
        questions == null && state.error != null -> ErrorView(message = state.error, onRetry = onRetry, modifier = modifier)
        questions == null -> LoadingView(modifier = modifier)
        questions.isEmpty() -> EmptyView(
            title = stringResource(R.string.structured_exam_empty),
            icon = Icons.Filled.FormatListNumbered,
            modifier = modifier,
        )
        else -> QuestionList(
            state = state,
            questions = questions,
            figureImages = figureImages,
            onToggleAnswer = onToggleAnswer,
            questionAdminAction = questionAdminAction,
            modifier = modifier,
        )
    }
}

@Composable
private fun QuestionList(
    state: StructuredExamUiState,
    questions: List<ExamQuestion>,
    figureImages: QuestionFigureImages,
    onToggleAnswer: (order: Int) -> Unit,
    questionAdminAction: (@Composable (ExamQuestion) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("questions.list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = HEADER_KEY) {
            ContextHeader(
                subtitle = state.subtitle,
                questionCount = questions.size,
                modifier = Modifier.widthIn(max = READABLE_WIDTH),
            )
        }
        items(items = questions, key = { it.id }) { question ->
            QuestionCard(
                question = question,
                isAnswerRevealed = state.isAnswerRevealed(question.order),
                onToggleAnswer = { onToggleAnswer(question.order) },
                figureImages = figureImages,
                headerAction = questionAdminAction?.let { action -> { action(question) } },
                // Cards wider than this are hard to read; tablets and landscape centre them.
                modifier = Modifier.widthIn(max = READABLE_WIDTH),
            )
        }
    }
}

/** "BOZOK ÜNİVERSİTESİ › MÜHENDİSLİK MATEMATİĞİ · 12 soru", above the first card (one TalkBack element). */
@Composable
private fun ContextHeader(subtitle: String, questionCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("questions.header"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = Icons.Filled.FormatListNumbered, size = 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = subtitle, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                text = questionCountText(questionCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val HEADER_KEY = "questions.header"
private val READABLE_WIDTH = 700.dp

@ViFiThemePreviews
@Composable
private fun StructuredExamContentPreview() {
    val path = ArchivePath(
        listOf("kocaeli", "muh", "mat", "topoloji-1", SampleQuestions.EXAM_ID),
        listOf("KOCAELİ ÜNİVERSİTESİ", "MÜHENDİSLİK FAKÜLTESİ", "MATEMATİK BÖLÜMÜ", "TOPOLOJİ 1", "2003 FİNAL"),
    )
    ViFiTheme {
        StructuredExamContent(
            state = StructuredExamUiState(
                path = path,
                kind = ExamKind.IMAGES,
                isLoading = false,
                questions = SampleQuestions.questions(),
                revealedOrders = setOf(2),
            ),
            figureImages = QuestionFigureImages.Unavailable,
            onRetry = {},
            onToggleAnswer = {},
        )
    }
}
