package com.salticusteam.vifi.feature.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val lesson = ArchivePath(listOf("itu", "muh", "blg", "blg101"), listOf("İTÜ", "MÜH", "BLG", "BLG 101"))
    private val pdf = ArchiveItem(lesson.appending("v", "2023 Vize"), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1))
    private val empty = ArchiveItem(lesson.appending("e", "2021 Bütünleme"), ArchiveLevel.EXAM, null, null)

    private fun show(state: BrowseUiState, onItemClick: (ArchiveItem) -> Unit = {}, onRetry: () -> Unit = {},
                     onSearch: (String) -> Unit = {}) {
        composeRule.setContent {
            ViFiTheme {
                BrowseContent(state, onSearch, onItemClick, onRetry, onRefresh = {}, onBack = {})
            }
        }
    }

    @Test
    fun loadedList_clickOpensItem_andFilelessExamIsDisabled() {
        var clicked: ArchiveItem? = null
        show(BrowseUiState(path = lesson, isLoading = false, items = listOf(pdf, empty)), onItemClick = { clicked = it })

        composeRule.onNodeWithTag("browse.list").assertIsDisplayed()
        composeRule.onNodeWithTag("row.2023 Vize").performClick()
        composeRule.onNodeWithTag("row.2021 Bütünleme").assertIsNotEnabled()

        assertEquals(pdf, clicked)
    }

    @Test
    fun error_showsMessageAndRetries() {
        var retries = 0
        show(BrowseUiState(isLoading = false, error = ArchiveError.Offline.userMessage), onRetry = { retries++ })

        composeRule.onNodeWithTag("state.retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun emptyAndLoadingStates_areShown() {
        show(BrowseUiState(path = lesson, isLoading = false, items = emptyList()))
        composeRule.onNodeWithTag("state.empty").assertIsDisplayed()
    }

    @Test
    fun searchField_forwardsText() {
        var typed = ""
        show(BrowseUiState(path = lesson, isLoading = false, items = listOf(pdf)), onSearch = { typed = it })

        composeRule.onNodeWithTag("browse.search").performTextInput("vize")

        assertEquals("vize", typed)
    }
}
