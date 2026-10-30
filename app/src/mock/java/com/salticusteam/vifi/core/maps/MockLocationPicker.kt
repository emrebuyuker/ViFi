package com.salticusteam.vifi.core.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPicker
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPickerTopBar
import javax.inject.Inject

/**
 * The mock flavor's picker (iOS `MockLocationPicker`): no Google SDK is touched, no key is needed and the location is
 * never read. It offers one fixed place (or the one being changed), so the composer's location flow can be exercised
 * end to end in UI tests.
 */
class MockLocationPicker @Inject constructor() : LocationPicker {

    @Composable
    override fun Content(
        initial: NotificationLocation?,
        onPick: (NotificationLocation) -> Unit,
        onCancel: () -> Unit,
    ) {
        MockLocationPickerContent(location = initial ?: SAMPLE_LOCATION, onPick = onPick, onCancel = onCancel)
    }

    companion object {
        /** What "Bu konumu seç" returns when nothing is being changed. */
        val SAMPLE_LOCATION = NotificationLocation(
            latitude = 40.9903,
            longitude = 29.0290,
            name = "ViFi Öğrenci Kafesi",
            address = "Caferağa, Kadıköy, İstanbul",
            placeId = null,
        )
    }
}

/** "Örnek harita": the place, its address and "Bu konumu seç"; the close "✕" backs out like the real picker. */
@Composable
internal fun MockLocationPickerContent(
    location: NotificationLocation,
    onPick: (NotificationLocation) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LocationPickerTopBar(onClose = onCancel, closeDescription = stringResource(R.string.location_picker_cancel))
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(text = stringResource(R.string.location_picker_mock_map), style = MaterialTheme.typography.titleMedium)
            Text(
                text = location.name,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("locationPicker.mock.name"),
            )
            location.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            // Primary button content is onPrimary (black): white on the brand orange falls below 3:1.
            Button(
                onClick = { onPick(location) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("locationPicker.confirm"),
            ) {
                Text(stringResource(R.string.location_picker_confirm), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MockLocationPickerPreview() {
    ViFiTheme {
        MockLocationPickerContent(location = MockLocationPicker.SAMPLE_LOCATION, onPick = {}, onCancel = {})
    }
}
