package com.salticusteam.vifi

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.salticusteam.vifi.core.appcheck.appCheckProviderFactory
import com.salticusteam.vifi.core.data.firebase.FirebaseDatabaseSetup

/**
 * Live backend start-up. Installs the App Check provider before any Firebase call is made:
 * Storage/RTDB/Auth enforce App Check on the shared vifi-831a8 project, so requests
 * without a valid token are rejected (403 / "invalid AppCheck token").
 */
internal object BackendStartup {

    fun start(app: Application) {
        // FirebaseInitProvider already initialised the default app; this is a no-op guard for
        // processes where it didn't run (e.g. instrumentation without the provider).
        FirebaseApp.initializeApp(app)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
        // Before any Activity runs: persistence can only be enabled ahead of the first RTDB use.
        FirebaseDatabaseSetup.configure()
    }
}
