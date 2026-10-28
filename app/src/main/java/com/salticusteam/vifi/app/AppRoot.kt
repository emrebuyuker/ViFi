package com.salticusteam.vifi.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.salticusteam.vifi.core.notifications.InboxDeepLink
import com.salticusteam.vifi.feature.about.AboutScreen
import com.salticusteam.vifi.feature.account.AccountScreen
import com.salticusteam.vifi.feature.addexam.AddExamScreen
import com.salticusteam.vifi.feature.browse.BrowseScreen
import com.salticusteam.vifi.feature.examviewer.ExamViewerScreen
import com.salticusteam.vifi.feature.home.HomeScreen
import com.salticusteam.vifi.feature.inbox.InboxScreen
import com.salticusteam.vifi.feature.notifications.NotificationPrompt
import com.salticusteam.vifi.feature.login.LoginScreen
import com.salticusteam.vifi.feature.moderation.ModerationDetailScreen
import com.salticusteam.vifi.feature.moderation.ModerationScreen
import com.salticusteam.vifi.feature.moderation.SubmissionPreviewScreen
import com.salticusteam.vifi.feature.onboarding.OnboardingGate
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
private fun ArchiveHost(user: AuthUser, profile: UserProfile) {
    val navController = rememberNavController()
    val router = rememberViFiRouter(navController)

    // A notification tap opens the inbox; a cold-start tap waits here until the signed-in host exists.
    val inboxDeepLink = rememberInboxDeepLink()
    val inboxRequested by inboxDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(inboxRequested) {
        if (inboxRequested) {
            router.push(ViFiRoute.Inbox)
            inboxDeepLink.consume()
        }
    }
    NotificationPrompt()
    ArchivePrefetch()

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
                onInboxClick = { router.push(ViFiRoute.Inbox) },
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
                onBack = { router.pop() },
            )
        },
        editProfile = {
            EditProfileScreen(user = user, existing = profile, onBack = { router.pop() })
        },
        inbox = {
            InboxScreen(onBack = { router.pop() })
        },
        about = {
            AboutScreen(onBack = { router.pop() })
        },
        addExam = { context ->
            AddExamScreen(ownerId = user.id, profile = profile, archiveContext = context, onClose = { router.pop() })
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

/** The process-wide [InboxDeepLink]; reached through a ViewModel so the composable needs no entry-point plumbing. */
@Composable
private fun rememberInboxDeepLink(viewModel: DeepLinkViewModel = hiltViewModel()): InboxDeepLink =
    remember(viewModel) { viewModel.inboxDeepLink }
