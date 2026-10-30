package com.salticusteam.vifi.core.model

// Added in 2.5 — the analytics events the app reports.
sealed interface AnalyticsEvent {
    data class ScreenView(val name: String) : AnalyticsEvent

    data class Browse(val level: ArchiveLevel) : AnalyticsEvent

    data class ExamOpened(val kind: ExamKind) : AnalyticsEvent

    data object RecentExamOpened : AnalyticsEvent

    // Coin economy
    data class ExamUnlockSheetShown(val canAfford: Boolean) : AnalyticsEvent

    data object EarnCoinsTapped : AnalyticsEvent

    data class ExamUnlocked(val kind: ExamKind, val coinsSpent: Int, val alreadyUnlocked: Boolean) : AnalyticsEvent

    data class ExamUnlockFailed(val reason: String) : AnalyticsEvent

    // Exam submission (supply side)
    data class ExamSubmitted(val kind: ExamKind, val fileCount: Int, val tier: SubmissionTier?) : AnalyticsEvent

    data class ExamSubmitFailed(val reason: String) : AnalyticsEvent

    // Login funnel
    data class LoginCodeRequested(val isResend: Boolean) : AnalyticsEvent

    data object LoginSucceeded : AnalyticsEvent

    /** [step] is the [LoginFailureStage] raw value ("send_code" | "verify"); [reason] is `AuthError.analyticsReason`. */
    data class LoginFailed(val reason: String, val step: String) : AnalyticsEvent

    // Activation
    data class OnboardingCompleted(val method: String, val source: String) : AnalyticsEvent

    data object ProfileCompleted : AnalyticsEvent

    data object ProfileUpdated : AnalyticsEvent

    data class SearchUsed(val level: ArchiveLevel, val zeroResults: Boolean) : AnalyticsEvent

    // Lifecycle & operations
    data object SignedOut : AnalyticsEvent

    data object AccountDeleted : AnalyticsEvent

    data class UpdatePromptShown(val isRequired: Boolean) : AnalyticsEvent

    data class UpdateAction(val action: String) : AnalyticsEvent

    data class SubmissionApproved(val tier: SubmissionTier) : AnalyticsEvent

    data object SubmissionRejected : AnalyticsEvent

    // Notification permission (step 1/2) and campaign consent (step 2/2, or Account's switch); iOS names.

    // Added in 4.1.0 — the permission step was shown.
    data class NotificationPrimingShown(val source: NotificationPromptSource) : AnalyticsEvent

    // Added in 4.1.0 — "Bildirimleri Aç" (before the system dialog, whatever it answers) or "Ayarlar'ı Aç" was tapped.
    data class NotificationPrimingAccepted(val source: NotificationPromptSource) : AnalyticsEvent

    // Added in 4.1.0 — "Şimdi Değil" was tapped (closing the sheet is not reported).
    data class NotificationPrimingDeclined(val source: NotificationPromptSource) : AnalyticsEvent

    // Added in 4.1.0 — the campaign consent step was shown.
    data class MarketingConsentShown(val source: NotificationPromptSource) : AnalyticsEvent

    // Added in 4.1.0 — campaigns were turned on and the answer was saved.
    data class MarketingConsentAccepted(val source: NotificationPromptSource) : AnalyticsEvent

    // Added in 4.1.0 — campaigns were turned off (or refused) and the answer was saved.
    data class MarketingConsentDeclined(val source: NotificationPromptSource) : AnalyticsEvent
}

// Added in 2.5 — the signed-in user's analytics identity: a stable id plus low-cardinality segment properties.
/** Never free text or anything identifying. A `null` identity clears it on sign-out. */
data class AnalyticsUser(
    val id: String,
    val universityId: String?,
    val facultyId: String?,
    val departmentId: String?,
    /** "female" | "male". */
    val gender: String?,
    val isAdmin: Boolean,
) {
    /**
     * The user-property values that are known, keyed by name, clamped to Analytics' 36-character limit. A name
     * absent from the map has no known value (e.g. no profile yet) and is cleared when applied.
     */
    val properties: Map<String, String>
        get() = buildMap {
            put("is_admin", if (isAdmin) "true" else "false")
            clamped(universityId)?.let { put("university_id", it) }
            clamped(facultyId)?.let { put("faculty_id", it) }
            clamped(departmentId)?.let { put("department_id", it) }
            clamped(gender)?.let { put("gender", it) }
        }

    companion object {
        /** Every analytics user-property name, so each can be set or cleared on identity changes. */
        val PROPERTY_NAMES = listOf("university_id", "faculty_id", "department_id", "gender", "is_admin")

        private const val MAX_VALUE_LENGTH = 36

        /** Builds the identity from the user's id, their profile (when present) and admin status. */
        fun from(id: String, profile: UserProfile?, isAdmin: Boolean): AnalyticsUser = AnalyticsUser(
            id = id,
            universityId = profile?.universityId,
            facultyId = profile?.facultyId,
            departmentId = profile?.departmentId,
            gender = profile?.gender?.raw,
            isAdmin = isAdmin,
        )

        private fun clamped(value: String?): String? = value?.take(MAX_VALUE_LENGTH)
    }
}
