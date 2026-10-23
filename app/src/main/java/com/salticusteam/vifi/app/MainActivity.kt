package com.salticusteam.vifi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Phone verification's reCAPTCHA fallback needs the host Activity; held weakly, detached on destroy.
        activityHolder.attach(this)
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
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
