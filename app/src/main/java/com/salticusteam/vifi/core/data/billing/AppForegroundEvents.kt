package com.salticusteam.vifi.core.data.billing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Emits each time the app comes to the foreground, so state Google Play does not push (a subscription that expired,
 * was refunded or bought on another device) is read again then (iOS `scenePhase == .active`).
 */
interface AppForegroundEvents {
    /** One event per foreground transition; also one at once when collected while the app is in the foreground. */
    val foregrounds: Flow<Unit>
}

/** The process lifecycle's ON_START, observed only while collected. */
class ProcessForegroundEvents @Inject constructor() : AppForegroundEvents {

    override val foregrounds: Flow<Unit> = callbackFlow {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) trySend(Unit)
        }
        lifecycle.addObserver(observer)
        awaitClose { lifecycle.removeObserver(observer) }
    }.flowOn(Dispatchers.Main.immediate) // Lifecycle observers are added and removed on the main thread.
}
