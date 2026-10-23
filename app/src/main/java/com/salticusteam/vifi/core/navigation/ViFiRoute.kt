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

    /** Edits the signed-in user's profile (opened from the account screen). */
    @Serializable
    data object EditProfile : ViFiRoute
}
