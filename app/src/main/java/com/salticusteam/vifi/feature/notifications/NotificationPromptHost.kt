package com.salticusteam.vifi.feature.notifications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.core.navigation.RegisterModalPresence
import com.salticusteam.vifi.core.util.LogCategory
import kotlinx.coroutines.launch

/**
 * The notification flow's place in the archive (iOS `presentsNotificationPrompts()`): makes the first offer after the
 * tour and shows each queued run as a sheet. Renders nothing while no run is on screen.
 */
@Composable
fun NotificationPromptHost(viewModel: NotificationPromptViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel) { viewModel.onArchiveShown() }

    val flow by viewModel.flow.collectAsStateWithLifecycle()
    flow?.let { current ->
        // A new run is a new sheet, never the previous one's state.
        key(current.prompt.id) {
            NotificationPromptSheet(flow = current, viewModel = viewModel)
        }
    }
}

/**
 * Both steps in one sheet whose height follows the page. The permission page may be swiped or backed away (a "no");
 * the consent page is answered, and may be closed only after a failed save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationPromptSheet(flow: NotificationPermissionFlow, viewModel: NotificationPromptViewModel) {
    val state by flow.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    RegisterModalPresence()

    val allowsHiding = rememberUpdatedState(state.canDismiss || state.isFinished)
    val confirmValueChange = remember { { value: SheetValue -> value != SheetValue.Hidden || allowsHiding.value } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirmValueChange)

    LaunchedEffect(flow) { viewModel.onShown() }
    LaunchedEffect(state.isFinished) {
        if (!state.isFinished) return@LaunchedEffect
        try {
            sheetState.hide()
        } finally {
            viewModel.onSheetClosed()
        }
    }

    ModalBottomSheet(
        onDismissRequest = viewModel::onSheetClosed,
        sheetState = sheetState,
        dragHandle = if (state.canDismiss) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
        // The sheet fixes its own Back handling when it opens (Material3 1.3, Android 13+), so a page that allows Back
        // would leave it on for the consent page too. Its own is kept off and Back follows the page here instead.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(enabled = state.canDismiss && !state.isFinished) {
            scope.launch { sheetState.hide() }.invokeOnCompletion { viewModel.onSheetClosed() }
        }
        NotificationPromptContent(
            state = state,
            source = flow.prompt.source,
            onEnable = viewModel::onEnable,
            onOpenSettings = {
                viewModel.onOpenSettings()
                context.openAppNotificationSettings()
            },
            onNotNow = viewModel::onNotNow,
            onAnswer = viewModel::onAnswer,
        )
    }
}

/** The app's notification settings, where a permission the system no longer asks for is turned on. */
internal fun Context.openAppNotificationSettings() {
    val notificationPage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        null
    }
    // Before Android 8 (or on a device without that page) the app's details page has the switch.
    val detailsPage = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    for (intent in listOfNotNull(notificationPage, detailsPage)) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            LogCategory.APP.log.w(e, "No settings page for the app's notifications")
        }
    }
}
