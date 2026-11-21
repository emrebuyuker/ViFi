package com.salticusteam.vifi.feature.structuredexam

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.hilt.work.HiltWorkerFactory
import com.salticusteam.vifi.core.data.repository.fake.FakePoolIndexReader
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.testing.MockApp
import com.salticusteam.vifi.testing.TestWorkManager
import com.salticusteam.vifi.testing.scrollToAndClick
import com.salticusteam.vifi.testing.tapBack
import com.salticusteam.vifi.testing.tapWhenSingle
import com.salticusteam.vifi.testing.waitForHome
import com.salticusteam.vifi.testing.waitForTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * iOS `StructuredExamUITests` on the mock archive, end to end (KOCAELİ › … › TOPOLOJİ 1 › 2003 FİNAL, 3 questions):
 * the row shows its question count, the exam opens on the question screen, "Cevabı Göster" reveals the answer, and the
 * recents card (which only knows the file kind) lands on the question screen with no file viewer left behind (SQS-01).
 * The admin menu and the capture-shield self-test of iOS belong to ADX-05 / the device checks.
 */
@HiltAndroidTest
class StructuredExamUiTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Before
    fun setUp() {
        hiltRule.inject()
        TestWorkManager.install(workerFactory)
    }

    @After
    fun tearDown() {
        MockApp.reset()
    }

    @Test
    fun structuredExamOpensQuestionsAndRevealsAnswer() {
        MockApp.launch().use {
            openLesson()
            composeRule.tapWhenSingle(EXAM_ROW)

            composeRule.waitForTag("questions.list")
            composeRule.waitForTag("questions.card.1")
            composeRule.onNodeWithTag("questions.answer.1", useUnmergedTree = true).assertDoesNotExist()

            composeRule.tapWhenSingle("questions.reveal.1")

            composeRule.waitForTag("questions.answer.1")
        }
    }

    @Test
    fun structuredExamRowShowsItsQuestionCount() {
        MockApp.launch().use {
            openLesson()

            composeRule.onAllNodes(hasText("3 soru") and hasAnyAncestor(hasTestTag(EXAM_ROW)), useUnmergedTree = true)
                .assertCountEquals(1)
            composeRule.onAllNodes(hasText("Sorular") and hasAnyAncestor(hasTestTag(EXAM_ROW)), useUnmergedTree = true)
                .assertCountEquals(1)
        }
    }

    @Test
    fun recentCardOpensTheQuestionScreen_andBackSkipsTheFileViewer() {
        MockApp.launch().use {
            openLesson()
            composeRule.tapWhenSingle(EXAM_ROW)
            composeRule.waitForTag("questions.list")
            // Back up to the home screen: lesson, department, faculty, university, home.
            repeat(BACKS_TO_HOME) { composeRule.tapBack() }
            composeRule.waitForHome()

            composeRule.tapWhenSingle(RECENT_CARD)

            composeRule.waitForTag("questions.list")
            composeRule.onNodeWithTag("exam.images").assertDoesNotExist()
            composeRule.tapBack()
            composeRule.waitForTag(EXAM_ROW)
        }
    }

    // --- Helpers ---

    private fun openLesson() {
        composeRule.waitForHome()
        composeRule.scrollToAndClick("browse.list", "row.KOCAELİ ÜNİVERSİTESİ")
        composeRule.tapWhenSingle("row.MÜHENDİSLİK FAKÜLTESİ")
        composeRule.tapWhenSingle("row.MATEMATİK BÖLÜMÜ")
        composeRule.tapWhenSingle("row.TOPOLOJİ 1")
        composeRule.waitForTag(EXAM_ROW)
    }

    private companion object {
        const val EXAM_ROW = "row.2003 FİNAL"
        const val BACKS_TO_HOME = 5
        val RECENT_CARD = "home.recent." + listOf(
            "kocaeli-universitesi",
            "kocaeli-muhendislik",
            "kocaeli-muhendislik-matematik",
            FakePoolIndexReader.SAMPLE_COURSE_ID,
            SampleQuestions.EXAM_ID,
        ).joinToString("/")
    }
}
