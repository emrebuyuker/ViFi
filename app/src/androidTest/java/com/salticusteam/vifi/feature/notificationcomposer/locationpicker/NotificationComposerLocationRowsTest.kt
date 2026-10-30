package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The composer's location rows (iOS `LocationCampaignUITests`, composer part, without the composer): the summary, "Konum
 * seç" / "Değiştir" opening the flavor's picker full screen, and the campaign note.
 */
@RunWith(AndroidJUnit4::class)
class NotificationComposerLocationRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val cafe = NotificationLocation(40.9903, 29.0290, "ViFi Öğrenci Kafesi", "Caferağa, Kadıköy, İstanbul")
    private var location by mutableStateOf<NotificationLocation?>(null)
    private var enabled by mutableStateOf(true)
    private var pickerInitial: NotificationLocation? = cafe

    /** Offers [cafe] like the mock picker and remembers which place it was opened on. */
    private val picker = object : LocationPicker {
        @Composable
        override fun Content(
            initial: NotificationLocation?,
            onPick: (NotificationLocation) -> Unit,
            onCancel: () -> Unit,
        ) {
            pickerInitial = initial
            Column {
                Button(onClick = { onPick(cafe) }, modifier = Modifier.testTag("locationPicker.confirm")) {
                    Text("Bu konumu seç")
                }
                Button(onClick = onCancel, modifier = Modifier.testTag("locationPicker.cancel")) {
                    Text("İptal")
                }
            }
        }
    }

    private fun show(initial: NotificationLocation?) {
        location = initial
        composeRule.setContent {
            ViFiTheme {
                CompositionLocalProvider(LocalLocationPicker provides picker) {
                    NotificationComposerLocationRows(
                        location = location,
                        enabled = enabled,
                        onLocationPicked = { location = it },
                    )
                }
            }
        }
    }

    @Test
    fun withoutAPlaceTheRowsOfferToPickOneAndSayItIsACampaign() {
        show(initial = null)

        composeRule.onNodeWithTag("notificationComposer.location.summary").assertDoesNotExist()
        composeRule.onNodeWithText("Konum seç").assertIsDisplayed()
        composeRule.onNodeWithTag("notificationComposer.location.note").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Konumlu bildirimler her zaman Kampanya olarak gider ve yalnızca kampanya bildirimlerini açmış " +
                "kullanıcılara ulaşır.",
        ).assertIsDisplayed()
    }

    @Test
    fun pickingAPlaceFromThePickerShowsItsSummary() {
        show(initial = null)

        composeRule.onNodeWithTag("notificationComposer.location.pick").performClick()
        composeRule.waitForIdle()
        assertNull("A new place starts from nothing", pickerInitial)
        composeRule.onNodeWithTag("locationPicker.confirm").performClick()

        composeRule.onNodeWithTag("locationPicker.confirm").assertDoesNotExist()
        assertEquals(cafe, location)
        composeRule.onNodeWithTag("notificationComposer.location.summary")
            .assertContentDescriptionEquals("Seçilen konum: ViFi Öğrenci Kafesi, Caferağa, Kadıköy, İstanbul")
        composeRule.onNodeWithText("Değiştir").assertIsDisplayed()
    }

    @Test
    fun cancelingThePickerLeavesNoPlace() {
        show(initial = null)

        composeRule.onNodeWithTag("notificationComposer.location.pick").performClick()
        composeRule.onNodeWithTag("locationPicker.cancel").performClick()

        composeRule.onNodeWithTag("locationPicker.cancel").assertDoesNotExist()
        composeRule.onNodeWithTag("notificationComposer.location.pick").assertIsDisplayed()
        composeRule.onNodeWithTag("notificationComposer.location.summary").assertDoesNotExist()
        assertNull(location)
    }

    @Test
    fun changingAPlaceOpensThePickerOnItAndAPlaceWithoutAnAddressIsSaidByName() {
        val noAddress = cafe.copy(address = null)
        show(initial = noAddress)

        composeRule.onNodeWithTag("notificationComposer.location.summary")
            .assertContentDescriptionEquals("Seçilen konum: ViFi Öğrenci Kafesi")
        composeRule.onNodeWithText("Değiştir").performClick()
        composeRule.waitForIdle()

        assertEquals(noAddress, pickerInitial)
    }

    @Test
    fun whileSendingThePlaceCannotBeChanged() {
        show(initial = cafe)
        enabled = false

        composeRule.onNodeWithTag("notificationComposer.location.pick").assertIsNotEnabled()
        composeRule.onNodeWithTag("notificationComposer.location.summary").assertIsDisplayed()
    }
}
