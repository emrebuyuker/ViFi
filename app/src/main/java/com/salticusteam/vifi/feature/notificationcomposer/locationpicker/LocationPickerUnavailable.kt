package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme

/**
 * Shown instead of the map when the build has no Google Maps key (iOS `LocationPickerUnavailableView`): a map
 * created without one fails, so neither the Google SDKs nor the location are touched. "Kapat" backs out.
 */
@Composable
fun LocationPickerUnavailable(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LocationPickerTopBar(onClose = onClose, closeDescription = stringResource(R.string.location_picker_close))
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("locationPicker.unavailable"),
        ) {
            EmptyView(
                title = stringResource(R.string.location_picker_unavailable_title),
                icon = Icons.Outlined.Map,
                message = stringResource(R.string.location_picker_unavailable_message),
            )
        }
    }
}

/**
 * The picker's bar: "Konum Seç" and, as for any full-screen modal, a close "✕" that backs out without choosing
 * ([closeDescription] is what TalkBack reads: "İptal" on the map, "Kapat" when there is none).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationPickerTopBar(onClose: () -> Unit, closeDescription: String) {
    TopAppBar(
        title = { Text(stringResource(R.string.location_picker_title), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.testTag("locationPicker.cancel")) {
                Icon(Icons.Filled.Close, contentDescription = closeDescription)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Preview(showBackground = true)
@Composable
private fun LocationPickerUnavailablePreview() {
    ViFiTheme {
        LocationPickerUnavailable(onClose = {})
    }
}
