package com.salticusteam.vifi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.util.LaunchArgs
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Compose host (iOS ViFiApp WindowGroup). Edge-to-edge; screens handle insets through Scaffold.
 *
 * Launcher in the mock flavor today; the live flavor switches over once sign-in and the live archive land
 * and the legacy Activities retire (FM-23).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Before any ViewModel or fake reads a scenario flag (debug builds only).
        LaunchArgs.update(intent?.extras)
        setContent {
            ViFiTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
