package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A build without a Maps key: the picker explains it instead of showing a map, and "Kapat" backs out. */
@RunWith(AndroidJUnit4::class)
class LocationPickerUnavailableTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun withoutAKeyThePickerSaysTheMapIsNotConfiguredAndCloses() {
        var closes = 0
        composeRule.setContent {
            ViFiTheme {
                LocationPickerUnavailable(onClose = { closes++ })
            }
        }

        composeRule.onNodeWithTag("locationPicker.unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Harita anahtarı yapılandırılmamış").assertIsDisplayed()
        composeRule.onNodeWithText("Konum Seç").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Kapat").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.cancel").performClick()

        assertEquals(1, closes)
    }
}
