package com.salticusteam.vifi.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.salticusteam.vifi.core.data.catalog.UniversityCatalog
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileFormContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ege = CatalogItem(id = "ege-universitesi", name = "Ege Üniversitesi")
    private val catalog = UniversityCatalog(listOf(ege), emptyList(), emptyList())

    private fun setContent(
        state: ProfileFormUiState,
        onSave: () -> Unit = {},
        onPickerOpen: (CatalogField) -> Unit = {},
        onCatalogSelected: (CatalogSelection) -> Unit = {},
        onSignOut: (() -> Unit)? = {},
    ) {
        composeRule.setContent {
            ViFiTheme {
                ProfileFormContent(
                    state = state,
                    title = "Profilini Tamamla",
                    onNameChange = {},
                    onSurnameChange = {},
                    onPickerOpen = onPickerOpen,
                    onPickerDismiss = {},
                    onCatalogSelected = onCatalogSelected,
                    onGenderSelected = {},
                    onSave = onSave,
                    testTagPrefix = "profile",
                    onSignOut = onSignOut,
                )
            }
        }
    }

    @Test
    fun profileForm_whenIncomplete_disablesSave() {
        setContent(ProfileFormUiState(phoneNumber = "+905321234567"))

        composeRule.onNodeWithTag("profile.save").assertIsNotEnabled()
        composeRule.onNodeWithTag("profile.signOut").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("profile.phone").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileForm_whenComplete_savesOnTap() {
        var saved = false
        setContent(
            ProfileFormUiState.initial(phoneNumber = null, existing = FakeProfileService.SAMPLE_PROFILE),
            onSave = { saved = true },
        )

        composeRule.onNodeWithTag("profile.save").assertIsEnabled().performClick()

        assertTrue(saved)
    }

    @Test
    fun profileForm_whenSaving_showsProgress() {
        setContent(
            ProfileFormUiState.initial(phoneNumber = null, existing = FakeProfileService.SAMPLE_PROFILE)
                .copy(isWorking = true),
        )

        composeRule.onNodeWithTag("profile.save.progress").assertIsDisplayed()
    }

    @Test
    fun profileForm_whenErrorSet_showsIt() {
        setContent(ProfileFormUiState(error = "İnternet bağlantısı yok."))

        composeRule.onNodeWithTag("profile.error").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileForm_whenUniversityRowTapped_requestsItsPicker() {
        var opened: CatalogField? = null
        setContent(ProfileFormUiState(), onPickerOpen = { opened = it })

        composeRule.onNodeWithTag("profile.university").performClick()

        assertEquals(CatalogField.UNIVERSITY, opened)
    }

    @Test
    fun picker_whenOptionTapped_returnsTheCatalogValue() {
        var chosen: CatalogSelection? = null
        setContent(
            ProfileFormUiState(catalog = catalog, picker = CatalogField.UNIVERSITY),
            onCatalogSelected = { chosen = it },
        )

        composeRule.onNodeWithTag("profile.university.option.ege-universitesi").performClick()

        assertEquals(CatalogSelection.catalog(ege), chosen)
    }

    @Test
    fun picker_whenTypedTextIsNotListed_offersItAsCustom() {
        var chosen: CatalogSelection? = null
        setContent(
            ProfileFormUiState(catalog = catalog, picker = CatalogField.UNIVERSITY),
            onCatalogSelected = { chosen = it },
        )

        composeRule.onNodeWithTag("profile.university.search").performTextInput("Yeni Üniversite")
        composeRule.onNodeWithTag("profile.university.custom").performClick()

        assertEquals(CatalogSelection.custom("Yeni Üniversite"), chosen)
    }

    @Test
    fun editForm_hasNoSignOut() {
        composeRule.setContent {
            ViFiTheme {
                ProfileFormContent(
                    state = ProfileFormUiState(),
                    title = "Profili Düzenle",
                    onNameChange = {},
                    onSurnameChange = {},
                    onPickerOpen = {},
                    onPickerDismiss = {},
                    onCatalogSelected = {},
                    onGenderSelected = {},
                    onSave = {},
                    testTagPrefix = "editProfile",
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("editProfile.save").assertIsDisplayed()
        composeRule.onNodeWithTag("topBar.backButton").assertIsDisplayed()
        composeRule.onNodeWithTag("profile.signOut").assertDoesNotExist()
    }
}
