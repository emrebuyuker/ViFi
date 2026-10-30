package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationLocation

/**
 * The composer's rows under "Eylem" while the button opens a place (iOS `NotificationComposerLocationRows`): the
 * place chosen so far, "Konum seç" / "Değiştir" to pick it on the map ([LocationPickerDialog], starting on
 * [location]), and what a location notification always is (a campaign). A picked place goes to [onLocationPicked];
 * backing out of the picker keeps [location] as it was. While a send is under way the composer passes
 * [enabled] `false` and the place cannot be changed (iOS `.disabled(isWorking)`).
 *
 * The parameters follow the composer's `ComposerLocationRows` slot (Hat D), so `NIT-01` wires it in one line.
 */
@Composable
fun NotificationComposerLocationRows(
    location: NotificationLocation?,
    enabled: Boolean,
    onLocationPicked: (NotificationLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPicking by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (location != null) {
            LocationSummary(location)
        }

        TextButton(
            onClick = { isPicking = true },
            enabled = enabled,
            modifier = Modifier.testTag("notificationComposer.location.pick"),
        ) {
            Icon(Icons.Filled.EditLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            val title = if (location == null) {
                R.string.location_picker_composer_pick
            } else {
                R.string.location_picker_composer_change
            }
            Text(text = stringResource(title), modifier = Modifier.padding(start = 8.dp))
        }

        Text(
            text = stringResource(R.string.location_picker_composer_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("notificationComposer.location.note"),
        )
    }

    if (isPicking) {
        LocationPickerDialog(
            initial = location,
            onPick = { picked ->
                onLocationPicked(picked)
                isPicking = false
            },
            onDismiss = { isPicking = false },
        )
    }
}

@Composable
private fun LocationSummary(location: NotificationLocation) {
    val address = location.address?.takeIf { it.isNotEmpty() }
    val place = if (address != null) {
        stringResource(R.string.location_picker_composer_place, location.name, address)
    } else {
        location.name
    }
    val summary = stringResource(R.string.location_picker_composer_summary, place)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // One spoken element: "Seçilen konum: <ad, adres>".
            .clearAndSetSemantics {
                contentDescription = summary
                testTag = "notificationComposer.location.summary"
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = location.name, style = MaterialTheme.typography.titleSmall)
        if (address != null) {
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationComposerLocationRowsPreview() {
    ViFiTheme {
        NotificationComposerLocationRows(
            location = NotificationLocation(40.9903, 29.0290, "ViFi Öğrenci Kafesi", "Caferağa, Kadıköy, İstanbul"),
            enabled = true,
            onLocationPicked = {},
        )
    }
}
