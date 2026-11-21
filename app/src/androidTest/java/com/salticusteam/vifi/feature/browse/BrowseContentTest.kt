package com.salticusteam.vifi.feature.browse

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    // iOS StructuredExamUITests.testStructuredExamRowShowsItsQuestionCount (4.2.0, SQS-06).
    @Test
    fun structuredExamRow_showsItsQuestionCountAndBadge_plainRowKeepsItsPages() {
        val structured = ArchiveItem(
            lesson.appending("f", "2003 FİNAL"), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 3, questionCount = 3),
        )
        val images = ArchiveItem(lesson.appending("v2", "2002 VİZE"), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 2))
        show(BrowseUiState(path = lesson, isLoading = false, items = listOf(structured, images)))

        inRow("row.2003 FİNAL", hasText("3 soru")).assertCountEquals(1)
        inRow("row.2003 FİNAL", hasText("Sorular")).assertCountEquals(1)
        inRow("row.2002 VİZE", hasText("2 sayfa")).assertCountEquals(1)
        inRow("row.2002 VİZE", hasText("soru", substring = true)).assertCountEquals(0)
    }

    @Test
    fun structuredExamRow_whenLocked_keepsTheLockBadge() {
        // The newest exam is locked; the two oldest are free (ExamLock), structured or not.
        val exams = listOf("2005 FİNAL", "2004 FİNAL", "2003 FİNAL").mapIndexed { index, name ->
            ArchiveItem(lesson.appending("e$index", name), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 3, questionCount = 3))
        }
        composeRule.setContent {
            ViFiTheme {
                BrowseContent(
                    state = BrowseUiState(path = lesson, isLoading = false, items = exams),
                    onSearchTextChange = {}, onItemClick = {}, onRetry = {}, onRefresh = {}, onBack = {},
                    lockState = ExamUnlockUiState(),
                )
            }
        }

        val locked = exams.first { it.path in ExamUnlockUiState().lockedPaths(exams) }
        inRow("row.${locked.name}", hasTestTag("row.lock")).assertCountEquals(1)
        inRow("row.${locked.name}", hasText("Sorular")).assertCountEquals(0)
    }

    /** The nodes matching [matcher] inside the row tagged [rowTag]. */
    private fun inRow(rowTag: String, matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher and hasAnyAncestor(hasTestTag(rowTag)), useUnmergedTree = true)

    @Test
    fun loadedList_sectionHeaderShowsLevelTitleAndCount() {
        show(BrowseUiState(path = lesson, isLoading = false, items = listOf(pdf, empty)))

        composeRule.onNodeWithTag("browse.sectionHeader").assertIsDisplayed()
        composeRule.onNodeWithText("Sınavlar").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
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
