package com.salticusteam.vifi.feature.moderation

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.salticusteam.vifi.core.data.repository.fake.FakeModerationService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubmitterProfileReader
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.AIProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** iOS `ModerationUITests` (approval buttons, duplicate warning, submitter) on the stateless moderation content. */
class ModerationContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val approvals = mutableListOf<AIProvider>()
    private val sub1 = FakeModerationService.SAMPLES.first { it.id == "sub-1" }
    private val sub2 = FakeModerationService.SAMPLES.first { it.id == "sub-2" }

    private fun setDetail(state: ModerationDetailUiState) {
        composeRule.setContent {
            ViFiTheme {
                ModerationDetailContent(
                    state = state,
                    onPreviewFiles = {},
                    onApprove = { approvals += it },
                    onRejectRequested = {},
                    onRejectReasonChange = {},
                    onConfirmReject = {},
                    onRejectDismissed = {},
                    onApprovalAcknowledged = {},
                    onBack = {},
                )
            }
        }
    }

    // iOS testApprovalOffersBothProviders
    @Test
    fun approvalOffersBothProviders() {
        setDetail(ModerationDetailUiState(record = sub2, submitter = SubmitterState.Missing))

        composeRule.onNodeWithTag("moderation.approve.claude").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("moderation.approve.claude").assert(hasText("Onayla · Claude ile ilerle"))
        composeRule.onNodeWithTag("moderation.approve.gemini").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("moderation.approve.gemini").assert(hasText("Onayla · Gemini ile ilerle"))

        composeRule.onNodeWithTag("moderation.approve.gemini").performClick()
        composeRule.onNodeWithTag("moderation.approve.claude").performClick()

        assertEquals(listOf(AIProvider.GEMINI, AIProvider.CLAUDE), approvals)
    }

    @Test
    fun approvalButtonsAreLockedWhileWorking() {
        setDetail(ModerationDetailUiState(record = sub1, isWorking = true))

        composeRule.onNodeWithTag("moderation.approve.claude").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("moderation.approve.gemini").performScrollTo().assertIsNotEnabled()
    }

    // iOS testDuplicateSubmissionShowsWarningAndBothButtons
    @Test
    fun duplicateSubmissionShowsWarningAndBothButtons() {
        setDetail(
            ModerationDetailUiState(
                record = FakeModerationService.SAMPLES.first { it.id == "sub-dup" },
                duplicateExamId = "mock-existing-exam",
            ),
        )

        composeRule.onNodeWithTag("moderation.duplicateWarning").assertIsDisplayed()
        composeRule.onNodeWithTag("moderation.duplicateWarning").assert(hasText("Bu sınav arşivde zaten yüklü"))
        composeRule.onNodeWithTag("moderation.approve.claude").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("moderation.approve.gemini").performScrollTo().assertIsDisplayed()
    }

    // iOS testNewSubmissionShowsNoDuplicateWarning
    @Test
    fun newSubmissionShowsNoDuplicateWarning() {
        setDetail(ModerationDetailUiState(record = sub2))

        composeRule.onNodeWithTag("moderation.approve.claude").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("moderation.duplicateWarning").assertDoesNotExist()
    }

    // iOS testQueueAndDetailShowTheSubmitter (detail half)
    @Test
    fun detailShowsTheSubmitter() {
        setDetail(
            ModerationDetailUiState(
                record = sub1,
                submitter = SubmitterState.Loaded(FakeSubmitterProfileReader.SAMPLE),
            ),
        )

        composeRule.onNodeWithTag("moderation.detail.submitter")
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("Ayşe Demir")))
            .assert(hasAnyDescendant(hasText("Boğaziçi Üniversitesi")))
    }

    // iOS testMissingSubmitterProfileDoesNotBlockReview
    @Test
    fun missingSubmitterProfileDoesNotBlockReview() {
        setDetail(ModerationDetailUiState(record = sub2, submitter = SubmitterState.Missing))

        composeRule.onNodeWithTag("moderation.detail.submitter")
            .assertIsDisplayed()
            .assert(hasText("Profil bulunamadı (hesap silinmiş olabilir)"))
        composeRule.onNodeWithTag("moderation.approve.claude").performScrollTo().performClick()

        assertEquals(listOf(AIProvider.CLAUDE), approvals)
    }

    // iOS testQueueAndDetailShowTheSubmitter (queue half)
    @Test
    fun queueRowShowsTheSubmitter() {
        val shown = mutableListOf<String>()
        composeRule.setContent {
            ViFiTheme {
                ModerationContent(
                    state = ModerationUiState(
                        isLoading = false,
                        records = listOf(sub1, sub2),
                        submitters = mapOf(
                            "mock-user" to SubmitterState.Loaded(FakeSubmitterProfileReader.SAMPLE),
                            "mock-user-2" to SubmitterState.Missing,
                        ),
                    ),
                    onRecordClick = {},
                    onRowShown = { shown += it },
                    onRetry = {},
                    onRefresh = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("moderation.row.sub-1").assert(hasText("Gönderen: Ayşe Demir", substring = true))
        composeRule.onNodeWithTag("moderation.row.sub-2")
            .assert(hasText("Profil bulunamadı (hesap silinmiş olabilir)", substring = true))
        composeRule.waitForIdle()
        assertEquals(setOf("mock-user", "mock-user-2"), shown.toSet())
    }
}
