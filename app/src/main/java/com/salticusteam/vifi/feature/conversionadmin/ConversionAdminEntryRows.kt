package com.salticusteam.vifi.feature.conversionadmin

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.IconBadge

/**
 * The admin's "Yapılandırılmış sınavlar" entries on the account screen, next to Moderasyon (iOS `moderationSection`):
 * Hazır sınavlar, Başarısız işler, Toplu dönüştür and Ders eşleme. Shown to admins only (the caller checks the claim).
 */
@Composable
fun ConversionAdminEntryRows(
    enabled: Boolean,
    onReady: () -> Unit,
    onFailed: () -> Unit,
    onBulk: () -> Unit,
    onSubjects: () -> Unit,
) {
    EntryRow(R.string.conversion_admin_entry_ready, R.string.conversion_admin_entry_ready_subtitle, Icons.Filled.Verified, "account.conversionReady", enabled, onReady)
    EntryRow(R.string.conversion_admin_entry_failed, R.string.conversion_admin_entry_failed_subtitle, Icons.Filled.Warning, "account.conversionFailed", enabled, onFailed)
    EntryRow(R.string.conversion_admin_entry_bulk, R.string.conversion_admin_entry_bulk_subtitle, Icons.Filled.Layers, "account.conversionBulk", enabled, onBulk)
    EntryRow(R.string.conversion_admin_entry_subjects, R.string.conversion_admin_entry_subjects_subtitle, Icons.Filled.CallMerge, "account.subjectMapping", enabled, onSubjects)
}

@Composable
private fun EntryRow(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    icon: ImageVector,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = stringResource(titleRes), fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(stringResource(subtitleRes)) },
        leadingContent = { IconBadge(icon = icon) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag),
    )
    HorizontalDivider()
}
