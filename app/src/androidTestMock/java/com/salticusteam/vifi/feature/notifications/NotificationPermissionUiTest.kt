package com.salticusteam.vifi.feature.notifications

import androidx.annotation.StringRes
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.swipeDown
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.espresso.Espresso
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.testing.MockApp
import com.salticusteam.vifi.testing.TestWorkManager
import com.salticusteam.vifi.testing.answerDocumentPicker
import com.salticusteam.vifi.testing.assertNeverAppears
import com.salticusteam.vifi.testing.openInboxFromHome
import com.salticusteam.vifi.testing.openNotificationSettingsFromHome
import com.salticusteam.vifi.testing.removePickerAnswer
import com.salticusteam.vifi.testing.scrollToAndClick
import com.salticusteam.vifi.testing.tapBack
import com.salticusteam.vifi.testing.tapWhenSingle
import com.salticusteam.vifi.testing.waitForHome
import com.salticusteam.vifi.testing.waitForTag
import com.salticusteam.vifi.testing.waitForTagGone
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * iOS `NotificationPermissionUITests` (9) on the mock app, end to end, plus the two NIT-01 flows that were only tried
 * by hand: the permission step 1/2 → 2/2 (`vifi_notifications_prompt`), Account › Bildirim Ayarları, a permission the
 * system no longer asks for (`vifi_notifications_denied`), the inbox's consent re-ask (`vifi_consent_reask`) and the
 * permission's re-ask after an exam submission (`vifi_notifications_reask`).
 *
 * Android has no quiet ("provisional") permission (NDEC-01): where iOS reads "Sessiz" after "Şimdi Değil", Android
 * reads "Sorulmadı" and still offers "Bildirimleri Aç".
 */
@HiltAndroidTest
class NotificationPermissionUiTest {

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
    fun enablingThenAcceptingTheConsentTurnsCampaignsOn() {
        MockApp.launch(LaunchArg.NOTIFICATIONS_PROMPT).use {
            composeRule.waitForTag("notifications.priming")
            composeRule.onNodeWithTag("notifications.priming.enable").performClick()
            composeRule.waitForTag("notifications.consent")
            composeRule.onNodeWithTag("notifications.consent.accept").performClick()
            composeRule.waitForTagGone("notifications.consent")

            composeRule.openNotificationSettingsFromHome()

            assertPermission(R.string.notification_settings_permission_authorized)
            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOn()
        }
    }

    @Test
    fun enablingThenDecliningTheConsentKeepsCampaignsOff() {
        MockApp.launch(LaunchArg.NOTIFICATIONS_PROMPT).use {
            composeRule.waitForTag("notifications.priming")
            composeRule.onNodeWithTag("notifications.priming.enable").performClick()
            composeRule.waitForTag("notifications.consent")
            composeRule.onNodeWithTag("notifications.consent.decline").performClick()
            composeRule.waitForTagGone("notifications.consent")

            composeRule.openNotificationSettingsFromHome()

            assertPermission(R.string.notification_settings_permission_authorized)
            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOff()
        }
    }

    @Test
    fun notNowClosesThePromptAndLeavesNotificationsUnasked() {
        MockApp.launch(LaunchArg.NOTIFICATIONS_PROMPT).use {
            composeRule.waitForTag("notifications.priming")
            composeRule.onNodeWithTag("notifications.priming.dismiss").performClick()
            composeRule.waitForTagGone("notifications.priming")
            composeRule.assertNeverAppears("notifications.consent", forMillis = 1_000)

            composeRule.openNotificationSettingsFromHome()

            assertPermission(R.string.notification_settings_permission_not_determined)
            composeRule.onNodeWithTag("notificationSettings.enable").assertIsDisplayed()
            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOff()
        }
    }

    @Test
    fun promptWaitsForTheOnboardingTourToEnd() {
        MockApp.launch(LaunchArg.NOTIFICATIONS_PROMPT, LaunchArg.SHOW_ONBOARDING).use {
            composeRule.waitForTag("onboarding.skip")
            composeRule.assertNeverAppears("notifications.priming", forMillis = 1_500)

            composeRule.onNodeWithTag("onboarding.skip").performClick()

            composeRule.waitForTag("notifications.priming")
        }
    }

