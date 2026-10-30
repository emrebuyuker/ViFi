package com.salticusteam.vifi.feature.inbox

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.hilt.work.HiltWorkerFactory
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.testing.MockApp
import com.salticusteam.vifi.testing.TestWorkManager
import com.salticusteam.vifi.testing.assertNeverAppears
import com.salticusteam.vifi.testing.hasTestTagPrefix
import com.salticusteam.vifi.testing.openInboxFromHome
import com.salticusteam.vifi.testing.tapBack
import com.salticusteam.vifi.testing.tapWhenSingle
import com.salticusteam.vifi.testing.waitForHome
import com.salticusteam.vifi.testing.waitForTag
import com.salticusteam.vifi.testing.waitForTagGone
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * iOS `InboxUITests` (13) on the mock app, end to end: the sample inbox (`a1`, `p-exam`, `p-paywall`, `p-exam-locked`,
 * `a2`, `p-location`; `a-expired` hidden), its actions through the archive's lock, and a tapped push
 * (`vifi_open_notification <id>`). iOS's `inbox.done` is the top bar's back arrow; `home.list` is the home list.
 */
@HiltAndroidTest
class InboxUiTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @After
    fun tearDown() {
        MockApp.reset()
    }

    @Test
    fun openingTheInboxShowsAnnouncements() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()

            composeRule.onNodeWithTag("inbox.row.a1").assertIsDisplayed()
        }
    }

    @Test
    fun inboxMergesBothSourcesAndHidesExpiredEntries() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()

            // The list's own key → position mapping: newest first across both sources, the expired one left out.
            val indexOf = composeRule.onNodeWithTag("inbox.list").fetchSemanticsNode()
                .config[SemanticsProperties.IndexForKey]
            assertEquals(VISIBLE_ORDER.indices.toList(), VISIBLE_ORDER.map(indexOf))
            assertEquals(-1, indexOf("a-expired"))
            composeRule.onNodeWithTag("inbox.row.a-expired").assertDoesNotExist()
            // A plain announcement has no button.
            composeRule.onNodeWithTag("inbox.row.a1").assertIsDisplayed()
            composeRule.onNodeWithTag("inbox.row.a1.action").assertDoesNotExist()
        }
    }

    @Test
    fun examActionOpensTheExamBehindTheInbox() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()

            tapAction("p-exam")

            composeRule.waitForTag("exam.pdf")
            composeRule.waitForTagGone("inbox.list")
            // The exam sits on its archive hierarchy (iOS openExam), so Back walks up the archive, not to the inbox.
            composeRule.tapBack()
            composeRule.waitForTag("browse.list")
            composeRule.onNodeWithTag("inbox.list").assertDoesNotExist()
        }
    }

    @Test
    fun tappingTheCardRunsTheAction() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()

            tapCard("p-exam")

            composeRule.waitForTag("exam.pdf")
        }
    }

    @Test
    fun premiumActionOpensThePaywall() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()

            tapAction("p-paywall")

            composeRule.waitForTag("paywall.close")
        }
    }

    @Test
    fun subscribersAreNotOfferedThePremiumButton() {
        MockApp.launch(LaunchArg.SUBSCRIBED).use {
            composeRule.openInboxFromHome()

            scrollTo("p-paywall")
            composeRule.waitForTagGone("inbox.row.p-paywall.action")
            composeRule.onNodeWithTag("inbox.row.p-paywall").assertIsDisplayed()
            // The card itself is inert too.
            tapCard("p-paywall")
            composeRule.assertNeverAppears("paywall.sheet", forMillis = 1_000)
        }
    }

    @Test
    fun lockedExamFromANotificationGoesThroughTheUnlockSheet() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()

            tapAction("p-exam-locked")
            composeRule.waitForTag("exam.unlock.sheet")
            composeRule.onNodeWithTag("exam.unlock.confirm").performClick()

            composeRule.waitForTag("exam.images")
            composeRule.onNodeWithTag("exam.unlock.sheet").assertDoesNotExist()
        }
    }

    @Test
    fun lockedExamWithoutCoinsLeadsToEarningThem() {
        hiltRule.inject()
        TestWorkManager.install(workerFactory)
        MockApp.launch(LaunchArg.NO_COINS).use {
            composeRule.openInboxFromHome()

            tapAction("p-exam-locked")
            composeRule.waitForTag("exam.unlock.sheet")
            composeRule.onNodeWithTag("exam.unlock.confirm").assertDoesNotExist()
            composeRule.onNodeWithTag("exam.unlock.earn").performClick()

            // "Sınav Ekle", opened on the exam's lesson.
            composeRule.waitForTag("addExam.university")
            composeRule.onNodeWithTag("addExam.lesson").assertTextContains("BLG 101", substring = true)
        }
    }

    @Test
    fun subscriberOpensALockedExamFromANotificationDirectly() {
        MockApp.launch(LaunchArg.SUBSCRIBED).use {
            composeRule.openInboxFromHome()
            // Premium is known once its button is gone.
            scrollTo("p-paywall")
            composeRule.waitForTagGone("inbox.row.p-paywall.action")

            tapAction("p-exam-locked")

            composeRule.waitForTag("exam.images")
            composeRule.onNodeWithTag("exam.unlock.sheet").assertDoesNotExist()
        }
    }

    @Test
    fun notificationImageIsShownOnItsCard() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()

            scrollTo("p-exam-locked")
            composeRule.onNodeWithTag("inbox.row.p-exam-locked.image").assertIsDisplayed()
            scrollTo("p-paywall")
            composeRule.onNodeWithTag("inbox.row.p-paywall.image").assertDoesNotExist()
        }
    }

    @Test
    fun tappedPushOpensTheInboxOnItsNotification() {
        MockApp.launch(openNotification = "p-paywall").use {
            composeRule.waitForTag("inbox.list")

            composeRule.waitUntil(TIMEOUT) { isSelected("p-paywall") }
            composeRule.onNodeWithTag("inbox.row.p-paywall").assertIsDisplayed().assert(selected(true))
            scrollTo("a1")
            composeRule.onNodeWithTag("inbox.row.a1").assert(selected(false))
        }
    }

    @Test
    fun tappedPushOfAnUnknownNotificationJustOpensTheInbox() {
        MockApp.launch(openNotification = "does-not-exist").use {
            composeRule.waitForTag("inbox.list")

            composeRule.onNodeWithTag("inbox.row.a1").assertIsDisplayed()
            composeRule.onAllNodes(hasTestTagPrefix("inbox.row.") and selected(true)).assertCountEquals(0)
        }
    }

    @Test
    fun closingTheInboxOpenedByAPushReturnsToHome() {
        MockApp.launch(openNotification = "a1").use {
            composeRule.waitForTag("inbox.list")

            composeRule.tapBack()

            composeRule.waitForHome()
            composeRule.waitForTagGone("inbox.list")
            // The push was used up: the inbox does not come back by itself.
            composeRule.assertNeverAppears("inbox.list")
        }
    }

    // Helpers
    private fun scrollTo(id: String) {
        composeRule.onNodeWithTag("inbox.list").performScrollToKey(id)
    }

    private fun tapAction(id: String) {
        scrollTo(id)
        composeRule.tapWhenSingle("inbox.row.$id.action")
    }

    /** Taps the card's text, above its image and button (the card runs the same action as the button). */
    private fun tapCard(id: String) {
        scrollTo(id)
        composeRule.onNodeWithTag("inbox.row.$id").performTouchInput { click(Offset(centerX, CARD_TEXT_Y.toPx())) }
    }

    private fun isSelected(id: String): Boolean =
        composeRule.onAllNodesWithTag("inbox.row.$id").fetchSemanticsNodes()
            .any { it.config.getOrNull(SemanticsProperties.Selected) == true }

    private fun selected(value: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, value)

    private companion object {
        val VISIBLE_ORDER = listOf("a1", "p-exam", "p-paywall", "p-exam-locked", "a2", "p-location")

        /** Inside the card's text row: 16 dp padding, then the title. */
        val CARD_TEXT_Y = 28.dp

        const val TIMEOUT = 5_000L
    }
}
