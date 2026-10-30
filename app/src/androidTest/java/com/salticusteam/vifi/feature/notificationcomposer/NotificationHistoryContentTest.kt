package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationStats
import com.salticusteam.vifi.core.model.NotificationStatus
import com.salticusteam.vifi.core.model.SentNotification
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The sending history (iOS `NotificationHistoryView`): rows, badges, numbers, cancel, and the empty and error states. */
@RunWith(AndroidJUnit4::class)
class NotificationHistoryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val canceled = mutableListOf<String>()

    private val scheduledAt = Instant.ofEpochSecond(1_761_800_000)

    private fun row(
        id: String,
        status: NotificationStatus,
        isTest: Boolean = false,
        isAutomatic: Boolean = false,
        deferred: Boolean = false,
    ) = SentNotification(
        id = id,
        kind = NotificationKind.PROMO,
        title = "Kampüs kafede indirim",
        body = "Mesaj",
        audience = NotificationAudience.All,
        audienceLabel = "Boğaziçi Üniversitesi › Mühendislik Fakültesi · Kadın",
        status = status,
        isTest = isTest,
        isAutomatic = isAutomatic,
        deferredByQuietHours = deferred,
        createdAt = Instant.ofEpochSecond(1_761_700_000),
        scheduledAt = if (status == NotificationStatus.SCHEDULED) scheduledAt else null,
        sentAt = Instant.ofEpochSecond(1_761_717_600),
        stats = NotificationStats(recipients = 18, pushTargets = 21, pushSent = 20, pushFailed = 1, opened = 9, actionTapped = 4),
    )

    private fun show(state: NotificationHistoryUiState) {
        composeRule.setContent {
            ViFiTheme {
                NotificationHistoryContent(state = state, onRefresh = {}, onCancel = { canceled += it.id }, onBack = {})
            }
        }
    }

    private fun tag(name: String) = composeRule.onNodeWithTag("$COMPOSER_TAG.history.$name")

    @Test
    fun aSentRow_showsItsBadgesAudienceAndNumbers() {
        show(NotificationHistoryUiState(notifications = listOf(row("sent", NotificationStatus.SENT, isTest = true, isAutomatic = true))))

        tag("row.sent")
            .assertTextContains("Kampüs kafede indirim")
            .assertTextContains("Boğaziçi Üniversitesi › Mühendislik Fakültesi · Kadın")
            .assertTextContains("Gönderildi")
            .assertTextContains("Kampanya")
            .assertTextContains("Test")
            .assertTextContains("Otomatik")
            .assertTextContains("18 alıcı · 20 push gönderildi · 1 başarısız")
            .assertTextContains("9 kişi açtı · 4 kişi butona dokundu")
        tag("row.sent.menu").assertDoesNotExist()
    }

    @Test
    fun aScheduledRow_saysWhenAndCanBeCanceledWithoutConfirmation() {
        show(NotificationHistoryUiState(notifications = listOf(row("later", NotificationStatus.SCHEDULED, deferred = true))))

        tag("row.later")
            .assertTextContains("Zamanlandı")
            .assertTextContains("${scheduledAt.toComposerDateTime()} için zamanlandı (sessiz saat)")
        composeRule.onNode(hasText("alıcı ·", substring = true)).assertDoesNotExist()
        tag("row.later.menu").performClick()
        tag("row.later.cancel").performClick()

        assertEquals(listOf("later"), canceled)
    }

    @Test
    fun whileACancellationIsInFlight_noOtherCanStart() {
        show(
            NotificationHistoryUiState(
                notifications = listOf(row("a", NotificationStatus.SCHEDULED), row("b", NotificationStatus.SCHEDULED)),
                cancelingId = "a",
            ),
        )

        tag("row.b.menu").performClick()
        tag("row.b.cancel").assertIsNotEnabled()
    }

    @Test
    fun aFailedRefresh_keepsTheRowsAndSaysWhy() {
        show(
            NotificationHistoryUiState(
                notifications = listOf(row("sent", NotificationStatus.SENT)),
                errorMessage = "İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.",
            ),
        )

        tag("error").assertTextContains("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")
        tag("row.sent").assertIsDisplayed()
    }

    @Test
    fun anEmptyHistory_explainsWhatWillAppear() {
        show(NotificationHistoryUiState(notifications = emptyList()))

        composeRule.onNode(hasText("Henüz bildirim yok")).assertIsDisplayed()
        composeRule.onNode(hasText("Gönderdiğin ve zamanladığın bildirimler burada görünür.")).assertIsDisplayed()
    }

    @Test
    fun aFailedFirstLoad_offersARetry() {
        show(NotificationHistoryUiState(loadError = "Bu işlem için yetkin yok."))

        composeRule.onNode(hasText("Bu işlem için yetkin yok.")).assertIsDisplayed()
        composeRule.onNodeWithTag("state.retry").assertIsDisplayed()
    }

    @Test
    fun theFirstLoad_saysItIsLoading() {
        show(NotificationHistoryUiState())

        composeRule.onNode(hasText("Geçmiş yükleniyor…")).assertIsDisplayed()
    }
}
