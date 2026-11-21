package com.salticusteam.vifi.feature.practice

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.hilt.work.HiltWorkerFactory
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.testing.MockApp
import com.salticusteam.vifi.testing.TestWorkManager
import com.salticusteam.vifi.testing.assertNeverAppears
import com.salticusteam.vifi.testing.scrollToAndClick
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
 * iOS `PracticeUITests` on the mock archive, end to end: TOPOLOJİ 1 offers "Sınava gir" (another course does not),
 * type → scope → session (reveal, next, "Bitir" back to the course), the pool's end with "Baştan başla", the free
 * daily limit's paywall prompt (`vifi_practice_limit_reached`, iOS `-ViFiPracticeLimitReached`) and Premium's missing
 * counter (`vifi_subscribed`).
 */
@HiltAndroidTest
class PracticeUiTest {

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

    // iOS testPracticeFlowFromCourseToQuestions
    @Test
    fun practiceFlowFromCourseToQuestions() {
        MockApp.launch().use {
            open(COURSE)
            composeRule.tapWhenSingle("practice.start")

            // Type step: only types with questions (final and vize in the mock index).
            composeRule.waitForTag("practice.type.final")
            composeRule.onNodeWithTag("practice.type.vize").assertExists()
            composeRule.onNodeWithTag("practice.type.butunleme").assertDoesNotExist()
            composeRule.tapWhenSingle("practice.type.final")

            // Scope step: all three scopes for final, each with a count.
            composeRule.waitForTag("practice.scope.course")
            composeRule.onNodeWithTag("practice.scope.university").assertExists()
            composeRule.onNodeWithTag("practice.scope.tr").assertExists()
            composeRule.onNodeWithTag("practice.scope.course").assert(hasText("3 soru", substring = true))
            composeRule.tapWhenSingle("practice.scope.course")

            // Session: a question card, the free counter, reveal, next.
            composeRule.waitForTag("practice.session")
            composeRule.waitForTag("practice.remaining")
            composeRule.waitForTag("questions.card.1")
            composeRule.onNodeWithTag("questions.answer.1", useUnmergedTree = true).assertDoesNotExist()
            composeRule.tapWhenSingle("questions.reveal.1")
            composeRule.waitForTag("questions.answer.1")

            composeRule.tapWhenSingle("practice.next")
            composeRule.waitForTag("questions.card.2")
            // The next question starts with its answer hidden.
            composeRule.onNodeWithTag("questions.answer.2", useUnmergedTree = true).assertDoesNotExist()

            // "Bitir" leaves the session and the setup: back on the course.
            composeRule.tapWhenSingle("practice.finish")
            composeRule.waitForTag("practice.start")
            composeRule.onNodeWithTag("practice.setup").assertDoesNotExist()
        }
    }

    // iOS testCourseWithoutPracticeQuestionsHasNoEntry
    @Test
    fun courseWithoutPracticeQuestionsHasNoEntry() {
        MockApp.launch().use {
            open(COURSE_WITHOUT_PRACTICE)

            composeRule.waitForTag("row.2023 Vize")
            composeRule.assertNeverAppears("practice.start")
        }
    }

    @Test
    fun poolEnd_offersRestart_whichStartsOver() {
        MockApp.launch().use {
            startSession(scope = "practice.scope.course")
            composeRule.waitForTag("questions.card.1")
            composeRule.tapWhenSingle("practice.next")
            composeRule.waitForTag("questions.card.2")
            composeRule.tapWhenSingle("practice.next")
            composeRule.waitForTag("questions.card.3")
            composeRule.tapWhenSingle("practice.next")

            composeRule.waitForTag("practice.exhausted")
            composeRule.tapWhenSingle("practice.restart")

            composeRule.waitForTag("questions.card.1")
        }
    }

    // iOS testFreeLimitReachedOffersThePaywall
    @Test
    fun freeLimitReachedOffersThePaywall() {
        MockApp.launch(LaunchArg.PRACTICE_LIMIT_REACHED).use {
            startSession(scope = "practice.scope.tr")

            composeRule.waitForTag("practice.limit")
            composeRule.tapWhenSingle("practice.paywall")
            composeRule.waitForTag("paywall.sheet")
        }
    }

    // iOS testPremiumSeesNoCounter
    @Test
    fun premiumSeesNoCounter() {
        MockApp.launch(LaunchArg.SUBSCRIBED).use {
            startSession(scope = "practice.scope.tr")

            composeRule.waitForTag("practice.session")
            composeRule.waitForTag("questions.card.1")
            composeRule.onNodeWithTag("practice.remaining").assertDoesNotExist()
        }
    }

    // --- Helpers ---

    private fun startSession(scope: String) {
        open(COURSE)
        composeRule.tapWhenSingle("practice.start")
        composeRule.tapWhenSingle("practice.type.final")
        composeRule.tapWhenSingle(scope)
    }

    private fun open(path: List<String>) {
        composeRule.waitForHome()
        composeRule.scrollToAndClick("browse.list", "row.${path.first()}")
        path.drop(1).forEach { composeRule.tapWhenSingle("row.$it") }
    }

    private companion object {
        val COURSE = listOf("KOCAELİ ÜNİVERSİTESİ", "MÜHENDİSLİK FAKÜLTESİ", "MATEMATİK BÖLÜMÜ", "TOPOLOJİ 1")
        val COURSE_WITHOUT_PRACTICE =
            listOf("İstanbul Teknik Üniversitesi", "Mühendislik", "Bilgisayar Mühendisliği", "BLG 101")
    }
}
