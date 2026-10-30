package com.salticusteam.vifi.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind

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

    /** Opens an exam with its whole hierarchy on the stack, so Back walks up the archive. */
    fun openExam(examPath: ArchivePath, kind: ExamKind) {
        popToRoot()
        pushAncestorsAndSelf(examPath.parent ?: ArchivePath.ROOT)
        navController.navigate(ViFiRoute.Exam(examPath, kind))
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
