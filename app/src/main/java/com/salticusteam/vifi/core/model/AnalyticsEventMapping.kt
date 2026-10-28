package com.salticusteam.vifi.core.model

// Added in 2.5 — the Firebase Analytics wire format of each event, kept free of Firebase types so it is unit-testable.
/** Event name as sent to Analytics. The names are the contract shared with iOS; never rename one. */
val AnalyticsEvent.firebaseName: String
    get() = when (this) {
        is AnalyticsEvent.ScreenView -> AnalyticsNames.SCREEN_VIEW
        is AnalyticsEvent.Browse -> "browse_level"
        is AnalyticsEvent.ExamOpened -> "exam_open"
        AnalyticsEvent.RecentExamOpened -> "recent_exam_open"
        is AnalyticsEvent.ExamUnlockSheetShown -> "unlock_sheet_shown"
        AnalyticsEvent.EarnCoinsTapped -> "earn_coins_tapped"
        is AnalyticsEvent.ExamUnlocked -> "exam_unlock"
        is AnalyticsEvent.ExamUnlockFailed -> "exam_unlock_failed"
        is AnalyticsEvent.ExamSubmitted -> "exam_submit"
        is AnalyticsEvent.ExamSubmitFailed -> "exam_submit_failed"
        is AnalyticsEvent.LoginCodeRequested -> "login_code_requested"
        AnalyticsEvent.LoginSucceeded -> "login_success"
        is AnalyticsEvent.LoginFailed -> "login_failed"
        is AnalyticsEvent.OnboardingCompleted -> "onboarding_complete"
        AnalyticsEvent.ProfileCompleted -> "profile_completed"
        AnalyticsEvent.ProfileUpdated -> "profile_updated"
        is AnalyticsEvent.SearchUsed -> "search_used"
        AnalyticsEvent.SignedOut -> "sign_out"
        AnalyticsEvent.AccountDeleted -> "account_deleted"
        is AnalyticsEvent.UpdatePromptShown -> "update_prompt_shown"
        is AnalyticsEvent.UpdateAction -> "update_action"
        is AnalyticsEvent.SubmissionApproved -> "submission_approved"
        AnalyticsEvent.SubmissionRejected -> "submission_rejected"
    }

/** Parameters as sent to Analytics (values are `String`, `Long` or `Boolean`), or `null` when the event has none. */
val AnalyticsEvent.firebaseParameters: Map<String, Any>?
    get() = when (this) {
        is AnalyticsEvent.ScreenView -> mapOf(AnalyticsNames.SCREEN_NAME to name)
        is AnalyticsEvent.Browse -> mapOf("level" to level.analyticsName)
        is AnalyticsEvent.ExamOpened -> mapOf("kind" to kind.raw)
        is AnalyticsEvent.ExamUnlockSheetShown -> mapOf("can_afford" to canAfford)
        is AnalyticsEvent.ExamUnlocked -> mapOf(
            "exam_kind" to kind.raw,
            "coins_spent" to coinsSpent.toLong(),
            "already_unlocked" to alreadyUnlocked,
        )
        is AnalyticsEvent.ExamSubmitted -> buildMap {
            put("exam_kind", kind.raw)
            put("file_count", fileCount.toLong())
            tier?.let { put("estimated_tier", it.raw) }
        }
        is AnalyticsEvent.LoginCodeRequested -> mapOf("resend" to isResend)
        is AnalyticsEvent.OnboardingCompleted -> mapOf("method" to method, "source" to source)
        is AnalyticsEvent.SearchUsed -> mapOf("level" to level.analyticsName, "zero_results" to zeroResults)
        is AnalyticsEvent.UpdatePromptShown -> mapOf("type" to if (isRequired) "required" else "optional")
        is AnalyticsEvent.UpdateAction -> mapOf("action" to action)
        is AnalyticsEvent.SubmissionApproved -> mapOf("tier" to tier.raw)
        is AnalyticsEvent.LoginFailed -> mapOf("reason" to reason, "step" to step)
        is AnalyticsEvent.ExamUnlockFailed -> mapOf("reason" to reason)
        is AnalyticsEvent.ExamSubmitFailed -> mapOf("reason" to reason)
        AnalyticsEvent.RecentExamOpened,
        AnalyticsEvent.EarnCoinsTapped,
        AnalyticsEvent.LoginSucceeded,
        AnalyticsEvent.ProfileCompleted,
        AnalyticsEvent.ProfileUpdated,
        AnalyticsEvent.SignedOut,
        AnalyticsEvent.AccountDeleted,
        AnalyticsEvent.SubmissionRejected,
        -> null
    }

/** Stable, language-independent level names for reports. */
val ArchiveLevel.analyticsName: String
    get() = when (this) {
        ArchiveLevel.UNIVERSITY -> "university"
        ArchiveLevel.FACULTY -> "faculty"
        ArchiveLevel.DEPARTMENT -> "department"
        ArchiveLevel.LESSON -> "lesson"
        ArchiveLevel.EXAM -> "exam"
    }

/** Firebase's own reserved names (`FirebaseAnalytics.Event.SCREEN_VIEW` / `Param.SCREEN_NAME`). */
object AnalyticsNames {
    const val SCREEN_VIEW = "screen_view"
    const val SCREEN_NAME = "screen_name"
}

// Added in 2.5 — the screen names reported by screen_view (iOS parity; Analytics' automatic reporting is off).
object AnalyticsScreen {
    const val HOME = "Home"
    const val BROWSE = "Browse"
    const val LOGIN = "Login"
    const val ONBOARDING = "Onboarding"
    const val PROFILE_COMPLETION = "ProfileCompletion"
    const val EDIT_PROFILE = "EditProfile"
    const val ADD_EXAM = "AddExam"
    const val EXAM_VIEWER = "ExamViewer"
    const val EXAM_PDF = "ExamPDF"
    const val EXAM_IMAGES = "ExamImages"
    const val INBOX = "Inbox"
    const val MODERATION = "Moderation"
    const val ABOUT = "About"
    const val ACCOUNT = "Account"

    // Added in 4.0.0 — the ViFi Premium paywall (iOS `screenView(name: "Paywall")`).
    const val PAYWALL = "Paywall"
}

// Added in 2.5 — fixed values of event parameters that iOS reports as plain strings.
object AnalyticsValues {
    /** `onboarding_complete.method`. */
    const val ONBOARDING_FINISHED = "finished"
    const val ONBOARDING_SKIPPED = "skipped"

    /** `onboarding_complete.source`. */
    const val ONBOARDING_SOURCE_FIRST_LAUNCH = "first_launch"

    // Added in 2.5 — the tour replayed from About; it never marks the tour as seen.
    const val ONBOARDING_SOURCE_REPLAY = "replay"

    /** `update_action.action`. */
    const val UPDATE_ACTION_UPDATE = "update"
    const val UPDATE_ACTION_SKIP = "skip"
    const val UPDATE_ACTION_LATER = "later"
}
