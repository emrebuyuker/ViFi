package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationLocation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The picker's screen without the Google map (iOS `LocationPickerView` + panels): search, details, the result. */
@RunWith(AndroidJUnit4::class)
class LocationPickerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val empty = LocationPickerUiState(
        initialCamera = LocationPickerViewModel.DEFAULT_CAMERA,
        latitude = LocationPickerViewModel.DEFAULT_CAMERA.latitude,
        longitude = LocationPickerViewModel.DEFAULT_CAMERA.longitude,
    )
    private val suggestion = PlaceSuggestion(id = "place-1", title = "Kadıköy", subtitle = "İstanbul")
    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(empty)

    private fun show(initial: LocationPickerUiState) {
        state = initial
        composeRule.setContent {
            ViFiTheme {
                LocationPickerContent(
                    state = state,
                    onQueryChange = { calls += "query:$it" },
                    onSuggestionSelected = { calls += "suggestion:${it.id}" },
                    onNameChange = { calls += "name:$it" },
                    onAddressChange = { calls += "address:$it" },
                    onConfirm = { calls += "confirm" },
                    onCancel = { calls += "cancel" },
                    map = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
                )
            }
        }
    }

    @Test
    fun anEmptyPickerShowsTheMapThePinCoordinatesAndNothingToConfirm() {
        show(empty)

        composeRule.onNodeWithText("Konum Seç").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.map")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Harita. Konum, haritanın ortasındaki iğnenin gösterdiği yerdir.")
        composeRule.onNodeWithTag("locationPicker.coordinates")
            .assertContentDescriptionEquals("Koordinat: 39.00000, 35.20000")
        composeRule.onNodeWithText("39.00000, 35.20000").assertIsDisplayed()
        composeRule.onNodeWithText("Yer adı (zorunlu)").assertIsDisplayed()
        composeRule.onNodeWithText("Adres (isteğe bağlı)").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("locationPicker.locationDenied").assertDoesNotExist()
        composeRule.onNodeWithTag("locationPicker.search.clear").assertDoesNotExist()
    }

    @Test
    fun typingSearchesAndTheClearButtonEmptiesTheBox() {
        show(empty)

        composeRule.onNodeWithTag("locationPicker.search").performTextInput("Kad")
        assertEquals(listOf("query:Kad"), calls)

        state = state.copy(query = "Kad")
        composeRule.onNodeWithContentDescription("Aramayı temizle").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.search.clear").performClick()
        assertEquals("query:", calls.last())
    }

    @Test
    fun theSearchPanelShowsEachStateAndAChosenResultIsReported() {
        show(empty.copy(query = "Kadıköy", searchState = PlaceSearchState.Searching))
        composeRule.onNodeWithText("Aranıyor…").assertIsDisplayed()

        state = state.copy(searchState = PlaceSearchState.NoResults)
        composeRule.onNodeWithText("Sonuç bulunamadı.").assertIsDisplayed()

        state = state.copy(searchState = PlaceSearchState.Failed(PlaceSearchError.Unavailable.userMessage))
        composeRule.onNodeWithText(PlaceSearchError.Unavailable.userMessage).assertIsDisplayed()

        state = state.copy(searchState = PlaceSearchState.Results(listOf(suggestion)))
        composeRule.onNodeWithText("İstanbul").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.suggestion.place-1").performClick()
        assertEquals("suggestion:place-1", calls.last())

        // A result that could not be resolved is said in place of the list.
        state = state.copy(resolveError = PlaceSearchError.NotFound.userMessage)
        composeRule.onNodeWithText(PlaceSearchError.NotFound.userMessage).assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.suggestion.place-1").assertDoesNotExist()
    }

    @Test
    fun aNamedPlaceCanBeConfirmedAndTheCloseButtonCancels() {
        show(empty.copy(latitude = 40.9903, longitude = 29.029, name = "Kadıköy", placeId = "place-1"))

        composeRule.onNodeWithText("40.99030, 29.02900").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.confirm").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("İptal").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.cancel").performClick()

        assertEquals(listOf("confirm", "cancel"), calls)
    }

    @Test
    fun editingTheDetailsIsReportedAndOverlongTextIsFlagged() {
        show(empty)

        composeRule.onNodeWithTag("locationPicker.name").performTextInput("Kafe")
        composeRule.onNodeWithTag("locationPicker.address").performTextInput("Bornova")
        assertEquals(listOf("name:Kafe", "address:Bornova"), calls)

        state = state.copy(
            name = "a".repeat(NotificationLocation.NAME_LIMIT + 1),
            address = "b".repeat(NotificationLocation.ADDRESS_LIMIT + 1),
        )
        composeRule.onNodeWithText("Ad en fazla 120 karakter olabilir.").assertIsDisplayed()
        composeRule.onNodeWithText("Adres en fazla 200 karakter olabilir.").assertIsDisplayed()
        composeRule.onNodeWithTag("locationPicker.confirm").assertIsNotEnabled()
    }

    @Test
    fun aTappedPlacesAddressShowsAsLoadingAndARefusedPermissionIsNotedQuietly() {
        show(
            empty.copy(
                name = "A Pastanesi",
                isLoadingAddress = true,
                locationAuthorization = LocationAuthorization.DENIED,
            ),
        )

        composeRule.onNodeWithTag("locationPicker.addressLoading", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Adres alınıyor", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("locationPicker.locationDenied").assertIsDisplayed()
        composeRule.onNodeWithText("Konum izni kapalı; harita bulunduğun yere odaklanmaz.").assertIsDisplayed()
        // The address is optional: the place can be confirmed before it arrives.
        composeRule.onNodeWithTag("locationPicker.confirm").assertIsEnabled()
    }
}
