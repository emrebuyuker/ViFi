package com.salticusteam.vifi.feature.structuredexam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamQuestion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The question screen's content on the mock sample (KOCAELİ › TOPOLOJİ 1 › 2003 FİNAL, 3 questions): iOS
 * `StructuredExamUITests` (answer hidden until revealed) plus the states, the AI note, the figure failure and the
 * TalkBack tree (a hidden answer is not in the semantics tree at all).
 */
@RunWith(AndroidJUnit4::class)
class StructuredExamContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val path = ArchivePath(
        listOf("kocaeli-universitesi", "kocaeli-muhendislik", "kocaeli-muhendislik-matematik", "topoloji-1", SampleQuestions.EXAM_ID),
        listOf("KOCAELİ ÜNİVERSİTESİ", "MÜHENDİSLİK FAKÜLTESİ", "MATEMATİK BÖLÜMÜ", "TOPOLOJİ 1", "2003 FİNAL"),
    )
    private val questions = SampleQuestions.questions()

    /** Shows the content with a local revealed set, like the screen does with its ViewModel. */
    private fun show(state: StructuredExamUiState, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            var revealed by remember { mutableStateOf(state.revealedOrders) }
            ViFiTheme {
                StructuredExamContent(
                    state = state.copy(revealedOrders = revealed),
                    figureImages = QuestionFigureImages.Unavailable,
                    onRetry = onRetry,
                    onToggleAnswer = { order -> revealed = if (order in revealed) revealed - order else revealed + order },
                )
            }
        }
    }

    private fun loaded(questions: List<ExamQuestion> = this.questions) =
        StructuredExamUiState(path = path, kind = ExamKind.IMAGES, isLoading = false, questions = questions)

    @Test
    fun structuredExam_listsItsQuestions_andRevealsTheAnswerOnTap() {
        show(loaded())

        composeRule.onNodeWithTag("questions.list").assertIsDisplayed()
        composeRule.onNodeWithTag("questions.card.1").assertIsDisplayed()
        composeRule.onNodeWithTag("questions.title.1").assert(hasText("1. Soru"))
        composeRule.onNodeWithTag("questions.answer.1", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("questions.reveal.1").performClick()

        composeRule.onNodeWithTag("questions.answer.1").assertIsDisplayed()
        composeRule.onNodeWithTag("questions.aiNote.1", useUnmergedTree = true)
            .assert(hasText("Bu çözüm yapay zekâ ile hazırlanmış ve incelenmiştir."))
        composeRule.onNodeWithTag("questions.reveal.1").assert(hasText("Cevabı Gizle", substring = true))
    }

    @Test
    fun hidingTheAnswer_removesItFromTheSemanticsTree() {
        show(loaded().copy(revealedOrders = setOf(1)))
        composeRule.onNodeWithTag("questions.answer.1").assertExists()

        composeRule.onNodeWithTag("questions.reveal.1").performClick()

        composeRule.onNodeWithTag("questions.answer.1", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("questions.aiNote.1", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("questions.reveal.1").assert(hasText("Cevabı Göster", substring = true))
    }

    @Test
    fun aRevealedChoiceQuestion_marksTheCorrectChoiceAndNamesIt() {
        show(loaded().copy(revealedOrders = setOf(2)))
        composeRule.onNodeWithTag("questions.list").performScrollToNode(hasTestTag("questions.card.2"))

        composeRule.onNodeWithTag("questions.choice.2.A", useUnmergedTree = true).assertExists()
        nodesIn("questions.choice.2.A", hasText("A ✓")).assertCountEquals(1)
        nodesIn("questions.choice.2.B", hasText("B")).assertCountEquals(1)
        composeRule.onNodeWithTag("questions.correctChoice.2", useUnmergedTree = true).assert(hasText("Doğru seçenek: A"))
    }

    @Test
    fun aHiddenChoiceQuestion_doesNotMarkTheCorrectChoice() {
        show(loaded())
        composeRule.onNodeWithTag("questions.list").performScrollToNode(hasTestTag("questions.card.2"))

        nodesIn("questions.choice.2.A", hasText("A ✓")).assertCountEquals(0)
    }

    @Test
    fun talkBack_readsTheQuestionSpeech_thenTheFigure_thenTheChoices() {
        show(loaded())
        composeRule.onNodeWithTag("questions.list").performScrollToNode(hasTestTag("questions.card.2"))
        composeRule.onNodeWithTag("questions.question.2").assert(
            hasContentDescription(
                "f x eşittir x küp eksi 3 x fonksiyonunun yerel maksimum noktası hangisidir? " +
                    "A: x = -1 B: x = 0 C: x = 1 D: x = 3",
            ),
        )

        composeRule.onNodeWithTag("questions.list").performScrollToNode(hasTestTag("questions.card.3"))
        composeRule.onNodeWithTag("questions.question.3").assert(
            hasContentDescription(
                "Şekildeki devrede R bir 2 ohm, R iki 4 ohm ve V 12 volt ise toplam akımı bulunuz. Şekil var.",
            ),
        )
    }

    @Test
    fun talkBack_readsTheAnswerOnlyOnceRevealed() {
        show(loaded().copy(revealedOrders = setOf(1)))

        // answer.speech → the steps (delimiters removed) → the note.
        composeRule.onNodeWithTag("questions.answer.1")
            .assert(hasContentDescription("Cevap: üçte bir Çözüm: Ters türev: ", prefixOnly = true))
            .assert(hasContentDescription("Sınırlar yerine konur:", substring = true))
            .assert(hasContentDescription(" Bu çözüm yapay zekâ ile hazırlanmış ve incelenmiştir.", substring = true))
        composeRule.onNodeWithTag("questions.title.1").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun aFigureThatCannotLoad_saysSo() {
        show(loaded(listOf(questions[2])))

        composeRule.onNodeWithTag("questions.figure.failed", useUnmergedTree = true)
            .assert(hasText("Şekil yüklenemedi"))
    }

    @Test
    fun anExamWithoutQuestions_saysSo() {
        show(loaded(emptyList()))

        composeRule.onNodeWithTag("state.empty").assertIsDisplayed()
        nodesIn("state.empty", hasText("Bu sınavda soru yok")).assertCountEquals(1)
    }

    @Test
    fun aFailedLoad_showsTheErrorAndRetries() {
        var retries = 0
        show(
            StructuredExamUiState(path = path, kind = ExamKind.IMAGES, isLoading = false, error = ArchiveError.Offline.userMessage),
            onRetry = { retries++ },
        )

        composeRule.onNodeWithTag("state.error").assertIsDisplayed()
        composeRule.onNodeWithTag("state.retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun aFailedReload_keepsTheQuestionsOnScreen() {
        show(loaded().copy(error = ArchiveError.Offline.userMessage))

        composeRule.onNodeWithTag("questions.list").assertIsDisplayed()
        composeRule.onNodeWithTag("state.error").assertDoesNotExist()
    }

    @Test
    fun whileLoading_showsTheSpinner() {
        show(StructuredExamUiState(path = path, kind = ExamKind.IMAGES))

        composeRule.onNodeWithTag("state.loading").assertIsDisplayed()
    }

    @Test
    fun theHeader_namesWhereTheExamComesFromAndItsQuestionCount() {
        show(loaded())

        nodesIn("questions.header", hasText("KOCAELİ ÜNİVERSİTESİ › TOPOLOJİ 1")).assertCountEquals(1)
        nodesIn("questions.header", hasText("3 soru")).assertCountEquals(1)
    }

    // --- Helpers ---

    private fun nodesIn(tag: String, matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    private fun hasContentDescription(value: String, substring: Boolean = false, prefixOnly: Boolean = false) =
        SemanticsMatcher("contentDescription ${if (prefixOnly) "starts with" else "is"} \"$value\"") { node ->
            val descriptions = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            descriptions.any { description ->
                when {
                    prefixOnly -> description.startsWith(value)
                    substring -> description.contains(value)
                    else -> description == value
                }
            }
        }
}
