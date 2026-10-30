package com.salticusteam.vifi.feature.inbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.navigation.RegisterModalPresence
import com.salticusteam.vifi.core.util.LogCategory

/**
 * "Konuma Git" (iOS `LocationChoiceSheet`, NDEC-03): the place's name (and address), then Google Maps, any other
 * maps app or "Vazgeç". Choosing a map closes the sheet; "Vazgeç" closes it and keeps the inbox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationChoiceSheet(
    location: NotificationLocation,
    onGoogleMaps: () -> Unit,
    onOtherMapsApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    RegisterModalPresence()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("inbox.location.sheet"),
    ) {
        LocationChoiceContent(location, onGoogleMaps, onOtherMapsApp, onDismiss)
    }
}

@Composable
internal fun LocationChoiceContent(
    location: NotificationLocation,
    onGoogleMaps: () -> Unit,
    onOtherMapsApp: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.inbox_location_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = listOfNotNull(location.name, location.address?.trim()?.takeIf { it.isNotEmpty() }).joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onGoogleMaps, modifier = Modifier.fillMaxWidth().testTag("inbox.location.google")) {
            ChoiceLabel(Icons.Filled.Map, stringResource(R.string.inbox_location_google))
        }
        OutlinedButton(onClick = onOtherMapsApp, modifier = Modifier.fillMaxWidth().testTag("inbox.location.other")) {
            ChoiceLabel(Icons.Outlined.Map, stringResource(R.string.inbox_location_other))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().testTag("inbox.location.cancel")) {
            Text(stringResource(R.string.inbox_location_cancel))
        }
    }
}

@Composable
private fun ChoiceLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text = text, fontWeight = FontWeight.SemiBold)
}

/** The Google Maps app when it is installed, otherwise the same URL in the browser. */
internal fun Context.openInGoogleMaps(location: NotificationLocation) {
    val url = MapsLinks.googleMaps(location)
    val inApp = Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(MapsLinks.GOOGLE_MAPS_PACKAGE)
    try {
        startActivity(inApp)
    } catch (e: ActivityNotFoundException) {
        openInBrowser(url)
    }
}

/** The system chooser over the maps apps that take a `geo:` link; none at all falls back to Google Maps on the web. */
internal fun Context.openInOtherMapsApp(location: NotificationLocation) {
    val geo = Intent(Intent.ACTION_VIEW, MapsLinks.geo(location).toUri())
    if (packageManager.handlers(geo).isEmpty()) {
        openInBrowser(MapsLinks.googleMaps(location))
        return
    }
    try {
        startActivity(Intent.createChooser(geo, null))
    } catch (e: ActivityNotFoundException) {
        openInBrowser(MapsLinks.googleMaps(location))
    }
}

private fun Context.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (e: ActivityNotFoundException) {
        LogCategory.APP.log.w(e, "No app can open the place")
    }
}

// The manifest's <queries> makes the geo: handlers visible on Android 11+.
private fun PackageManager.handlers(intent: Intent) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, 0)
    }
