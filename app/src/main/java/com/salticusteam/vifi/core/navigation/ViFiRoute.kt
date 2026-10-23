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
}
