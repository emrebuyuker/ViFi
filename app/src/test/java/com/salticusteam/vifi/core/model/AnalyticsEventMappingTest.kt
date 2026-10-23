package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyticsEventMappingTest {

    @Test
    fun `firebaseName matches the iOS event names for all 23 events`() {
        val expected = mapOf(
            AnalyticsEvent.ScreenView("Home") to "screen_view",
            AnalyticsEvent.Browse(ArchiveLevel.FACULTY) to "browse_level",
            AnalyticsEvent.ExamOpened(ExamKind.PDF) to "exam_open",
            AnalyticsEvent.RecentExamOpened to "recent_exam_open",
            AnalyticsEvent.ExamUnlockSheetShown(true) to "unlock_sheet_shown",
            AnalyticsEvent.EarnCoinsTapped to "earn_coins_tapped",
            AnalyticsEvent.ExamUnlocked(ExamKind.PDF, 100, false) to "exam_unlock",
            AnalyticsEvent.ExamUnlockFailed("offline") to "exam_unlock_failed",
            AnalyticsEvent.ExamSubmitted(ExamKind.IMAGES, 2, null) to "exam_submit",
            AnalyticsEvent.ExamSubmitFailed("offline") to "exam_submit_failed",
            AnalyticsEvent.LoginCodeRequested(false) to "login_code_requested",
            AnalyticsEvent.LoginSucceeded to "login_success",
            AnalyticsEvent.LoginFailed("offline", "verify") to "login_failed",
            AnalyticsEvent.OnboardingCompleted("finished", "first_launch") to "onboarding_complete",
            AnalyticsEvent.ProfileCompleted to "profile_completed",
            AnalyticsEvent.ProfileUpdated to "profile_updated",
            AnalyticsEvent.SearchUsed(ArchiveLevel.LESSON, true) to "search_used",
            AnalyticsEvent.SignedOut to "sign_out",
            AnalyticsEvent.AccountDeleted to "account_deleted",
            AnalyticsEvent.UpdatePromptShown(true) to "update_prompt_shown",
            AnalyticsEvent.UpdateAction("later") to "update_action",
            AnalyticsEvent.SubmissionApproved(SubmissionTier.NEW_COURSE) to "submission_approved",
            AnalyticsEvent.SubmissionRejected to "submission_rejected",
        )

        assertEquals(23, expected.size)
        expected.forEach { (event, name) -> assertEquals(name, event.firebaseName) }
    }

    @Test
    fun `screen view carries screen_name`() {
        assertEquals(mapOf("screen_name" to "Browse"), AnalyticsEvent.ScreenView("Browse").firebaseParameters)
    }

    @Test
    fun `exam unlocked reports kind raw, coins as Long and flag`() {
        val params = AnalyticsEvent.ExamUnlocked(ExamKind.IMAGES, 100, alreadyUnlocked = true).firebaseParameters

        assertEquals(mapOf("exam_kind" to "JPG", "coins_spent" to 100L, "already_unlocked" to true), params)
    }

    @Test
    fun `exam submitted includes estimated_tier only when known`() {
        val withTier = AnalyticsEvent.ExamSubmitted(ExamKind.PDF, 3, SubmissionTier.NEW_FACULTY).firebaseParameters
        val withoutTier = AnalyticsEvent.ExamSubmitted(ExamKind.PDF, 3, null).firebaseParameters

        assertEquals(mapOf("exam_kind" to "PDF", "file_count" to 3L, "estimated_tier" to "newFaculty"), withTier)
        assertEquals(mapOf("exam_kind" to "PDF", "file_count" to 3L), withoutTier)
    }

    @Test
    fun `level parameters use stable language-independent names`() {
        val names = ArchiveLevel.entries.map {
            (AnalyticsEvent.Browse(it).firebaseParameters ?: emptyMap())["level"]
        }

        assertEquals(listOf("university", "faculty", "department", "lesson", "exam"), names)
    }

    @Test
    fun `update prompt maps isRequired to type`() {
        assertEquals(mapOf("type" to "required"), AnalyticsEvent.UpdatePromptShown(true).firebaseParameters)
        assertEquals(mapOf("type" to "optional"), AnalyticsEvent.UpdatePromptShown(false).firebaseParameters)
    }

    @Test
    fun `failure events report reason and step`() {
        assertEquals(
            mapOf("reason" to "invalid_code", "step" to "verify"),
            AnalyticsEvent.LoginFailed("invalid_code", "verify").firebaseParameters,
        )
        assertEquals(mapOf("reason" to "offline"), AnalyticsEvent.ExamUnlockFailed("offline").firebaseParameters)
        assertEquals(mapOf("reason" to "offline"), AnalyticsEvent.ExamSubmitFailed("offline").firebaseParameters)
    }

    @Test
    fun `other parameters match iOS keys`() {
        assertEquals(mapOf("can_afford" to false), AnalyticsEvent.ExamUnlockSheetShown(false).firebaseParameters)
        assertEquals(mapOf("resend" to true), AnalyticsEvent.LoginCodeRequested(true).firebaseParameters)
        assertEquals(
            mapOf("method" to "skipped", "source" to "first_launch"),
            AnalyticsEvent.OnboardingCompleted("skipped", "first_launch").firebaseParameters,
        )
        assertEquals(
            mapOf("level" to "department", "zero_results" to true),
            AnalyticsEvent.SearchUsed(ArchiveLevel.DEPARTMENT, true).firebaseParameters,
        )
        assertEquals(mapOf("action" to "skip"), AnalyticsEvent.UpdateAction("skip").firebaseParameters)
        assertEquals(mapOf("tier" to "duplicate"), AnalyticsEvent.SubmissionApproved(SubmissionTier.DUPLICATE).firebaseParameters)
    }

    @Test
    fun `events without data have no parameters`() {
        listOf(
            AnalyticsEvent.RecentExamOpened,
            AnalyticsEvent.EarnCoinsTapped,
            AnalyticsEvent.LoginSucceeded,
            AnalyticsEvent.ProfileCompleted,
            AnalyticsEvent.ProfileUpdated,
            AnalyticsEvent.SignedOut,
            AnalyticsEvent.AccountDeleted,
            AnalyticsEvent.SubmissionRejected,
        ).forEach { assertNull(it.firebaseParameters) }
    }

    @Test
    fun `reasons and stages match the iOS raw values`() {
        assertEquals("send_code", LoginFailureStage.SEND_CODE.raw)
        assertEquals("verify", LoginFailureStage.VERIFY.raw)
        assertEquals("sms_temporarily_unavailable", AuthError.SmsTemporarilyUnavailable.analyticsReason)
        assertEquals("insufficient_coins", ExamUnlockError.InsufficientCoins.analyticsReason)
        assertEquals("invalid_submission", ExamSubmissionError.InvalidSubmission.analyticsReason)
        assertEquals(
            listOf("newUniversity", "newFaculty", "newDepartment", "newCourse", "newExam", "duplicate"),
            SubmissionTier.entries.map { it.raw },
        )
    }
}
