package com.salticusteam.vifi.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.AuthUser

/**
 * The blocking screen shown after sign-in while the user has no profile (iOS ProfileCompletionView): it must be
 * filled in before the archive opens. Saving updates the profile store, which opens the gate; signing out is
 * always possible, so nobody is trapped.
 */
@Composable
fun ProfileCompletionScreen(
    user: AuthUser,
    // Keyed by uid: the gate lives outside the NavHost (activity-scoped models), so each user gets their own form.
    viewModel: ProfileFormViewModel = hiltViewModel<ProfileFormViewModel, ProfileFormViewModel.Factory>(
        key = "profileCompletion-${user.id}",
    ) { factory -> factory.create(user, existing = null) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    ProfileFormContent(
        state = state,
        title = stringResource(R.string.profile_completion_title),
        onNameChange = viewModel::onNameChange,
        onSurnameChange = viewModel::onSurnameChange,
        onPickerOpen = viewModel::onPickerOpen,
        onPickerDismiss = viewModel::onPickerDismiss,
        onCatalogSelected = viewModel::onCatalogSelected,
        onGenderSelected = viewModel::onGenderSelected,
        onSave = viewModel::save,
        testTagPrefix = "profile",
        onSignOut = viewModel::signOut,
    )
}
