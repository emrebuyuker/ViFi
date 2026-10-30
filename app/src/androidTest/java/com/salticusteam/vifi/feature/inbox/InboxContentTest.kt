package com.salticusteam.vifi.feature.inbox

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.InboxSource
import com.salticusteam.vifi.core.model.NotificationAction
import com.salticusteam.vifi.core.model.NotificationKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The inbox's cards and states (iOS `InboxView`): texts, the unread dot, actions and the partial failure strip. */
@RunWith(AndroidJUnit4::class)
class InboxContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tapped = mutableListOf<String>()
    private var retries = 0

    private fun item(id: String, kind: NotificationKind = NotificationKind.GENERAL) = InboxItem(
        id, InboxSource.PERSONAL, kind, "Başlık $id", "Metin $id", createdAt = Instant.parse("2026-10-30T09:00:00Z"),
    )

    private fun show(state: InboxUiState) {
        composeRule.setContent {
            ViFiTheme {
                InboxContent(
                    state = state,
                    imageLoader = null,
                    onAction = { tapped += it.item.id },
                    onRetry = { retries++ },
                    onOpenSettings = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun cards_showTheirTextsTheCampaignLabelTheUnreadDotAndTheButton() {
        show(
            InboxUiState(
                isLoading = false,
                items = listOf(
                    InboxEntry(item("a1"), isUnread = true),
                    InboxEntry(item("p1", NotificationKind.PROMO), isUnread = false, action = NotificationAction.Paywall),
                ),
            ),
        )

        composeRule.onNodeWithText("Başlık a1").assertIsDisplayed()
        composeRule.onNodeWithTag("inbox.unread.a1").assertContentDescriptionEquals("Okunmadı")
        composeRule.onNodeWithTag("inbox.unread.p1").assertDoesNotExist()
        composeRule.onNodeWithText("Kampanya").assertIsDisplayed()
        composeRule.onNodeWithTag("inbox.row.p1.action").assertTextContains("Premium'a Göz At")
        composeRule.onNodeWithTag("inbox.row.a1.action").assertDoesNotExist()
        // iOS's abbreviated month, on both cards.
        composeRule.onAllNodesWithText("30 Eki 2026", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun aCardWithAButtonRunsItsActionFromTheButtonAndTheCard_aCardWithoutOneIsInert() {
        show(
            InboxUiState(
                isLoading = false,
                items = listOf(
                    InboxEntry(item("a1"), isUnread = false),
                    InboxEntry(item("p1"), isUnread = false, action = NotificationAction.AddExam),
                ),
            ),
        )

        composeRule.onNodeWithTag("inbox.row.p1.action").assertTextContains("Sınav Ekle").performClick()
        composeRule.onNodeWithText("Başlık p1").performClick()
        composeRule.onNodeWithText("Başlık a1").performClick()

        assertEquals(listOf("p1", "p1"), tapped)
    }

    @Test
    fun everyActionHasItsOwnButtonLabel() {
        show(
            InboxUiState(
                isLoading = false,
                items = listOf(
                    InboxEntry(item("u"), false, NotificationAction.Url("https://vifi.app")),
                    InboxEntry(item("a"), false, NotificationAction.AddExam),
                    InboxEntry(item("p"), false, NotificationAction.Paywall),
                ),
            ),
        )

        composeRule.onNodeWithTag("inbox.row.u.action").assertTextContains("Bağlantıyı Aç")
        composeRule.onNodeWithTag("inbox.row.a.action").assertTextContains("Sınav Ekle")
        composeRule.onNodeWithTag("inbox.row.p.action").assertTextContains("Premium'a Göz At")
    }

    @Test
    fun aPartialFailureKeepsTheListAndOffersARetry() {
        show(InboxUiState(isLoading = false, hasPartialFailure = true, items = listOf(InboxEntry(item("a1"), false))))

        composeRule.onNodeWithTag("inbox.partialFailure").assertIsDisplayed()
        composeRule.onNodeWithText("Bazı bildirimler yüklenemedi.").assertIsDisplayed()
        composeRule.onNodeWithTag("inbox.retry").performClick()
        composeRule.onNodeWithTag("inbox.row.a1").assertIsDisplayed()

        assertEquals(1, retries)
    }

    @Test
    fun anEmptyInboxSaysSo() {
        show(InboxUiState(isLoading = false))

        composeRule.onNodeWithText("Bildirim yok").assertIsDisplayed()
        composeRule.onNodeWithText("Yeni bildirimler burada görünecek.").assertIsDisplayed()
    }

    @Test
    fun theTappedPushsCardIsSelected() {
        show(
            InboxUiState(
                isLoading = false,
                highlightedId = "p1",
                items = listOf(InboxEntry(item("a1"), false), InboxEntry(item("p1"), false)),
            ),
        )

        composeRule.onNodeWithTag("inbox.row.p1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithTag("inbox.row.a1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        assertTrue(retries == 0)
    }
}
