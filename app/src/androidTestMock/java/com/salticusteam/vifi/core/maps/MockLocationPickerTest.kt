package com.salticusteam.vifi.core.maps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** The mock flavor's picker (iOS `MockLocationPicker`): a fixed place, picked or backed out of, with no map. */
@RunWith(AndroidJUnit4::class)
class MockLocationPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picks = mutableListOf<NotificationLocation>()
    private var cancels = 0

    private fun show(initial: NotificationLocation?) {
        composeRule.setContent {
            ViFiTheme {
                MockLocationPicker().Content(initial = initial, onPick = { picks += it }, onCancel = { cancels++ })
            }
        }
    }

    @Test
    fun aNewPlaceIsTheSampleCafe() {
        show(initial = null)

        composeRule.onNodeWithText("Konum Seç").assertIsDisplayed()
        composeRule.onNodeWithText("Örnek harita").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.mock.name").assertIsDisplayed()
        composeRule.onNodeWithText("ViFi Öğrenci Kafesi").assertIsDisplayed()
        composeRule.onNodeWithText("Caferağa, Kadıköy, İstanbul").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.confirm").performClick()

        assertEquals(
            listOf(NotificationLocation(40.9903, 29.0290, "ViFi Öğrenci Kafesi", "Caferağa, Kadıköy, İstanbul", null)),
            picks,
        )
    }

    @Test
    fun changingAPlaceOffersThatPlace() {
        val kafe = NotificationLocation(38.5, 27.1, "Kafe", address = null, placeId = "p1")
        show(initial = kafe)

        composeRule.onNodeWithText("Kafe").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.confirm").performClick()

        assertEquals(listOf(kafe), picks)
    }

    @Test
    fun cancelingBacksOutWithoutAPlace() {
        show(initial = null)

        composeRule.onNodeWithContentDescription("İptal").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.cancel").performClick()

        assertEquals(1, cancels)
        assertEquals(emptyList<NotificationLocation>(), picks)
    }
}
