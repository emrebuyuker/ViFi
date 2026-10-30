package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import android.os.Build
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.navigation.RegisterModalPresence

/**
 * Shows the flavor's [LocationPicker] ([LocalLocationPicker]) full screen over the composer (iOS `fullScreenCover`),
 * counted as an app modal so pushes and prompts wait for it. [initial] is the place being changed, if any. A valid
 * place goes to [onPick]; the close "✕" and Back call [onDismiss]. The caller removes the dialog in both cases.
 */
@Composable
fun LocationPickerDialog(
    initial: NotificationLocation?,
    onPick: (NotificationLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = LocalLocationPicker.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            // Edge-to-edge only where FullScreenDialogWindow also lifts the window out of the bars (as the tour).
            decorFitsSystemWindows = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM,
        ),
    ) {
        RegisterModalPresence()
        FullScreenDialogWindow()
        picker.Content(initial = initial, onPick = onPick, onCancel = onDismiss)
    }
}

/**
 * Lets the picker's dialog window cover the whole screen, system bars included, the way the onboarding tour does
 * (QA-01): from Android 15 Compose 1.7 sizes a dialog to the full display but still fits its window between the bars,
 * which would slide the details card under the navigation bar. Bar icons follow the background so they stay readable.
 */
@Composable
private fun FullScreenDialogWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(window, lightBackground) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.attributes = window.attributes.apply {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            // Nothing is left to dim; a rounding pixel at the edge would show as a grey line.
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
        // The window goes away with the dialog; the activity's own bar appearance applies again.
        onDispose {}
    }
}
