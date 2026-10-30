package com.salticusteam.vifi.feature.inbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationLocation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Konuma Git" (iOS `LocationCampaignUITests`): the place, then Google Maps, another maps app or "Vazgeç". */
@RunWith(AndroidJUnit4::class)
class LocationChoiceContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picks = mutableListOf<String>()

    private fun show(location: NotificationLocation) {
        composeRule.setContent {
            ViFiTheme {
                LocationChoiceContent(
                    location = location,
                    onGoogleMaps = { picks += "google" },
                    onOtherMapsApp = { picks += "other" },
                    onCancel = { picks += "cancel" },
                )
            }
        }
    }

    @Test
    fun theSheetNamesThePlaceAndOffersBothMapsChoicesAndCancel() {
        show(NotificationLocation(40.9903, 29.0290, "ViFi Öğrenci Kafesi", "Caferağa, Kadıköy, İstanbul"))

        composeRule.onNodeWithText("Konumu hangi uygulamada açmak istersin?").assertIsDisplayed()
        composeRule.onNodeWithText("ViFi Öğrenci Kafesi\nCaferağa, Kadıköy, İstanbul").assertIsDisplayed()
        composeRule.onNodeWithTag("inbox.location.google").performClick()
        composeRule.onNodeWithTag("inbox.location.other").performClick()
        composeRule.onNodeWithTag("inbox.location.cancel").performClick()

        assertEquals(listOf("google", "other", "cancel"), picks)
    }

    @Test
    fun aPlaceWithoutAnAddressShowsOnlyItsName() {
        show(NotificationLocation(40.9903, 29.0290, "ViFi Öğrenci Kafesi", address = "  "))

        composeRule.onNodeWithText("ViFi Öğrenci Kafesi").assertIsDisplayed()
        composeRule.onNodeWithText("Google Haritalar").assertIsDisplayed()
        composeRule.onNodeWithText("Başka bir harita uygulaması").assertIsDisplayed()
        composeRule.onNodeWithText("Vazgeç").assertIsDisplayed()
    }
}
