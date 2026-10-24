package com.salticusteam.vifi

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.salticusteam.vifi.app.AppLifecycleHooks
import com.salticusteam.vifi.core.notifications.NotificationChannels
import com.salticusteam.vifi.core.util.Logging
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * App entry point. Backend start-up is flavor-specific ([BackendStartup]): `live` wires Firebase
 * (App Check first, then RTDB persistence); `mock` touches no Firebase at all (iOS `-ViFiMockData`).
 */
@HiltAndroidApp
class VifiApplication : Application() {

    @Inject lateinit var lifecycleHooks: AppLifecycleHooks

    override fun onCreate() {
        super.onCreate()
        Logging.install(debug = BuildConfig.DEBUG)
        BackendStartup.start(this)
        // The channel must exist before the system shows a background push (manifest default channel id).
        NotificationChannels.ensure(this)
        // Badge clearing and disk-cache pruning on every foreground/background transition (N9, iOS scenePhase).
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleHooks)
    }
}
