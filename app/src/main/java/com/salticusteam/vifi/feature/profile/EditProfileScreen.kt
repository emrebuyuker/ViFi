package com.salticusteam.vifi.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.UserProfile

/**
 * Edits the signed-in user's profile (iOS EditProfileView). A navigation destination: saving updates the shared
 * profile and goes back.
 */
@Composable
fun EditProfileScreen(
    user: AuthUser,
    existing: UserProfile?,
    onBack: () -> Unit,
    viewModel: ProfileFormViewModel = hiltViewModel<ProfileFormViewModel, ProfileFormViewModel.Factory> { factory ->
        factory.create(user, existing)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ProfileFormEvent.Saved -> onBack()
            }
        }
    }

    ProfileFormContent(
        state = state,
        title = stringResource(R.string.profile_edit_title),
        onNameChange = viewModel::onNameChange,
        onSurnameChange = viewModel::onSurnameChange,
        onPickerOpen = viewModel::onPickerOpen,
        onPickerDismiss = viewModel::onPickerDismiss,
        onCatalogSelected = viewModel::onCatalogSelected,
        onGenderSelected = viewModel::onGenderSelected,
        onSave = viewModel::save,
        testTagPrefix = "editProfile",
        onBack = onBack,
    )
}
