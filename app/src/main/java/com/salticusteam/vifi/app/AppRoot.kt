package com.salticusteam.vifi.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.core.navigation.ViFiNavHost
import com.salticusteam.vifi.core.navigation.ViFiRoute
import com.salticusteam.vifi.core.navigation.ViFiRouter
import com.salticusteam.vifi.core.navigation.rememberViFiRouter
import com.salticusteam.vifi.feature.about.AboutScreen
import com.salticusteam.vifi.feature.account.AccountScreen
import com.salticusteam.vifi.feature.addexam.AddExamScreen
import com.salticusteam.vifi.feature.browse.BrowseScreen
import com.salticusteam.vifi.feature.examviewer.ExamViewerScreen
import com.salticusteam.vifi.feature.home.HomeScreen
import com.salticusteam.vifi.feature.inbox.InboxScreen
import com.salticusteam.vifi.feature.login.LoginScreen
import com.salticusteam.vifi.feature.moderation.ModerationDetailScreen
import com.salticusteam.vifi.feature.moderation.ModerationScreen
import com.salticusteam.vifi.feature.moderation.SubmissionPreviewScreen
import com.salticusteam.vifi.feature.notificationcomposer.NotificationComposerScreen
import com.salticusteam.vifi.feature.notificationcomposer.NotificationHistoryScreen
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.NotificationComposerLocationRows
import com.salticusteam.vifi.feature.notifications.NotificationPromptHost
import com.salticusteam.vifi.feature.notifications.NotificationPromptViewModel
import com.salticusteam.vifi.feature.notificationsettings.NotificationSettingsScreen
import com.salticusteam.vifi.feature.onboarding.OnboardingGate
import com.salticusteam.vifi.feature.premium.PaywallScreen
import com.salticusteam.vifi.feature.profile.EditProfileScreen
import com.salticusteam.vifi.feature.update.AppUpdateGate

/**
 * The app's root composable (iOS RootView): gates the archive behind sign-in, then behind a completed profile
 * ([ProfileGate]), all beneath the required-update gate ([AppUpdateGate]). The app-switcher privacy cover wraps it
 * in [MainActivity]; badge clearing and disk-cache pruning follow the process lifecycle ([AppLifecycleHooks]).
 */
@Composable
fun AppRoot() {
    // The required-update gate is outermost: it replaces everything beneath it, sign-in included.
    AppUpdateGate {
        SessionRoot()
    }
}

@Composable
private fun SessionRoot(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()

    when (val state = session) {
        SessionState.Unknown -> LoadingView(modifier = Modifier.testTag("session.loading"))
        SessionState.SignedOut -> LoginScreen()
        // Keyed by user: after a sign-out the next user starts on the home screen with a fresh stack (iOS popToRoot).
        is SessionState.SignedIn -> key(state.user.id) {
            ProfileGate(user = state.user) { profile ->
                // The first-launch tour sits over the archive, after sign-in and the profile gate (iOS RootView).
                OnboardingGate { ArchiveHost(user = state.user, profile = profile) }
            }
        }
    }
}

