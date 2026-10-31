package com.salticusteam.vifi.feature.notificationcomposer

import androidx.annotation.StringRes
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.testing.MockApp
import com.salticusteam.vifi.testing.TIMEOUT_MS
import com.salticusteam.vifi.testing.assertNeverAppears
import com.salticusteam.vifi.testing.hasTestTagPrefix
import com.salticusteam.vifi.testing.openAccountFromHome
import com.salticusteam.vifi.testing.openComposerFromHome
import com.salticusteam.vifi.testing.scrollToAndClick
import com.salticusteam.vifi.testing.waitForTag
import com.salticusteam.vifi.testing.waitForTagGone
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * iOS `NotificationComposerUITests` (6) on the mock app, end to end from Hesap › Bildirim Gönder (`vifi_admin`): the
 * sample audience gives iOS's numbers (64 → 40 → 28 → 17, Sakarya 24, Kadın 9); sends land in the in-memory history.
 * The archive walk uses the mock archive (İTÜ › Mühendislik › Bilgisayar Mühendisliği › BLG 101 › 2020 Final).
 */
@HiltAndroidTest
class NotificationComposerUiTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @After
    fun tearDown() {
        MockApp.reset()
    }

    @Test
    fun entryIsHiddenFromNonAdmins() {
        MockApp.launch().use {
            composeRule.openAccountFromHome()
            composeRule.onNodeWithTag("account.list").performScrollToNode(hasTestTag("account.notificationSettings"))

            composeRule.assertNeverAppears("account.notificationComposer", forMillis = 1_000)
            composeRule.onNodeWithTag("account.moderation").assertDoesNotExist()
        }
    }

    @Test
    fun segmentCascadeNarrowsTheEstimate() {
        MockApp.launch(LaunchArg.ADMIN).use {
            composeRule.openComposerFromHome()
            composeRule.waitUntil(TIMEOUT_MS) { estimateReads(64) }

            composeRule.composer("audience.segment").performScrollTo().performClick()
            composeRule.pickComposerOption("university", "$COMPOSER_TAG.university.option.bogazici-universitesi")
            composeRule.waitUntil(TIMEOUT_MS) { estimateReads(40) }
            composeRule.pickComposerOption("faculty", "$COMPOSER_TAG.faculty.option.muhendislik-fakultesi")
            composeRule.waitUntil(TIMEOUT_MS) { estimateReads(28) }
            composeRule.pickComposerOption("department", "$COMPOSER_TAG.department.option.bilgisayar-muhendisligi")
            composeRule.waitUntil(TIMEOUT_MS) { estimateReads(17) }

            // Another university resets the faculty and the department below it.
            composeRule.pickComposerOption("university", "$COMPOSER_TAG.university.option.sakarya-universitesi")
            composeRule.waitUntil(TIMEOUT_MS) { estimateReads(24) }
            composeRule.composer("department.field").assertIsNotEnabled()

            composeRule.composer("gender.female").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT_MS) { estimateReads(9) }
        }
    }

    @Test
    fun adminSendsAnAnnouncementAfterConfirming() {
        MockApp.launch(LaunchArg.ADMIN).use {
            composeRule.openComposerFromHome()
            composeRule.fillTitleAndBody(ANNOUNCEMENT)

            composeRule.sendAndConfirm()
            composeRule.openHistory()

            composeRule.historyRow(ANNOUNCEMENT)
                .assert(hasText(string(R.string.notification_history_status_sent), substring = true))
        }
    }

    @Test
    fun decliningTheConfirmationSendsNothing() {
        MockApp.launch(LaunchArg.ADMIN).use {
            composeRule.openComposerFromHome()
            composeRule.fillTitleAndBody(ANNOUNCEMENT)

            composeRule.composer("send").performScrollTo().performClick()
            composeRule.waitForTag("$COMPOSER_TAG.confirm")
            composeRule.composer("confirm.cancel").performClick()
            composeRule.waitForTagGone("$COMPOSER_TAG.confirm")
            composeRule.composer("success").assertDoesNotExist()
            composeRule.composer("title").performScrollTo().assertTextContains(ANNOUNCEMENT)

            composeRule.openHistory()
            composeRule.waitForTag("$HISTORY_PREFIX.row.sample-general")
            composeRule.onAllNodes(hasTestTagPrefix("$HISTORY_PREFIX.row.") and hasText(ANNOUNCEMENT, substring = true))
                .assertCountEquals(0)
        }
    }

    @Test
    fun scheduledNotificationIsCanceledFromHistory() {
        MockApp.launch(LaunchArg.ADMIN).use {
            composeRule.openComposerFromHome()
            composeRule.fillTitleAndBody(SCHEDULED)
            composeRule.composer("scheduleToggle").performScrollTo().performClick()
            composeRule.composer("scheduledAt").performScrollTo()

            composeRule.sendAndConfirm()
            composeRule.openHistory()

            composeRule.historyRow(SCHEDULED)
                .assert(hasText(string(R.string.notification_history_status_scheduled), substring = true))
            val rowTag = composeRule.historyRowTag(SCHEDULED)
            composeRule.onNodeWithTag("$rowTag.menu").performClick()
            composeRule.waitForTag("$rowTag.cancel")
            composeRule.onNodeWithTag("$rowTag.cancel").performClick()

            val canceled = string(R.string.notification_history_status_canceled)
            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.onAllNodes(hasTestTagPrefix(rowTag) and hasText(canceled, substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("$rowTag.menu").assertDoesNotExist()
        }
    }

    @Test
    fun adminLinksAnExamPickedFromTheArchive() {
        MockApp.launch(LaunchArg.ADMIN).use {
            composeRule.openComposerFromHome()
            composeRule.fillTitleAndBody(EXAM_NOTICE)
            composeRule.pickComposerOption("action", "$COMPOSER_TAG.action.exam")
            composeRule.composer("send").performScrollTo().assertIsNotEnabled()

            composeRule.composer("exam").performScrollTo().performClick()
            for (name in ARCHIVE_WALK) {
                composeRule.waitForTag("$COMPOSER_TAG.exam.list")
                composeRule.scrollToAndClick("$COMPOSER_TAG.exam.list", "$COMPOSER_TAG.exam.row.$name")
            }

            composeRule.waitForTagGone("$COMPOSER_TAG.exam.list")
            composeRule.composer("exam").performScrollTo().assertTextContains(ARCHIVE_WALK.last(), substring = true)
            composeRule.composer("examPath").assertTextContains(ARCHIVE_WALK.dropLast(1).joinToString(" › "))
            composeRule.composer("send").performScrollTo().assertIsEnabled()
        }
    }

    // Helpers
    private fun estimateReads(people: Int): Boolean =
        composeRule.onAllNodes(hasTestTag("$COMPOSER_TAG.estimate") and hasText("≈ $people ", substring = true))
            .fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int) = MockApp.context.getString(id)

    private companion object {
        const val ANNOUNCEMENT = "NIT-03 duyuru"
        const val SCHEDULED = "NIT-03 zamanlanmış"
        const val EXAM_NOTICE = "NIT-03 sınav"

        val ARCHIVE_WALK = listOf(
            "İstanbul Teknik Üniversitesi",
            "Mühendislik",
            "Bilgisayar Mühendisliği",
            "BLG 101",
            "2020 Final",
        )
    }
}
