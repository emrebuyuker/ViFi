package com.salticusteam.vifi

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.salticusteam.vifi.core.appcheck.appCheckProviderFactory

/**
 * App entry point. Installs the App Check provider before any Firebase call is made:
 * Storage/RTDB/Auth enforce App Check on the shared vifi-831a8 project, so requests
 * without a valid token are rejected (403 / "invalid AppCheck token").
 */
class VifiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // FirebaseInitProvider already initialised the default app; this is a no-op guard for
        // processes where it didn't run (e.g. instrumentation without the provider).
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
