package com.salticusteam.vifi

import android.app.Application
import com.salticusteam.vifi.core.notifications.NotificationChannels
import com.salticusteam.vifi.core.util.Logging
import dagger.hilt.android.HiltAndroidApp

/**
 * App entry point. Backend start-up is flavor-specific ([BackendStartup]): `live` wires Firebase
 * (App Check first, then RTDB persistence); `mock` touches no Firebase at all (iOS `-ViFiMockData`).
 */
@HiltAndroidApp
class VifiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logging.install(debug = BuildConfig.DEBUG)
        BackendStartup.start(this)
        // The channel must exist before the system shows a background push (manifest default channel id).
        NotificationChannels.ensure(this)
    }
}