    @Test
    fun consentPageCannotBeSwipedOrBackedAway() {
        MockApp.launch(LaunchArg.NOTIFICATIONS_PROMPT).use {
            composeRule.waitForTag("notifications.priming")
            composeRule.onNodeWithTag("notifications.priming.enable").performClick()
            composeRule.waitForTag("notifications.consent")

            composeRule.onNodeWithTag("notifications.consent").performTouchInput { swipeDown() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("notifications.consent").assertIsDisplayed()
            // Android's Back is the other way out of a sheet; the consent page refuses it too.
            Espresso.pressBack()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("notifications.consent").assertIsDisplayed()
            composeRule.onNodeWithTag("notifications.consent.accept").assertIsDisplayed()
        }
    }

    @Test
    fun campaignSwitchTurnsOnAndOffAndKeepsItsValue() {
        MockApp.launch().use {
            composeRule.openNotificationSettingsFromHome()
            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOff().performClick()
            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOn()

            reopenSettings()
            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOn().performClick()
            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOff()

            reopenSettings()
            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOff()
        }
    }

    @Test
    fun deniedPermissionSendsTheUserToSettings() {
        MockApp.launch(LaunchArg.NOTIFICATIONS_DENIED).use {
            composeRule.openNotificationSettingsFromHome()

            assertPermission(R.string.notification_settings_permission_denied)
            composeRule.onNodeWithTag("notificationSettings.openSettings").assertIsDisplayed()
            composeRule.onNodeWithTag("notificationSettings.enable").assertDoesNotExist()
        }
    }

    @Test
    fun inboxOffersTheConsentOnceMoreAfterANo() {
        MockApp.launch(LaunchArg.CONSENT_REASK).use {
            composeRule.openInboxFromHome()
            composeRule.waitForTag("notifications.consent")

            composeRule.onNodeWithTag("notifications.consent.decline").performClick()

            composeRule.waitForTagGone("notifications.consent")
            composeRule.onNodeWithTag("inbox.list").assertIsDisplayed()
            // Used up: opening the inbox again asks nothing.
            composeRule.tapBack()
            composeRule.openInboxFromHome()
            composeRule.assertNeverAppears("notifications.consent")
        }
    }

    @Test
    fun acceptingTheInboxReaskTurnsCampaignsOn() {
        MockApp.launch(LaunchArg.CONSENT_REASK).use {
            composeRule.openInboxFromHome()
            composeRule.waitForTag("notifications.consent")

            composeRule.onNodeWithTag("notifications.consent.accept").performClick()
            composeRule.waitForTagGone("notifications.consent")
            composeRule.tapBack()
            composeRule.openNotificationSettingsFromHome()

            composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOn()
        }
    }

    // NIT-01: the inbox opened by a tapped push keeps the highlighted notification in view; no re-ask on top of it.
    @Test
    fun inboxOpenedByAPushDoesNotReaskTheConsent() {
        MockApp.launch(LaunchArg.CONSENT_REASK, openNotification = "p-paywall").use {
            composeRule.waitForTag("inbox.list")

            composeRule.assertNeverAppears("notifications.consent")
            composeRule.onNodeWithTag("inbox.row.p-paywall").assertIsDisplayed()
        }
    }

    // NIT-01: after a "no" 31 days ago, a successful exam submission asks for the permission once more.
    @Test
    fun aSubmittedExamAsksForThePermissionOnceMore() {
        hiltRule.inject()
        TestWorkManager.install(workerFactory)
        val pdf = samplePdf()
        val picker = answerDocumentPicker(pdf)
        try {
            MockApp.launch(LaunchArg.NOTIFICATIONS_REASK).use {
                composeRule.waitForHome()
                composeRule.assertNeverAppears("notifications.priming", forMillis = 1_000)
                composeRule.tapWhenSingle("home.addExam")
                composeRule.waitForTag("addExam.form")

                fillTheFormWithAPdf()
                composeRule.onNodeWithTag("addExam.submit").performClick()

                // The form closes once the upload is stored; then the re-ask ("Onaylanınca haber verelim mi?").
                composeRule.waitForTagGone("addExam.form", timeoutMillis = SUBMIT_TIMEOUT_MS)
                composeRule.waitForTag("notifications.priming")
                composeRule.onNodeWithTag("notifications.priming")
                    .assert(hasAnyDescendant(hasText(string(R.string.notification_prompt_priming_reask_title))))
                composeRule.onNodeWithTag("notifications.priming.enable").assertIsDisplayed()
            }
        } finally {
            removePickerAnswer(picker)
            pdf.delete()
        }
    }

    // Helpers
    private fun assertPermission(@StringRes label: Int) {
        composeRule.onNodeWithTag("notificationSettings.permission").assert(hasText(string(label)))
    }

    private fun reopenSettings() {
        composeRule.tapBack()
        composeRule.waitForTag("account.list")
        composeRule.scrollToAndClick("account.list", "account.notificationSettings")
        composeRule.waitForTag("notificationSettings.list")
    }

    /** The profile fills the school; the lesson is typed in, the rest picked; [answerDocumentPicker] gives the PDF. */
    private fun fillTheFormWithAPdf() {
        tapFormRow("addExam.lesson")
        composeRule.waitForTag("lessonPicker.list")
        composeRule.scrollToAndClick("lessonPicker.list", "lessonPicker.addCustom")
        composeRule.waitForTag("lessonPicker.customField")
        composeRule.onNodeWithTag("lessonPicker.customField").performTextInput("Test Dersi")
        composeRule.onNodeWithTag("lessonPicker.customConfirm").performClick()
        composeRule.waitForTag("addExam.form")

        tapFormRow("addExam.examName")
        composeRule.waitForTag("addExam.examName.list")
        composeRule.scrollToAndClick("addExam.examName.list", "addExam.examName.option.vize")
        composeRule.waitForTag("addExam.form")

        tapFormRow("addExam.kind.PDF")
        tapFormRow("addExam.pickPDF")
        composeRule.waitForTag("addExam.fileSummary")
        tapFormRow("addExam.termsCheckbox")
        composeRule.waitForIdle()
    }

    private fun tapFormRow(tag: String) {
        composeRule.onNodeWithTag("addExam.form").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun samplePdf(): File {
        val file = File(MockApp.context.cacheDir, "nit03-sample-exam.pdf")
        MockApp.context.assets.open("samples/sample-exam.pdf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }

    private fun string(@StringRes id: Int) = MockApp.context.getString(id)

    private companion object {
        const val SUBMIT_TIMEOUT_MS = 30_000L
    }
}
