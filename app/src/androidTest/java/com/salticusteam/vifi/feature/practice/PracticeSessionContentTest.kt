package com.salticusteam.vifi.feature.practice

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.PracticeSelection
import com.salticusteam.vifi.feature.structuredexam.QuestionFigureImages
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "Sınava gir" session on the stateless content (iOS `PracticeSessionView` + `PracticeUITests`): the question card
 * with its running number and source line, reveal, the free counter (free users only), "Sıradaki soru" / "Bitir", the
 * limit screen's paywall button, the exhausted screen's "Baştan başla", the error's retry and the loading state.
 */
@RunWith(AndroidJUnit4::class)
class PracticeSessionContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selection = PracticeSelection(
        subjectId = "topoloji-1",
        examType = ExamType.FINAL,
        scope = PracticeScope.TURKEY,
        courseId = "topoloji-1",
        universityId = null,
        poolCount = 3,
    )

    private val question = PracticeQuestion(
        SampleQuestions.questions("mock-exam").first(),
        examTitle = "2019 FİNAL",
        universityName = "Bozok Üniversitesi",
    )
    private val order = question.question.order

    private val questionState = PracticeSessionUiState(
        selection = selection,
        phase = PracticePhase.Question,
        current = question,
        questionNumber = 2,
        remainingToday = 3,
    )

    private var toggles = 0
    private var nexts = 0
    private var restarts = 0
    private var paywalls = 0
    private var finishes = 0
    private var backs = 0

    private fun setContent(state: PracticeSessionUiState) {
        composeRule.setContent {
            ViFiTheme {
                PracticeSessionContent(
                    state = state,
                    figureImages = QuestionFigureImages.Unavailable,
                    onToggleAnswer = { toggles++ },
                    onNext = { nexts++ },
                    onRestart = { restarts++ },
                    onPaywall = { paywalls++ },
                    onFinish = { finishes++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun question_showsCardWithRunningNumberAndSource_andTheButtonsReport() {
        setContent(questionState)

        composeRule.onNodeWithTag("practice.session").assertIsDisplayed()
        composeRule.onNodeWithTag("questions.card.$order").assertIsDisplayed()
        composeRule.onNodeWithTag("questions.title.$order", useUnmergedTree = true).assert(hasText("Soru 2"))
        composeRule.onNodeWithTag("practice.source").assert(hasText("BOZOK ÜNİVERSİTESİ · 2019 FİNAL"))

        composeRule.onNodeWithTag("practice.next").performClick()
        composeRule.onNodeWithTag("practice.finish").performClick()
        composeRule.onNodeWithTag("topBar.backButton").performClick()

        assertEquals(1, nexts)
        assertEquals(1, finishes)
        assertEquals(1, backs)
    }

    @Test
    fun question_answerHiddenUntilRevealed_andRevealReports() {
        setContent(questionState)

        composeRule.onNodeWithTag("questions.answer.$order", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("questions.reveal.$order").performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun question_revealedShowsTheAnswer() {
        setContent(questionState.copy(isAnswerRevealed = true))

        composeRule.onNodeWithTag("questions.answer.$order", useUnmergedTree = true).assertExists()
    }

    @Test
    fun freeUser_seesTheRemainingCounter() {
        setContent(questionState)

        composeRule.onNodeWithTag("practice.remaining").assert(hasText("Bugün kalan: 3 soru"))
    }

    // iOS PracticeUITests.testPremiumSeesNoCounter
    @Test
    fun premium_seesNoCounter() {
        setContent(questionState.copy(remainingToday = null))

        composeRule.onNodeWithTag("practice.session").assertIsDisplayed()
        composeRule.onNodeWithTag("practice.remaining").assertDoesNotExist()
    }

    // iOS PracticeUITests.testFreeLimitReachedOffersThePaywall
    @Test
    fun limitReached_offersThePaywall_andFinish() {
        setContent(PracticeSessionUiState(selection = selection, phase = PracticePhase.LimitReached))

        composeRule.onNodeWithTag("practice.limit").assertIsDisplayed()
        composeRule.onNodeWithTag("practice.next").assertDoesNotExist()
        composeRule.onNodeWithTag("practice.paywall").performClick()
        composeRule.onNodeWithTag("practice.finish").performClick()

        assertEquals(1, paywalls)
        assertEquals(1, finishes)
    }

    @Test
    fun exhausted_offersRestart() {
        setContent(PracticeSessionUiState(selection = selection, phase = PracticePhase.Exhausted))

        composeRule.onNodeWithTag("practice.exhausted").assertIsDisplayed()
        composeRule.onNodeWithTag("practice.restart").performClick()

        assertEquals(1, restarts)
    }

    @Test
    fun failed_showsTheMessage_andRetryAsksAgain() {
        setContent(PracticeSessionUiState(selection = selection, phase = PracticePhase.Failed("Bağlantı yok.")))

        composeRule.onNodeWithTag("state.error").assertIsDisplayed()
        composeRule.onNodeWithTag("state.retry").performClick()

        assertEquals(1, nexts)
    }

    @Test
    fun loading_showsTheSpinnerOnly() {
        setContent(PracticeSessionUiState(selection = selection))

        composeRule.onNodeWithTag("state.loading").assertIsDisplayed()
        composeRule.onNodeWithTag("practice.next").assertDoesNotExist()
    }
}
