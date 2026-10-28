package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Google Analytics' collection limits (AN-11): a name or value outside them is dropped or truncated silently by
 * the SDK, so they are checked here for every event, parameter, user property and stable reason value.
 */
class AnalyticsLimitsTest {

    /** One sample per event; [kindIndex] makes adding an event without a sample a compile error. */
    private val samples: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.ScreenView(AnalyticsScreen.PROFILE_COMPLETION),
        AnalyticsEvent.Browse(ArchiveLevel.DEPARTMENT),
        AnalyticsEvent.ExamOpened(ExamKind.PDF),
        AnalyticsEvent.RecentExamOpened,
        AnalyticsEvent.ExamUnlockSheetShown(canAfford = true),
        AnalyticsEvent.EarnCoinsTapped,
        AnalyticsEvent.ExamUnlocked(ExamKind.IMAGES, coinsSpent = 100, alreadyUnlocked = false),
        AnalyticsEvent.ExamUnlockFailed(ExamUnlockError.InsufficientCoins.analyticsReason),
        AnalyticsEvent.ExamSubmitted(ExamKind.IMAGES, fileCount = 10, tier = SubmissionTier.NEW_UNIVERSITY),
        AnalyticsEvent.ExamSubmitFailed(ExamSubmissionError.InvalidSubmission.analyticsReason),
        AnalyticsEvent.LoginCodeRequested(isResend = true),
        AnalyticsEvent.LoginSucceeded,
        AnalyticsEvent.LoginFailed(AuthError.SmsTemporarilyUnavailable.analyticsReason, LoginFailureStage.SEND_CODE.raw),
        AnalyticsEvent.OnboardingCompleted(AnalyticsValues.ONBOARDING_SKIPPED, AnalyticsValues.ONBOARDING_SOURCE_FIRST_LAUNCH),
        AnalyticsEvent.ProfileCompleted,
        AnalyticsEvent.ProfileUpdated,
        AnalyticsEvent.SearchUsed(ArchiveLevel.LESSON, zeroResults = true),
        AnalyticsEvent.SignedOut,
        AnalyticsEvent.AccountDeleted,
        AnalyticsEvent.UpdatePromptShown(isRequired = false),
        AnalyticsEvent.UpdateAction(AnalyticsValues.UPDATE_ACTION_LATER),
        AnalyticsEvent.SubmissionApproved(SubmissionTier.NEW_DEPARTMENT),
        AnalyticsEvent.SubmissionRejected,
    )

    private fun kindIndex(event: AnalyticsEvent): Int = when (event) {
        is AnalyticsEvent.ScreenView -> 0
        is AnalyticsEvent.Browse -> 1
        is AnalyticsEvent.ExamOpened -> 2
        AnalyticsEvent.RecentExamOpened -> 3
        is AnalyticsEvent.ExamUnlockSheetShown -> 4
        AnalyticsEvent.EarnCoinsTapped -> 5
        is AnalyticsEvent.ExamUnlocked -> 6
        is AnalyticsEvent.ExamUnlockFailed -> 7
        is AnalyticsEvent.ExamSubmitted -> 8
        is AnalyticsEvent.ExamSubmitFailed -> 9
        is AnalyticsEvent.LoginCodeRequested -> 10
        AnalyticsEvent.LoginSucceeded -> 11
        is AnalyticsEvent.LoginFailed -> 12
        is AnalyticsEvent.OnboardingCompleted -> 13
        AnalyticsEvent.ProfileCompleted -> 14
        AnalyticsEvent.ProfileUpdated -> 15
        is AnalyticsEvent.SearchUsed -> 16
        AnalyticsEvent.SignedOut -> 17
        AnalyticsEvent.AccountDeleted -> 18
        is AnalyticsEvent.UpdatePromptShown -> 19
        is AnalyticsEvent.UpdateAction -> 20
        is AnalyticsEvent.SubmissionApproved -> 21
        AnalyticsEvent.SubmissionRejected -> 22
    }

    @Test
    fun `samples cover every event exactly once`() {
        assertEquals((0..22).toList(), samples.map(::kindIndex).sorted())
    }

    @Test
    fun `event names are valid, within 40 characters and unique`() {
        val names = samples.map { it.firebaseName }

        names.forEach { assertValidName(it, max = EVENT_NAME_MAX, what = "event name") }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `parameters stay within the per-event count, name and value limits`() {
        samples.forEach { event ->
            val params = event.firebaseParameters.orEmpty()
            assertTrue("${event.firebaseName} has too many parameters", params.size <= PARAMS_PER_EVENT_MAX)
            params.forEach { (name, value) ->
                // screen_name is Firebase's own reserved parameter; the custom-name rules apply to the rest.
                if (name != AnalyticsNames.SCREEN_NAME) assertValidName(name, max = PARAM_NAME_MAX, what = "parameter")
                when (value) {
                    is String -> assertTrue("$name value too long: $value", value.length <= PARAM_VALUE_MAX)
                    is Long, is Boolean -> Unit
                    else -> throw AssertionError("$name has unsupported type ${value::class}")
                }
            }
        }
    }

    @Test
    fun `screen names fit the parameter value limit`() {
        val screens = listOf(
            AnalyticsScreen.HOME, AnalyticsScreen.BROWSE, AnalyticsScreen.LOGIN, AnalyticsScreen.ONBOARDING,
            AnalyticsScreen.PROFILE_COMPLETION, AnalyticsScreen.EDIT_PROFILE, AnalyticsScreen.ADD_EXAM,
            AnalyticsScreen.EXAM_VIEWER, AnalyticsScreen.EXAM_PDF, AnalyticsScreen.EXAM_IMAGES, AnalyticsScreen.INBOX,
            AnalyticsScreen.MODERATION, AnalyticsScreen.ABOUT, AnalyticsScreen.ACCOUNT, AnalyticsScreen.PAYWALL,
        )

        assertEquals(15, screens.toSet().size)
        screens.forEach { assertTrue(it.length <= PARAM_VALUE_MAX) }
    }

    @Test
    fun `every stable reason value fits the parameter value limit`() {
        val reasons = listOf(
            AuthError.InvalidPhoneNumber, AuthError.InvalidCode, AuthError.CodeExpired, AuthError.TooManyRequests,
            AuthError.SmsTemporarilyUnavailable, AuthError.Offline, AuthError.VerificationCancelled,
            AuthError.AppVerificationFailed, AuthError.RequiresRecentLogin, AuthError.NotSignedIn,
            AuthError.AccountDisabled, AuthError.Unknown("x"),
        ).map { it.analyticsReason } +
            listOf(
                ExamUnlockError.InsufficientCoins, ExamUnlockError.NotFound, ExamUnlockError.NotSignedIn,
                ExamUnlockError.Offline, ExamUnlockError.Unknown("x"),
            ).map { it.analyticsReason } +
            listOf(
                ExamSubmissionError.NotSignedIn, ExamSubmissionError.InvalidSubmission,
                ExamSubmissionError.PermissionDenied, ExamSubmissionError.Offline, ExamSubmissionError.Unknown("x"),
            ).map { it.analyticsReason }

        reasons.forEach { assertTrue(it.matches(Regex("[a-z_]{1,$PARAM_VALUE_MAX}"))) }
    }

    @Test
    fun `user property names are valid and within 24 characters`() {
        AnalyticsUser.PROPERTY_NAMES.forEach { assertValidName(it, max = USER_PROPERTY_NAME_MAX, what = "user property") }
    }

    @Test
    fun `user property values never exceed 36 characters`() {
        val long = "x".repeat(80)
        val user = AnalyticsUser("u", universityId = long, facultyId = long, departmentId = long, gender = long, isAdmin = true)

        user.properties.values.forEach { assertTrue(it.length <= USER_PROPERTY_VALUE_MAX) }
    }

    private fun assertValidName(name: String, max: Int, what: String) {
        assertTrue("$what '$name' is too long", name.length <= max)
        assertTrue("$what '$name' is not [a-zA-Z][a-zA-Z0-9_]*", name.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*")))
        RESERVED_PREFIXES.forEach { assertTrue("$what '$name' uses reserved prefix $it", !name.startsWith(it)) }
    }

    private companion object {
        const val EVENT_NAME_MAX = 40
        const val PARAM_NAME_MAX = 40
        const val PARAM_VALUE_MAX = 100
        const val PARAMS_PER_EVENT_MAX = 25
        const val USER_PROPERTY_NAME_MAX = 24
        const val USER_PROPERTY_VALUE_MAX = 36
        val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")
    }
}
