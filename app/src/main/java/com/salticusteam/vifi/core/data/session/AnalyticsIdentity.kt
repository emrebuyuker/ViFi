package com.salticusteam.vifi.core.data.session

import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.AnalyticsUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Identifies the signed-in user for analytics (iOS ProfileGate's `identifyUser()`): the user id and admin claim as
 * soon as a user signs in, enriched with the profile's segment properties once the profile is loaded and again
 * whenever it changes. Clearing the identity on sign-out is [SessionTeardown]'s job.
 */
@Singleton
class AnalyticsIdentity @Inject constructor(
    private val sessionStore: SessionStore,
    private val profileStore: ProfileStore,
    private val authService: AuthService,
    private val analytics: AnalyticsTracker,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // Internal state
    private var job: Job? = null

    /** Starts following the session for the process lifetime; later calls do nothing. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            sessionStore.state
                .map { (it as? SessionState.SignedIn)?.user?.id }
                .distinctUntilChanged()
                // A newer user (or a sign-out) cancels the previous user's identification.
                .collectLatest { uid -> if (uid != null) identify(uid) }
        }
    }

    // Helpers
    private suspend fun identify(uid: String) {
        // Read once per user, like iOS; the claim only changes out-of-band.
        val isAdmin = authService.isCurrentUserAdmin()
        profileStore.state
            .map { state -> AnalyticsUser.from(uid, (state as? ProfileState.Present)?.profile, isAdmin) }
            .distinctUntilChanged()
            .collect { user ->
                // The teardown's profile reset can reach this collector before the sign-out cancels it; never
                // re-identify a user who is no longer signed in.
                if (sessionStore.user?.id == uid) analytics.setUser(user)
            }
    }
}
