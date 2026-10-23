package com.salticusteam.vifi.core.notifications

import androidx.activity.result.ActivityResultLauncher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the POST_NOTIFICATIONS runtime prompt to a suspend call. The host Activity registers the system
 * launcher in `onCreate` (a result launcher can only be registered before STARTED) and attaches it here; the
 * notification service calls [request] without ever seeing the Activity.
 */
@Singleton
class NotificationPermissionRequester @Inject constructor() {

    @Volatile
    private var launcher: ActivityResultLauncher<String>? = null
    private var pending: CompletableDeferred<Boolean>? = null

    fun attach(launcher: ActivityResultLauncher<String>) {
        this.launcher = launcher
    }

    fun detach(launcher: ActivityResultLauncher<String>) {
        if (this.launcher === launcher) this.launcher = null
    }

    /** Forwards the system dialog's outcome. */
    fun onResult(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }

    /** Shows the system prompt for [permission]; `false` when no Activity is attached or it was denied. */
    suspend fun request(permission: String): Boolean {
        val current = launcher ?: return false
        val deferred = CompletableDeferred<Boolean>().also { pending = it }
        current.launch(permission)
        return deferred.await()
    }
}
