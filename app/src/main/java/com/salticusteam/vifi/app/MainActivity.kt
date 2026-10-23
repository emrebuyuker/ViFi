package com.salticusteam.vifi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.notifications.InboxDeepLink
import com.salticusteam.vifi.core.notifications.NotificationPermissionRequester
import com.salticusteam.vifi.core.util.ForegroundActivityHolder
import com.salticusteam.vifi.core.util.LaunchArgs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single Compose host and launcher (iOS ViFiApp WindowGroup). Edge-to-edge; screens handle insets
 * through Scaffold.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var activityHolder: ForegroundActivityHolder
    @Inject lateinit var permissionRequester: NotificationPermissionRequester
    @Inject lateinit var inboxDeepLink: InboxDeepLink

    // Registered before STARTED (a result launcher cannot be registered later); the prompt itself is started
    // through [permissionRequester] by the notification service.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRequester.onResult(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Phone verification's reCAPTCHA fallback needs the host Activity; held weakly, detached on destroy.
        activityHolder.attach(this)
        permissionRequester.attach(notificationPermission)
        // A notification tap that launched the app opens the inbox once the signed-in host is up.
        if (savedInstanceState == null) inboxDeepLink.handle(intent?.extras)
        // Before any ViewModel or fake reads a scenario flag (debug builds only).
        LaunchArgs.update(intent?.extras)
        setContent {
            ViFiTheme {
                AppRoot()
            }
        }
    }

    override fun onDestroy() {
        activityHolder.detach(this)
        permissionRequester.detach(notificationPermission)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inboxDeepLink.handle(intent.extras)
    }
}
