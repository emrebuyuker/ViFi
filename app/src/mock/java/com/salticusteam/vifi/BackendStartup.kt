package com.salticusteam.vifi

import android.app.Application

/**
 * Mock backend start-up: nothing to do. Firebase is never initialised in this flavor
 * (FirebaseInitProvider is removed from the merged manifest), matching iOS `-ViFiMockData`.
 */
internal object BackendStartup {

    // Same signature as the live flavor's BackendStartup, which needs the Application.
    @Suppress("UNUSED_PARAMETER")
    fun start(app: Application) = Unit
}
