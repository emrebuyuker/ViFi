package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.BulkConversionScope

/**
 * Admin bulk conversion (K12b; iOS `BulkConversionView`): pick a course or a whole department, pick the AI provider,
 * read the dry-run estimate next to the month's $50 gauge, and confirm to queue the unconverted exams.
 */
@Composable
fun BulkConversionScreen(
    onBack: () -> Unit,
    viewModel: BulkConversionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BulkConversionContent(
        state = state,
        onScopeSelected = viewModel::onScopeSelected,
        onProviderSelected = viewModel::onProviderSelected,
        onEstimate = viewModel::estimate,
        onConfirm = viewModel::confirm,
        onBack = onBack,
    )
}

/** Stateless bulk conversion content; previews and UI tests drive this. */
@Composable
fun BulkConversionContent(
    state: BulkConversionUiState,
    onScopeSelected: (BulkConversionScope, String) -> Unit,
    onProviderSelected: (AIProvider) -> Unit,
    onEstimate: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPickingScope by rememberSaveable { mutableStateOf(false) }
    var isConfirming by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.conversion_admin_bulk_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("bulk.list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "usage") { AiUsageGauge(state.usage) }
            item(key = "scope") { ScopeRow(state.scopeName) { isPickingScope = true } }
            item(key = "provider") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle(stringResource(R.string.conversion_admin_bulk_model))
                    AiProviderChoice(selection = state.provider, onSelect = onProviderSelected)
                    Caption(stringResource(R.string.conversion_admin_bulk_model_footer))
                }
            }
            item(key = "estimate") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onEstimate,
                        enabled = state.canEstimate,
                        modifier = Modifier.fillMaxWidth().testTag("bulk.estimate"),
                    ) {
                        if (state.phase == BulkPhase.Estimating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Functions, contentDescription = null)
                        }
                        Text(stringResource(R.string.conversion_admin_bulk_estimate), modifier = Modifier.padding(start = 8.dp))
                    }
                    Caption(stringResource(R.string.conversion_admin_bulk_estimate_footer))
                }
            }
            item(key = "result") { ResultSection(state, onConfirmRequested = { isConfirming = true }) }
            state.error?.let { message ->
                item(key = "error") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("bulk.error"),
                        )
                    }
                }
            }
        }
    }

    if (isPickingScope) {
        BulkScopePickerDialog(
            onSelect = { scope, name ->
                isPickingScope = false
                onScopeSelected(scope, name)
            },
            onDismiss = { isPickingScope = false },
        )
    }
    val estimated = state.phase as? BulkPhase.Estimated
    if (isConfirming && estimated != null) {
        AlertDialog(
            onDismissRequest = { isConfirming = false },
            title = { Text(stringResource(R.string.conversion_admin_bulk_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.conversion_admin_bulk_confirm_message,
                        estimated.estimate.examCount,
                        estimated.estimate.estimatedCostUsd.toUsd(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirming = false
                        onConfirm()
                    },
                    modifier = Modifier.testTag("bulk.confirm.queue"),
                ) { Text(stringResource(R.string.conversion_admin_bulk_queue)) }
            },
            dismissButton = {
                TextButton(onClick = { isConfirming = false }) { Text(stringResource(R.string.conversion_admin_cancel)) }
            },
        )
    }
}

@Composable
private fun ScopeRow(scopeName: String?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(stringResource(R.string.conversion_admin_bulk_scope))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp)
                .testTag("bulk.scope"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = scopeName ?: stringResource(R.string.conversion_admin_bulk_scope_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = if (scopeName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        HorizontalDivider()
    }
}

@Composable
private fun ResultSection(state: BulkConversionUiState, onConfirmRequested: () -> Unit) {
    when (val phase = state.phase) {
        is BulkPhase.Estimated -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.conversion_admin_bulk_result))
            LabeledValue(stringResource(R.string.conversion_admin_bulk_exam_count), phase.estimate.examCount.toString())
            LabeledValue(stringResource(R.string.conversion_admin_bulk_cost), phase.estimate.estimatedCostUsd.toUsd())
            if (state.estimateExceedsBudget) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = stringResource(R.string.conversion_admin_bulk_over_budget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.testTag("bulk.overBudget"),
                    )
                }
            }
            Button(
                onClick = onConfirmRequested,
                enabled = phase.estimate.examCount > 0,
                modifier = Modifier.fillMaxWidth().testTag("bulk.confirm"),
            ) { Text(stringResource(R.string.conversion_admin_bulk_queue)) }
        }
        BulkPhase.Queueing -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.conversion_admin_bulk_queueing))
        }
        is BulkPhase.Queued -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.conversion_admin_bulk_queued, phase.result.examCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("bulk.queued"),
            )
        }
        BulkPhase.Idle, BulkPhase.Estimating -> Unit
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Browses the archive down to a department (all its exams) or one of its courses and reports the choice (iOS
 * `BulkScopePicker`).
 */
@Composable
private fun BulkScopePickerDialog(
    onSelect: (BulkConversionScope, String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: BulkScopePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The next opening starts at the universities again (iOS builds a new picker each time).
    DisposableEffect(viewModel) { onDispose { viewModel.reset() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BulkScopePickerContent(
            state = state,
            onOpen = viewModel::open,
            onPickDepartment = { state.departmentScope()?.let { (scope, name) -> onSelect(scope, name) } },
            onPickCourse = { item ->
                item.path.components.lastOrNull()?.let { id ->
                    onSelect(BulkConversionScope.Course(id), item.path.displayNames.joinToString(" › "))
                }
            },
            onRetry = viewModel::retry,
            onBack = { if (!viewModel.up()) onDismiss() },
        )
    }
}

/** Stateless scope picker content; previews and UI tests drive this. */
@Composable
fun BulkScopePickerContent(
    state: BulkScopePickerUiState,
    onOpen: (ArchiveItem) -> Unit,
    onPickDepartment: () -> Unit,
    onPickCourse: (ArchiveItem) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = state.path.displayName ?: stringResource(R.string.conversion_admin_bulk_picker_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        val items = state.items
        when {
            items == null && state.error != null -> ErrorView(state.error, onRetry, contentModifier)
            items == null -> LoadingView(contentModifier)
            else -> LazyColumn(modifier = contentModifier.testTag("bulk.picker")) {
                if (state.canPickDepartment) {
                    item(key = "department") {
                        TextButton(
                            onClick = onPickDepartment,
                            modifier = Modifier.padding(horizontal = 8.dp).testTag("bulk.pick.department"),
                        ) { Text(stringResource(R.string.conversion_admin_bulk_pick_department)) }
                        HorizontalDivider()
                    }
                }
                items(items = items.filter { it.level != ArchiveLevel.EXAM }, key = { it.path.components.joinToString("/") }) { entry ->
                    val isCourse = entry.level == ArchiveLevel.LESSON
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (isCourse) onPickCourse(entry) else onOpen(entry) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag(
                                if (isCourse) "bulk.pick.course.${entry.path.components.lastOrNull()}"
                                else "bulk.open.${entry.path.components.lastOrNull()}",
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = entry.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (!isCourse) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
