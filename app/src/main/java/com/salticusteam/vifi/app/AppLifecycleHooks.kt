package com.salticusteam.vifi.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.salticusteam.vifi.core.data.repository.RemoteFileLoader
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.notifications.NotificationBadge
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app-wide foreground/background reactions (iOS RootView's `scenePhase` handling and AppDelegate's launch
 * prune), observed on the process lifecycle so they fire once per app transition, not per screen:
 * - the app comes to the foreground (cold start included): clear the icon badge (N9);
 * - the first foreground of the process: trim the downloaded-file cache back within budget;
 * - the app goes to the background: trim it again while the user is away.
 *
 * Dependencies are reached lazily: VifiApplication registers this observer before any screen exists, and their
 * graph (in the mock flavor, with its launch-argument scenarios) must only be built once the app runs.
 */
@Singleton
class AppLifecycleHooks @Inject constructor(
    private val badge: dagger.Lazy<NotificationBadge>,
    private val fileLoader: dagger.Lazy<RemoteFileLoader>,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    // Internal state
    private var hasPrunedAtLaunch = false

    // Intents
    override fun onStart(owner: LifecycleOwner) {
        badge.get().clear()
        if (!hasPrunedAtLaunch) {
            hasPrunedAtLaunch = true
            pruneDiskCache()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        pruneDiskCache()
    }

    // Helpers
    private fun pruneDiskCache() {
        scope.launch {
            try {
                fileLoader.get().pruneDiskCache()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogCategory.FILES.log.e(e, "Pruning the file cache failed")
            }
        }
    }
}
