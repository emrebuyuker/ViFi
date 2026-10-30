package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.data.notifications.admin.EncodedNotificationImage
import com.salticusteam.vifi.core.data.notifications.admin.NotificationImageEncoder
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationAdminService
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationPreview
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The composer form (iOS `NotificationComposerUITests`, the parts inside the composer): the real view model over the
 * in-memory admin service, whose sample audience gives iOS's numbers (64 → 40 → 28 → 17, Sakarya 24, Kadın 9).
 */
@RunWith(AndroidJUnit4::class)
class NotificationComposerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val service = FakeNotificationAdminService()

    private val encoder = object : NotificationImageEncoder {
        override suspend fun encode(uri: String) = EncodedNotificationImage.Unusable
    }

    private fun showComposer(): NotificationComposerViewModel {
        val viewModel = NotificationComposerViewModel(service, encoder, FakeAnalyticsTracker(), Clock.systemUTC())
        composeRule.setContent {
            ViFiTheme {
                NotificationComposerScreen(onBack = {}, onOpenHistory = {}, viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private fun tag(name: String) = composeRule.onNodeWithTag("$COMPOSER_TAG.$name")

    private fun pick(dropdown: String, option: String) {
        tag(dropdown).performScrollTo().performClick()
        composeRule.onNodeWithTag(option).performClick()
        composeRule.waitForIdle()
    }

    private fun fillTitleAndBody() {
        tag("title").performScrollTo().performTextInput("Yeni sınavlar")
        tag("body").performScrollTo().performTextInput("Bölümüne 2024 finalleri eklendi.")
    }

    @Test
    fun untouchedForm_isBlockedButDoesNotScold() {
        showComposer()

        tag("send").performScrollTo().assertIsNotEnabled()
        tag("testSend").assertIsNotEnabled()
        tag("issue").assertDoesNotExist()
        tag("estimate").performScrollTo().assertTextContains("≈ 64 kişi")
    }

    @Test
    fun segmentCascade_narrowsTheEstimate() {
        showComposer()

        tag("estimate").performScrollTo().assertTextContains("≈ 64 kişi")
        tag("audience.segment").performScrollTo().performClick()
        tag("estimate").assertDoesNotExist()

        pick("university", "$COMPOSER_TAG.university.option.bogazici-universitesi")
        tag("estimate").performScrollTo().assertTextContains("≈ 40 kişi")
        pick("faculty", "$COMPOSER_TAG.faculty.option.muhendislik-fakultesi")
        tag("estimate").performScrollTo().assertTextContains("≈ 28 kişi")
        pick("department", "$COMPOSER_TAG.department.option.bilgisayar-muhendisligi")
        tag("estimate").performScrollTo().assertTextContains("≈ 17 kişi")

        // A new university resets the faculty and the department below it.
        pick("university", "$COMPOSER_TAG.university.option.sakarya-universitesi")
        tag("estimate").performScrollTo().assertTextContains("≈ 24 kişi")
        tag("faculty.field").assertTextContains("Tüm fakülteler")
        tag("department.field").assertIsNotEnabled()

        tag("gender.female").performScrollTo().performClick()
        tag("estimate").performScrollTo().assertTextContains("≈ 9 kişi")
    }

    @Test
    fun adminSendsAnAnnouncementAfterConfirming() {
        showComposer()
        fillTitleAndBody()

        tag("send").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("64 kişiye gönderilecek", substring = true)).assertIsDisplayed()
        tag("confirm").performClick()
        composeRule.waitForIdle()

        tag("success").performScrollTo().assertTextContains("Bildirim gönderildi. Alıcı sayısı: 64.")
        assertEquals(1, service.sentDrafts.size)
    }

    @Test
    fun decliningTheConfirmation_sendsNothing() {
        showComposer()
        fillTitleAndBody()

        tag("send").performScrollTo().performClick()
        composeRule.waitForIdle()
        tag("confirm.cancel").performClick()
        composeRule.waitForIdle()

        tag("confirm").assertDoesNotExist()
        assertTrue(service.sentDrafts.isEmpty())
        tag("title").performScrollTo().assertTextContains("Yeni sınavlar")
    }

    @Test
    fun konumLocksTheKindToKampanya_andAnotherActionRestoresIt() {
        showComposer()
        fillTitleAndBody()

        pick("action", "$COMPOSER_TAG.action.location")
        tag("kind.lockNote").performScrollTo().assertIsDisplayed()
        tag("kind.general").assertIsNotEnabled()
        // No place picked yet: nothing goes out as if there were no action.
        tag("issue").performScrollTo().assertTextContains("Haritadan geçerli bir konum seç.")
        tag("send").performScrollTo().assertIsNotEnabled()

        pick("action", "$COMPOSER_TAG.action.none")
        tag("kind.lockNote").assertDoesNotExist()
        tag("kind.general").performScrollTo().assertIsEnabled()
        tag("send").performScrollTo().assertIsEnabled()
    }

    @Test
    fun overlongTitle_turnsTheCounterAndShowsWhy() {
        composeRule.setContent {
            ViFiTheme {
                NotificationComposerContent(
                    state = NotificationComposerUiState(title = "a".repeat(NotificationDraft.TITLE_LIMIT + 1), body = "Mesaj"),
                    actions = NotificationComposerActions(),
                    onBack = {},
                    onOpenHistory = {},
                )
            }
        }

        tag("titleCounter").performScrollTo().assertTextContains("81/80")
        tag("issue").performScrollTo().assertTextContains("Başlık en fazla 80 karakter olabilir.")
        tag("send").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun scheduledDraft_asksToScheduleWithTheTime() {
        val at = Instant.now() + Duration.ofHours(2)
        val state = NotificationComposerUiState(title = "Başlık", body = "Mesaj", scheduledAt = at)
        var confirmed = false
        composeRule.setContent {
            ViFiTheme {
                NotificationComposerContent(
                    state = state.copy(
                        isConfirming = true,
                        confirmation = NotificationSendConfirmation(state.draft, NotificationPreview(12, 15)),
                    ),
                    actions = NotificationComposerActions(onConfirm = { confirmed = true }),
                    onBack = {},
                    onOpenHistory = {},
                )
            }
        }

        composeRule.onNode(hasText("Bildirim zamanlansın mı?")).assertIsDisplayed()
        composeRule.onNode(hasText("12 kişiye gönderilecek; 15 cihaza", substring = true)).assertIsDisplayed()
        // The scheduling row behind the dialog shows the time too.
        composeRule.onNode(hasText(at.toComposerDateTime(), substring = true) and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        tag("confirm").assertTextContains("Zamanla").performClick()
        assertTrue(confirmed)
    }
}
