package com.salticusteam.vifi.feature.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.PracticeSelection

/**
 * The "Sınava gir" setup (iOS `PracticeFlowView`): exam type, then scope. [onStartSession] opens the session over the
 * chosen pool; [onBack] leaves the setup (from the scope step, Back first returns to the types).
 */
@Composable
fun PracticeSetupScreen(
    subjectId: String,
    courseId: String,
    onStartSession: (PracticeSelection) -> Unit,
    onBack: () -> Unit,
    viewModel: PracticeSetupViewModel = hiltViewModel<PracticeSetupViewModel, PracticeSetupViewModel.Factory>(
        key = "$subjectId/$courseId",
    ) { it.create(subjectId, courseId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PracticeSetupEvent.StartSession -> onStartSession(event.selection)
            }
        }
    }

    PracticeSetupContent(
        state = state,
        onTypeSelected = viewModel::onTypeSelected,
        onScopeSelected = viewModel::onScopeSelected,
        onBackToTypes = viewModel::onBackToTypes,
        onRetry = viewModel::onRetry,
        onBack = onBack,
    )
}

/** Stateless setup content; previews and UI tests drive this. */
@Composable
fun PracticeSetupContent(
    state: PracticeSetupUiState,
    onTypeSelected: (ExamType) -> Unit,
    onScopeSelected: (PracticeScope) -> Unit,
    onBackToTypes: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedType = state.selectedType
    BackHandler(enabled = selectedType != null) { onBackToTypes() }

    Scaffold(
        modifier = modifier.testTag("practice.setup"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = stringResource(selectedType?.practiceTitleRes() ?: R.string.practice_setup_title),
                onBack = if (selectedType != null) onBackToTypes else onBack,
            )
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingView(contentModifier)
            state.error != null -> ErrorView(state.error, onRetry, contentModifier)
            state.isEmpty -> EmptyView(
                title = stringResource(R.string.practice_setup_empty_title),
                icon = Icons.Filled.QuestionMark,
                message = stringResource(R.string.practice_setup_empty_message),
                modifier = contentModifier,
            )
            selectedType == null -> ChoiceList(
                header = stringResource(R.string.practice_setup_types_header),
                listTag = "practice.types",
                modifier = contentModifier,
            ) {
                items(state.typeRows, key = { it.type.raw }) { row ->
                    ChoiceRow(
                        title = stringResource(row.type.practiceTitleRes()),
                        count = row.count,
                        tag = "practice.type.${row.type.raw}",
                        onClick = { onTypeSelected(row.type) },
                    )
                }
            }
            else -> ChoiceList(
                header = stringResource(R.string.practice_setup_scopes_header),
                listTag = "practice.scopes",
                modifier = contentModifier,
            ) {
                items(state.scopeRows, key = { it.scope.raw }) { row ->
                    ChoiceRow(
                        title = stringResource(row.scope.titleRes()),
                        count = row.count,
                        tag = "practice.scope.${row.scope.raw}",
                        onClick = { onScopeSelected(row.scope) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceList(
    header: String,
    listTag: String,
    modifier: Modifier = Modifier,
    rows: LazyListScope.() -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxWidth().testTag(listTag)) {
        item(key = "header") {
            Text(
                text = header,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
        rows()
    }
}

/** A list row with a title, its question count and a chevron; one accessibility element. */
@Composable
private fun ChoiceRow(
    title: String,
    count: Int,
    tag: String,
    onClick: () -> Unit,
) {
    val countText = pluralStringResource(R.plurals.practice_question_count, count, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Preview(showBackground = true)
@Composable
private fun PracticeSetupTypesPreview() {
    ViFiTheme {
        PracticeSetupContent(
            state = PracticeSetupUiState(
                isLoading = false,
                typeRows = listOf(PracticeTypeRow(ExamType.VIZE, 1), PracticeTypeRow(ExamType.FINAL, 40)),
            ),
            onTypeSelected = {},
            onScopeSelected = {},
            onBackToTypes = {},
            onRetry = {},
            onBack = {},
        )
    }
}
