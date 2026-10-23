package com.salticusteam.vifi.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.feature.profile.ProfileCompletionScreen

/**
 * The second gate after sign-in (iOS ProfileGate): the archive opens only once [user] has a completed profile.
 * Loads the profile when the user appears or changes; a failed read offers a retry instead of forcing the user
 * to enter the profile again.
 */
@Composable
fun ProfileGate(
    user: AuthUser,
    viewModel: ProfileGateViewModel = hiltViewModel(),
    content: @Composable (profile: UserProfile) -> Unit,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    LaunchedEffect(user.id) {
        viewModel.load(user.id)
    }

    when (val state = profile) {
        ProfileState.Loading -> LoadingView(modifier = Modifier.testTag("profileGate.loading"))
        ProfileState.Absent -> ProfileCompletionScreen(user = user)
        is ProfileState.Present -> content(state.profile)
        is ProfileState.Failed -> ErrorView(
            message = state.message,
            onRetry = { viewModel.retry(user.id) },
        )
    }
}
