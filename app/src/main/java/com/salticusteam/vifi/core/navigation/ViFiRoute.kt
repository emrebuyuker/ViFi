package com.salticusteam.vifi.core.navigation

import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations (iOS `Route` parity). [ArchivePath] travels as two string lists
 * so no custom NavType is needed; PII never goes into a route.
 */
sealed interface ViFiRoute {

    /** Root screen: the university list (iOS root, not a pushed route there). */
    @Serializable
    data object Home : ViFiRoute

    /** The list of entries under [path] (faculties, departments, lessons or exams). */
    @Serializable
    data class Browse(
        val ids: List<String>,
        val names: List<String>,
    ) : ViFiRoute {
        constructor(path: ArchivePath) : this(path.components, path.displayNames)

        val path: ArchivePath get() = ArchivePath(ids, names)
    }

    /** The viewer of the exam at [path]. */
    @Serializable
    data class Exam(
        val ids: List<String>,
        val names: List<String>,
        val kind: ExamKind,
    ) : ViFiRoute {
        constructor(path: ArchivePath, kind: ExamKind) : this(path.components, path.displayNames, kind)

        val path: ArchivePath get() = ArchivePath(ids, names)
    }

    /** The signed-in account: profile, phone number, sign out (opened from the home screen). */
    @Serializable
    data object Account : ViFiRoute

    /** The announcements inbox (opened from the home toolbar's bell or a notification tap). */
    @Serializable
    data object Inbox : ViFiRoute

    /** App information: version, highlights, tour replay, "Bize Ulaş" (opened from the account screen). */
    @Serializable
    data object About : ViFiRoute

    /** Edits the signed-in user's profile (opened from the account screen). */
    @Serializable
    data object EditProfile : ViFiRoute

    /**
     * Submits an exam for moderation (opened from the home screen and the unlock sheet's "Sınav Ekle"). From the
     * unlock sheet [ids] / [names] carry the locked exam's lesson, which pre-fills the school and lesson rows.
     */
    @Serializable
    data class AddExam(
        val ids: List<String> = emptyList(),
        val names: List<String> = emptyList(),
    ) : ViFiRoute {
        constructor(context: ArchivePath?) : this(context?.components.orEmpty(), context?.displayNames.orEmpty())

        /** The archive location the form was opened from, `null` from the home screen. */
        val context: ArchivePath? get() = ArchivePath(ids, names).takeUnless { it.isRoot }
    }

    /** The ViFi Premium subscription screen (opened from a locked exam's sheet and from the account screen). */
    @Serializable
    data object Paywall : ViFiRoute

    /** The admin moderation queue (opened from the account screen, by an admin only). */
    @Serializable
    data object Moderation : ViFiRoute

    /** One pending submission under review; the record itself is read from the loaded queue. */
    @Serializable
    data class ModerationDetail(val submissionId: String) : ViFiRoute

    /** The pending files of the submission [submissionId], previewed before approving. */
    @Serializable
    data class SubmissionPreview(val submissionId: String) : ViFiRoute
}
