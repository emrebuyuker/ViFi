package com.salticusteam.vifi

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.salticusteam.vifi.core.notifications.ViFiMessagingService
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs instrumented tests on HiltTestApplication so @HiltAndroidTest classes can swap modules.
 *
 * The app's FCM service is switched off for the run. On a device that never had an FCM token the live build's Firebase
 * fetches one at start and hands it to that service, which cannot start under HiltTestApplication (no Hilt component
 * outside a Hilt test) and crashes the run. Switched off from inside the app, the process stays and the hand-over is
 * dropped; FCM still stores the token and does not hand it over again.
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        // Before the app's content providers start Firebase.
        switchMessagingService(on = false)
        super.onCreate(arguments)
    }

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun finish(resultCode: Int, results: Bundle?) {
        switchMessagingService(on = true)
        super.finish(resultCode, results)
    }

    private fun switchMessagingService(on: Boolean) {
        targetContext.packageManager.setComponentEnabledSetting(
            ComponentName(targetContext, ViFiMessagingService::class.java),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
