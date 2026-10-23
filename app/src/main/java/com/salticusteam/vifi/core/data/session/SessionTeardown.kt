package com.salticusteam.vifi.core.data.session

import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The single app-wide reaction to the session ending (iOS RootView's `onChange(of: session.state)`): whenever the
 * user signs out or their account is deleted, the next user starts with a clean profile read, without the
 * previous user's recently viewed exams and without the previous analytics identity.
 *
 * The navigation half of iOS `router.popToRoot()` needs no code here: `AppRoot` drops the whole archive stack
 * on sign-out and keys the next one by user. Downloaded files are kept; only account deletion purges them
 * (`LocalDataCleaner`). Wallet, unlocks and announcements resets join here when their stores land.
 */
@Singleton
class SessionTeardown @Inject constructor(
    private val sessionStore: SessionStore,
    private val profileStore: ProfileStore,
    private val recents: RecentExamsRepository,
    private val analytics: AnalyticsTracker,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // Internal state
    private var job: Job? = null

    /** Starts following the session for the process lifetime; later calls do nothing. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            sessionStore.state.collect { state ->
                if (state == SessionState.SignedOut) tearDown()
            }
        }
    }

    // Helpers
    private suspend fun tearDown() {
        profileStore.reset()
        analytics.setUser(null)
        try {
            recents.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.e(e, "Clearing recent exams on sign-out failed")
        }
    }
}
