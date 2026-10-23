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
import com.salticusteam.vifi.feature.browse.BrowseScreen
import com.salticusteam.vifi.feature.examviewer.ExamViewerScreen
import com.salticusteam.vifi.feature.home.HomeScreen
import com.salticusteam.vifi.feature.inbox.InboxScreen
import com.salticusteam.vifi.feature.notifications.NotificationPrompt
import com.salticusteam.vifi.feature.login.LoginScreen
import com.salticusteam.vifi.feature.onboarding.OnboardingGate
import com.salticusteam.vifi.feature.profile.EditProfileScreen
import com.salticusteam.vifi.feature.update.AppUpdateGate

/**
 * The app's root composable (iOS RootView): gates the archive behind sign-in, then behind a completed profile
 * ([ProfileGate]), all beneath the required-update gate ([AppUpdateGate]). The lifecycle hooks (badge
 * clearing, disk-cache pruning) are layered here as their roadmap items land.
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

    ViFiNavHost(
        navController = navController,
        modifier = Modifier.fillMaxSize(),
        home = {
            HomeScreen(
                onItemClick = { router.open(it) },
                onRecentClick = { router.push(ViFiRoute.Exam(it.path, it.kind)) },
                // TODO(add-exam): push ViFiRoute.AddExam once the add-exam flow lands.
                onAddExamClick = {},
                onAccountClick = { router.push(ViFiRoute.Account) },
                onInboxClick = { router.push(ViFiRoute.Inbox) },
                onAboutClick = { router.push(ViFiRoute.About) },
            )
        },
        browse = { _ ->
            BrowseScreen(onItemClick = { router.open(it) }, onBack = { router.pop() })
        },
        exam = { path, kind ->
            ExamViewerScreen(path = path, routeKind = kind, onBack = { router.pop() })
        },
        account = {
            AccountScreen(
                onEditProfile = { router.push(ViFiRoute.EditProfile) },
                onAbout = { router.push(ViFiRoute.About) },
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

/** The process-wide [InboxDeepLink]; reached through a ViewModel so the composable needs no entry-point plumbing. */
@Composable
private fun rememberInboxDeepLink(viewModel: DeepLinkViewModel = hiltViewModel()): InboxDeepLink =
    remember(viewModel) { viewModel.inboxDeepLink }