/** The archive navigation stack, shown once the user is signed in and has a profile. */
@Composable
private fun ArchiveHost(
    user: AuthUser,
    profile: UserProfile,
    // The activity's: it outlives this host, so a sign-out still ends the flow it shows.
    notificationPrompts: NotificationPromptViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val router = rememberViFiRouter(navController)

    // A notification tap opens the inbox on that notification; a cold-start tap waits here until the signed-in host
    // exists, the tour is over and no modal is in the way.
    PushTapDelivery(router)
    // The notification permission and campaign consent flow, over whatever screen is up once nothing is in the way.
    NotificationPromptHost(notificationPrompts)
    ArchivePrefetch()
    PaidUnlockRecovery()

    ViFiNavHost(
        navController = navController,
        modifier = Modifier.fillMaxSize(),
        home = {
            HomeScreen(
                onItemClick = { router.open(it) },
                // The exam's whole hierarchy goes on the stack, so Back walks up the archive (iOS openExam).
                onRecentClick = { router.openExam(it.path, it.kind) },
                onAddExamClick = { router.push(ViFiRoute.AddExam()) },
                onAccountClick = { router.push(ViFiRoute.Account) },
                onInboxClick = { router.push(ViFiRoute.Inbox()) },
                onAboutClick = { router.push(ViFiRoute.About) },
            )
        },
        browse = { _ ->
            BrowseScreen(
                onItemClick = { router.open(it) },
                onBack = { router.pop() },
                onBreadcrumbSelect = { router.popTo(it) },
                onHomeClick = { router.popToRoot() },
                // The unlock sheet's "Sınav Ekle": earn coins by contributing an exam, pre-filled with its lesson.
                onEarnCoins = { lesson -> router.push(ViFiRoute.AddExam(lesson)) },
                // The unlock sheet's "Tek tek açmak yerine Premium'a geç".
                onOpenPaywall = { router.push(ViFiRoute.Paywall) },
            )
        },
        exam = { path, kind ->
            ExamViewerScreen(path = path, routeKind = kind, onBack = { router.pop() })
        },
        account = {
            AccountScreen(
                onEditProfile = { router.push(ViFiRoute.EditProfile) },
                onAbout = { router.push(ViFiRoute.About) },
                onModeration = { router.push(ViFiRoute.Moderation) },
                onNotificationComposer = { router.push(ViFiRoute.NotificationComposer) },
                onOpenPaywall = { router.push(ViFiRoute.Paywall) },
                onNotificationSettings = { router.push(ViFiRoute.NotificationSettings) },
                onBack = { router.pop() },
            )
        },
        editProfile = {
            EditProfileScreen(user = user, existing = profile, onBack = { router.pop() })
        },
        inbox = { highlightId ->
            InboxScreen(
                highlightId = highlightId,
                onBack = { router.pop() },
                // The exam's whole hierarchy goes on the stack, so Back walks up the archive (iOS openExam).
                onOpenExam = { path, kind -> router.openExam(path, kind) },
                onOpenPaywall = { router.push(ViFiRoute.Paywall) },
                onAddExam = { lesson -> router.push(ViFiRoute.AddExam(lesson)) },
            )
        },
        about = {
            AboutScreen(onBack = { router.pop() })
        },
        addExam = { context ->
            AddExamScreen(
                ownerId = user.id,
                profile = profile,
                archiveContext = context,
                onClose = { router.pop() },
                // Back where it came from first; the permission's re-ask (if due) then waits for nothing in the way.
                onSubmitted = {
                    router.pop()
                    notificationPrompts.onExamSubmitted()
                },
            )
        },
        paywall = {
            PaywallScreen(onClose = { router.pop() })
        },
        moderation = {
            ModerationScreen(
                onRecordClick = { router.push(ViFiRoute.ModerationDetail(it)) },
                onBack = { router.pop() },
            )
        },
        moderationDetail = { submissionId ->
            ModerationDetailScreen(
                submissionId = submissionId,
                onPreviewFiles = { router.push(ViFiRoute.SubmissionPreview(submissionId)) },
                onFinished = { router.pop() },
                onBack = { router.pop() },
            )
        },
        submissionPreview = { submissionId ->
            SubmissionPreviewScreen(submissionId = submissionId, onBack = { router.pop() })
        },
        notificationSettings = {
            NotificationSettingsScreen(onBack = { router.pop() })
        },
        notificationComposer = {
            NotificationComposerScreen(
                onBack = { router.pop() },
                onOpenHistory = { router.push(ViFiRoute.NotificationHistory) },
                // The location picker's rows open the flavor's picker full screen themselves.
                locationRows = { location, enabled, onLocationPicked ->
                    NotificationComposerLocationRows(location, enabled, onLocationPicked)
                },
            )
        },
        notificationHistory = {
            NotificationHistoryScreen(onBack = { router.pop() })
        },
    )
}

/** Drills into a list entry, or opens an exam that has files. */
private fun ViFiRouter.open(item: ArchiveItem) {
    val exam = item.exam
    when {
        item.level != ArchiveLevel.EXAM -> push(ViFiRoute.Browse(item.path))
        exam != null -> push(ViFiRoute.Exam(item.path, exam.kind))
    }
}

/** Warms the archive's offline cache once the signed-in user reaches it; cancelled on sign-out (ARCH-09). */
@Composable
private fun ArchivePrefetch(viewModel: ArchivePrefetchViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel) { viewModel.onArchiveShown() }
}

/**
 * Finishes paid single-exam unlocks whose server record did not complete earlier, now and on every return to the
 * foreground, while the signed-in archive is shown; a sign-out cancels it (iOS ProfileGate `recoverPendingUnlock`).
 */
@Composable
private fun PaidUnlockRecovery(viewModel: PaidUnlockRecoveryViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel) { viewModel.onArchiveShown() }
}

/** Opens the inbox for each tapped push ([DeepLinkViewModel]); an inbox already on top only takes the new highlight. */
@Composable
private fun PushTapDelivery(router: ViFiRouter, viewModel: DeepLinkViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel, router) {
        viewModel.inboxOpenings.collect { router.showInbox(it.notificationId) }
    }
}
