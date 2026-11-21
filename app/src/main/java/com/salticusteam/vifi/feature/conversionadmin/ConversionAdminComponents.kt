package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider

// Small shared pieces of the admin conversion screens: the provider picker (K9: Claude or Gemini at every AI action,
// never a default), the monthly spend gauge (iOS `ConversionAdminComponents`).

/**
 * Asks which AI provider to use ("Claude ile ilerle" / "Gemini ile ilerle") before an action that runs a model, then
 * calls [onPick] with the choice (iOS `aiProviderDialog`). There is no default and no "remember my choice".
 */
@Composable
fun AiProviderDialog(
    title: String,
    onPick: (AIProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (provider in AIProvider.entries) {
                    Button(
                        onClick = { onPick(provider) },
                        modifier = Modifier.fillMaxWidth().testTag("ai.provider.${provider.raw}"),
                    ) {
                        Text(stringResource(R.string.conversion_admin_provider_proceed, stringResource(provider.nameRes)))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("ai.provider.cancel")) {
                Text(stringResource(R.string.conversion_admin_cancel))
            }
        },
    )
}

/** Two side-by-side buttons for picking a provider inline; nothing is selected at first (iOS `AIProviderChoice`). */
@Composable
fun AiProviderChoice(
    selection: AIProvider?,
    onSelect: (AIProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (provider in AIProvider.entries) {
            val isSelected = selection == provider
            OutlinedButton(
                onClick = { onSelect(provider) },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.RadioButton
                        selected = isSelected
                    }
                    .testTag("bulk.provider.${provider.raw}"),
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                )
                Text(text = stringResource(provider.nameRes), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** This month's AI spend against the $50 cap (iOS `AIUsageGauge`); nothing while the figure is unknown. */
@Composable
fun AiUsageGauge(usage: AIMonthlyUsage?, modifier: Modifier = Modifier) {
    if (usage == null) return
    val summary = stringResource(
        R.string.conversion_admin_usage_value,
        usage.costUsd.toUsd(),
        AIMonthlyUsage.CAP_USD.toUsd(),
    )
    val title = stringResource(R.string.conversion_admin_usage_title)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$title: $summary" }
            .testTag("ai.usage"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { usage.fraction.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = if (usage.fraction >= NEAR_CAP) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

/** The gauge turns red from this share of the cap (iOS `0.9`). */
private const val NEAR_CAP = 0.9
