package com.salticusteam.vifi.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.util.ExamRouting

/**
 * Owns stack operations so any screen can go home, jump to a breadcrumb or deep-link (iOS `Router` parity).
 * Screens never touch the NavController; they raise intents that the NavHost maps onto these calls.
 */
class ViFiRouter(private val navController: NavHostController) {

    // No launchSingleTop: it matches the destination type, not its arguments, so Browse(faculty)
    // would replace Browse(university) instead of stacking on it.
    fun push(route: ViFiRoute) {
        navController.navigate(route)
    }

    fun pop(): Boolean = navController.popBackStack()

    /**
     * Opens the inbox for a tapped push, highlighting [highlightId]. Single top: an inbox already on top only takes
     * the new highlight (no second inbox to back out of); the bell keeps using [push] with `Inbox()`.
     */
    fun showInbox(highlightId: String?) {
        navController.navigate(ViFiRoute.Inbox(highlightId)) { launchSingleTop = true }
    }

    fun popToRoot() {
        navController.popBackStack(ViFiRoute.Home, inclusive = false)
    }

    /** Shows the list at [archivePath], keeping its ancestors on the stack (breadcrumb tap). */
    fun popTo(archivePath: ArchivePath) {
        if (archivePath.isRoot) {
            popToRoot()
            return
        }
        // Normal drill-down already has the entry; otherwise rebuild the hierarchy like iOS `path.prefix(depth)`.
        if (!navController.popBackStack(ViFiRoute.Browse(archivePath), inclusive = false)) {
            popToRoot()
            pushAncestorsAndSelf(archivePath)
        }
    }

    /**
     * Opens an exam with its whole hierarchy on the stack, so Back walks up the archive. The screen is chosen by
     * `ExamRouting`: callers that only know the exam's file kind (recents, notifications) pass no [questionCount] and
     * open the file viewer, which replaces itself with the question screen once it sees the exam is structured
     * ([replaceExamWithQuestions], iOS `Router.openExam`).
     */
    fun openExam(examPath: ArchivePath, kind: ExamKind, questionCount: Int? = null) {
        popToRoot()
        pushAncestorsAndSelf(examPath.parent ?: ArchivePath.ROOT)
        navController.navigate(ExamRouting.route(examPath, kind, questionCount))
    }

    /**
     * Puts the question screen [route] in place of the file viewer of the same exam on top of the stack (SQS-01), so
     * Back skips the viewer. Does nothing when that viewer is no longer on top (the user left meanwhile); single top
     * keeps a repeated event from stacking a second question screen.
     */
    fun replaceExamWithQuestions(route: ViFiRoute.StructuredExam) {
        val top = navController.currentBackStackEntry ?: return
        if (!top.destination.hasRoute<ViFiRoute.Exam>()) return
        if (top.toRoute<ViFiRoute.Exam>().path != route.path) return
        navController.navigate(route) {
            popUpTo(top.destination.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    private fun pushAncestorsAndSelf(path: ArchivePath) {
        for (depth in 1..path.depth) {
            navController.navigate(ViFiRoute.Browse(path.prefix(depth)))
        }
    }
}

@Composable
fun rememberViFiRouter(navController: NavHostController): ViFiRouter =
    remember(navController) { ViFiRouter(navController) }
