package com.salticusteam.vifi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * App entry point. Backend start-up is flavor-specific ([BackendStartup]): `live` wires Firebase
 * (App Check first, then RTDB persistence); `mock` touches no Firebase at all (iOS `-ViFiMockData`).
 */
@HiltAndroidApp
class VifiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        BackendStartup.start(this)
    }
}
