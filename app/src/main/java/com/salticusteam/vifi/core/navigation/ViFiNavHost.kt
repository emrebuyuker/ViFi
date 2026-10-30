package com.salticusteam.vifi.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind

/**
 * The app's single navigation graph. Destinations are slots so the host (AppRoot, FM-22) plugs in the
 * feature screens without this file depending on them; each slot receives its decoded route arguments.
 */
@Composable
fun ViFiNavHost(
    navController: NavHostController,
    home: @Composable () -> Unit,
    browse: @Composable (path: ArchivePath) -> Unit,
    exam: @Composable (path: ArchivePath, kind: ExamKind) -> Unit,
    account: @Composable () -> Unit,
    editProfile: @Composable () -> Unit,
    about: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    inbox: @Composable (highlightId: String?) -> Unit = {},
    addExam: @Composable (context: ArchivePath?) -> Unit = {},
    paywall: @Composable () -> Unit = {},
    moderation: @Composable () -> Unit = {},
    moderationDetail: @Composable (submissionId: String) -> Unit = {},
    submissionPreview: @Composable (submissionId: String) -> Unit = {},
    notificationSettings: @Composable () -> Unit = {},
    notificationComposer: @Composable () -> Unit = {},
    notificationHistory: @Composable () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = ViFiRoute.Home,
        modifier = modifier,
    ) {
        composable<ViFiRoute.Home> {
            home()
        }
        composable<ViFiRoute.Browse> { entry ->
            browse(entry.toRoute<ViFiRoute.Browse>().path)
        }
        composable<ViFiRoute.Exam> { entry ->
            val route = entry.toRoute<ViFiRoute.Exam>()
            exam(route.path, route.kind)
        }
        composable<ViFiRoute.Account> {
            account()
        }
        composable<ViFiRoute.EditProfile> {
            editProfile()
        }
        composable<ViFiRoute.Inbox> { entry ->
            inbox(entry.toRoute<ViFiRoute.Inbox>().highlightId)
        }
        composable<ViFiRoute.About> {
            about()
        }
        composable<ViFiRoute.AddExam> { entry ->
            addExam(entry.toRoute<ViFiRoute.AddExam>().context)
        }
        composable<ViFiRoute.Paywall> {
            paywall()
        }
        composable<ViFiRoute.Moderation> {
            moderation()
        }
        composable<ViFiRoute.ModerationDetail> { entry ->
            moderationDetail(entry.toRoute<ViFiRoute.ModerationDetail>().submissionId)
        }
        composable<ViFiRoute.SubmissionPreview> { entry ->
            submissionPreview(entry.toRoute<ViFiRoute.SubmissionPreview>().submissionId)
        }
        composable<ViFiRoute.NotificationSettings> {
            notificationSettings()
        }
        composable<ViFiRoute.NotificationComposer> {
            notificationComposer()
        }
        composable<ViFiRoute.NotificationHistory> {
            notificationHistory()
        }
    }
}
