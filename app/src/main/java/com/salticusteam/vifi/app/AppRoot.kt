package com.salticusteam.vifi.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.core.navigation.ViFiNavHost
import com.salticusteam.vifi.core.navigation.ViFiRoute
import com.salticusteam.vifi.core.navigation.ViFiRouter
import com.salticusteam.vifi.core.navigation.rememberViFiRouter
import com.salticusteam.vifi.feature.browse.BrowseScreen
import com.salticusteam.vifi.feature.login.LoginScreen
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
            ProfileGate(user = state.user) { profile -> ArchiveHost(user = state.user, profile = profile) }
        }
    }
}

/** The archive navigation stack, shown once the user is signed in and has a profile. */
@Composable
private fun ArchiveHost(user: AuthUser, profile: UserProfile) {
    val navController = rememberNavController()
    val router = rememberViFiRouter(navController)

    ViFiNavHost(
        navController = navController,
        modifier = Modifier.fillMaxSize(),
        home = {
            BrowseScreen(onItemClick = { router.open(it) }, onBack = null)
        },
        browse = { _ ->
            BrowseScreen(onItemClick = { router.open(it) }, onBack = { router.pop() })
        },
        exam = { path, kind ->
            // Placeholder until the exam viewers (VIEW-01/02) land.
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { ViFiTopAppBar(title = path.displayName.orEmpty(), onBack = { router.pop() }) },
            ) { padding ->
                EmptyView(
                    title = path.displayName.orEmpty(),
                    icon = kind.icon,
                    message = stringResource(R.string.exam_viewer_coming_soon),
                    modifier = Modifier.padding(padding),
                )
            }
        },
        editProfile = {
            EditProfileScreen(user = user, existing = profile, onBack = { router.pop() })
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
