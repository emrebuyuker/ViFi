package com.salticusteam.vifi.feature.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.core.model.ExamUnlockError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamUnlockContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val lesson = ArchivePath(listOf("itu", "muh", "blg", "blg101"), listOf("İTÜ", "MÜH", "BLG", "BLG 101"))
    private fun exam(id: String, name: String) =
        ArchiveItem(lesson.appending(id, name), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1))

    // Newest first: only "2024 Final" is locked.
    private val exams = listOf(exam("f24", "2024 Final"), exam("v23", "2023 Vize"), exam("f22", "2022 Final"))

    @Test
    fun lockedRow_tapOffersUnlock_freeRowOpens_badgeShowsBalance() {
        var opened: ArchiveItem? = null
        var lockedTap: ArchiveItem? = null
        composeRule.setContent {
            ViFiTheme {
                BrowseContent(
                    state = BrowseUiState(path = lesson, isLoading = false, items = exams),
                    onSearchTextChange = {}, onItemClick = { opened = it }, onRetry = {}, onRefresh = {}, onBack = {},
                    lockState = ExamUnlockUiState(balance = 120),
                    onLockedExamTap = { lockedTap = it },
                )
            }
        }

        composeRule.onNodeWithTag("coin.balance").assertIsDisplayed()
        composeRule.onNodeWithTag("row.lock", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("row.2024 Final").performClick()
        assertEquals(exams[0], lockedTap)
        assertNull(opened)

        composeRule.onNodeWithTag("row.2022 Final").performClick()
        assertEquals(exams[2], opened)
    }

    @Test
    fun sheet_whenAffordable_confirmsUnlock() {
        var unlocks = 0
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent("2024 Final", ExamUnlockUiState(balance = 120), { unlocks++ }, null, {})
            }
        }

        composeRule.onNodeWithTag("exam.unlock.confirm").performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun sheet_whenUnlocking_disablesConfirmAndClose() {
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent("2024 Final", ExamUnlockUiState(balance = 120, isUnlocking = true), {}, null, {})
            }
        }

        composeRule.onNodeWithTag("exam.unlock.confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("exam.unlock.close").assertIsNotEnabled()
    }

    @Test
    fun sheet_whenBalanceLow_offersEarnAndShowsError() {
        var earn = 0
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent(
                    "2024 Final",
                    ExamUnlockUiState(balance = 20, error = ExamUnlockError.InsufficientCoins.userMessage),
                    {}, { earn++ }, {},
                )
            }
        }

        composeRule.onNodeWithTag("exam.unlock.error").assertIsDisplayed()
        composeRule.onNodeWithTag("exam.unlock.earn").performClick()
        assertEquals(1, earn)
    }
}
