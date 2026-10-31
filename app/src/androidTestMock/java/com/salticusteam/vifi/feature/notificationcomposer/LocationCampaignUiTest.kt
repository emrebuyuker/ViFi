package com.salticusteam.vifi.feature.notificationcomposer

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.maps.MockLocationPicker
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.testing.MockApp
import com.salticusteam.vifi.testing.openComposerFromHome
import com.salticusteam.vifi.testing.openInboxFromHome
import com.salticusteam.vifi.testing.tapWhenSingle
import com.salticusteam.vifi.testing.waitForTag
import com.salticusteam.vifi.testing.waitForTagGone
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * iOS `LocationCampaignUITests` (6) on the mock app, end to end. The inbox half: `p-location`'s "Konuma Git" offers
 * the two maps choices (Android NDEC-03: Google Haritalar and another maps app, instead of Apple's). The composer half
 * (`vifi_admin`, Hesap › Bildirim Gönder): a place from the mock picker makes the notification a campaign.
 */
@HiltAndroidTest
class LocationCampaignUiTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @After
    fun tearDown() {
        MockApp.reset()
    }

    // Inbox

    @Test
    fun konumaGitOffersBothMapsApps() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()
            composeRule.onNodeWithTag("inbox.list").performScrollToKey("p-location")

            composeRule.tapWhenSingle("inbox.row.p-location.action")

            assertTheChoiceIsOffered()
        }
    }

    @Test
    fun tappingTheCardOffersBothMapsApps() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()
            composeRule.onNodeWithTag("inbox.list").performScrollToKey("p-location")

            // The card's text, above its button.
            composeRule.onNodeWithTag("inbox.row.p-location")
                .performTouchInput { click(Offset(centerX, CARD_TEXT_Y.toPx())) }

            assertTheChoiceIsOffered()
        }
    }

    @Test
    fun decliningTheChoiceClosesItAndKeepsTheInbox() {
        MockApp.launch().use {
            composeRule.openInboxFromHome()
            composeRule.onNodeWithTag("inbox.list").performScrollToKey("p-location")
            composeRule.tapWhenSingle("inbox.row.p-location.action")
            composeRule.waitForTag("inbox.location.sheet")

            composeRule.onNodeWithTag("inbox.location.cancel").performClick()

            composeRule.waitForTagGone("inbox.location.sheet")
            composeRule.onNodeWithTag("inbox.list").assertIsDisplayed()
            composeRule.onNodeWithTag("inbox.row.p-location").assertIsDisplayed()
        }
    }

    // Composer

    // Also NIT-01's hand-tried path: Hesap › Bildirim Gönder → Konum → mock picker → Geçmiş.
    @Test
    fun adminAttachesAPlaceAndSendsACampaignAfterConfirming() {
        MockApp.launch(LaunchArg.ADMIN).use {
            composeRule.openComposerFromHome()
            composeRule.fillTitleAndBody(CAMPAIGN)
            composeRule.pickComposerOption("action", "$COMPOSER_TAG.action.location")
            composeRule.composer("send").performScrollTo().assertIsNotEnabled()

            composeRule.composer("location.pick").performScrollTo().performClick()
            composeRule.waitForTag("locationPicker.mock.name")
            composeRule.onNodeWithTag("locationPicker.confirm").performClick()
            composeRule.waitForTagGone("locationPicker.confirm")

            composeRule.composer("location.summary").performScrollTo().assertContentDescriptionEquals(sampleSummary())
            composeRule.composer("send").performScrollTo().assertIsEnabled()
            composeRule.sendAndConfirm()
            composeRule.openHistory()

            composeRule.historyRow(CAMPAIGN)
                .assert(hasText(string(R.string.notification_composer_kind_promo), substring = true))
                .assert(hasText(string(R.string.notification_history_status_sent), substring = true))
        }
    }

    @Test
    fun konumLocksTheKindToKampanyaAndAnotherActionRestoresIt() {
        MockApp.launch(LaunchArg.ADMIN).use {
            composeRule.openComposerFromHome()
            composeRule.fillTitleAndBody(CAMPAIGN)

            composeRule.pickComposerOption("action", "$COMPOSER_TAG.action.location")
            composeRule.composer("kind.lockNote").performScrollTo().assertIsDisplayed()
            composeRule.composer("kind.general").assertIsNotEnabled()

            composeRule.pickComposerOption("action", "$COMPOSER_TAG.action.none")
            composeRule.composer("kind.lockNote").assertDoesNotExist()
            composeRule.composer("kind.general").performScrollTo().assertIsEnabled()
            composeRule.composer("send").performScrollTo().assertIsEnabled()
        }
    }

    @Test
    fun cancelingThePickerLeavesNoPlace() {
        MockApp.launch(LaunchArg.ADMIN).use {
            composeRule.openComposerFromHome()
            composeRule.fillTitleAndBody(CAMPAIGN)
            composeRule.pickComposerOption("action", "$COMPOSER_TAG.action.location")

            composeRule.composer("location.pick").performScrollTo().performClick()
            composeRule.waitForTag("locationPicker.cancel")
            composeRule.onNodeWithTag("locationPicker.cancel").performClick()

            composeRule.waitForTagGone("locationPicker.cancel")
            composeRule.composer("location.pick").performScrollTo().assertIsDisplayed()
            composeRule.composer("location.summary").assertDoesNotExist()
            composeRule.composer("send").performScrollTo().assertIsNotEnabled()
        }
    }

    // Helpers
    private fun assertTheChoiceIsOffered() {
        composeRule.waitForTag("inbox.location.sheet")
        composeRule.onNodeWithTag("inbox.location.google").assertIsDisplayed()
        composeRule.onNodeWithTag("inbox.location.other").assertIsDisplayed()
        composeRule.onNodeWithTag("inbox.location.cancel").assertIsDisplayed()
    }

    /** "Seçilen konum: ViFi Öğrenci Kafesi, Caferağa, Kadıköy, İstanbul", read as one element. */
    private fun sampleSummary(): String {
        val place = MockLocationPicker.SAMPLE_LOCATION
        val named = MockApp.context.getString(R.string.location_picker_composer_place, place.name, place.address)
        return MockApp.context.getString(R.string.location_picker_composer_summary, named)
    }

    private fun string(@StringRes id: Int) = MockApp.context.getString(id)

    private companion object {
        const val CAMPAIGN = "NIT-03 kahve kampanyası"

        /** Inside the card's text row: 16 dp padding, then the title. */
        val CARD_TEXT_Y = 28.dp
    }
}
