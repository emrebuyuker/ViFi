package com.salticusteam.vifi.app

import androidx.compose.runtime.Composable
import com.salticusteam.vifi.core.model.AuthUser

/**
 * The second gate after sign-in (iOS ProfileGate): the archive opens only once [user] has a completed profile.
 *
 * Placeholder hook: it shows [content] straight away until the profile area (PROFILE-GATE) replaces this body
 * with the profile check and completion form.
 */
@Composable
fun ProfileGate(
    user: AuthUser,
    content: @Composable () -> Unit,
) {
    content()
}
