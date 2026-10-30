package com.salticusteam.vifi.feature.notificationcomposer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.ExamKindBadge
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.badgeRes
import com.salticusteam.vifi.core.designsystem.component.childCountText
import com.salticusteam.vifi.core.designsystem.component.contentText
import com.salticusteam.vifi.core.designsystem.component.emptyListTitleRes
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.designsystem.component.searchPromptRes
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.navigation.RegisterModalPresence
import com.salticusteam.vifi.core.navigation.ViFiRoute
import com.salticusteam.vifi.feature.browse.BrowseUiState
import com.salticusteam.vifi.feature.browse.BrowseViewModel

/**
 * Picks the exam a notification links to by walking the archive — university › faculty › department › course › exam
 * — with the same view model and search as browsing it (iOS `NotificationExamPickerView`). Only an exam with files can
 * be picked; the server fills in its path and names from the published exam. Shown full screen in place of the form
 * (like Sınav Ekle's pickers): back steps up a level, and at the top level back or "✕" ([onDismiss]) closes it; a
 * picked exam ([onPick]) closes it too.
 */
@Composable
fun NotificationExamPicker(onPick: (ArchiveItem) -> Unit, onDismiss: () -> Unit) {
    RegisterModalPresence()
    // The levels' view models live only as long as the picker: its own store, cleared when it closes.
    val storeOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) { onDispose { storeOwner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        // Deeper levels go back through the NavHost; only the top level closes the picker.
        BackHandler(enabled = entry?.destination?.hasRoute<ViFiRoute.Home>() ?: true, onBack = onDismiss)
        // The browser's own destinations: their arguments are what BrowseViewModel reads its level from.
        NavHost(navController = navController, startDestination = ViFiRoute.Home) {
            composable<ViFiRoute.Home> {
                ExamPickerLevel(navController, onPick = onPick, onDismiss = onDismiss)
            }
            composable<ViFiRoute.Browse> {
                ExamPickerLevel(navController, onPick = onPick, onDismiss = onDismiss)
            }
        }
    }
}

/** One level of the picker: drills down into a non-exam entry, picks an exam. */
@Composable
private fun ExamPickerLevel(
    navController: NavHostController,
    onPick: (ArchiveItem) -> Unit,
    onDismiss: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Searched here rather than through the view model, which would report it as an archive search.
    var searchText by rememberSaveable { mutableStateOf("") }
    ExamPickerContent(
        state = state.copy(searchText = searchText),
        onSearchTextChange = { searchText = it },
        onItemClick = { item ->
            if (item.level == ArchiveLevel.EXAM) onPick(item) else navController.navigate(ViFiRoute.Browse(item.path))
        },
        onRetry = viewModel::load,
        onBack = if (state.path.isRoot) null else ({ navController.popBackStack() }),
        onDismiss = onDismiss,
    )
}

/** Stateless level content; UI tests drive this. [onBack] is `null` at the root, where "✕" is the only way out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamPickerContent(
    state: BrowseUiState,
    onSearchTextChange: (String) -> Unit,
    onItemClick: (ArchiveItem) -> Unit,
    onRetry: () -> Unit,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val listedLevel = state.path.childLevel ?: ArchiveLevel.EXAM
    val close = @Composable {
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("$COMPOSER_TAG.exam.cancel")) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.notification_composer_cancel))
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.path.displayName ?: stringResource(R.string.notification_composer_exam_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack == null) {
                        close()
                    } else {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("$COMPOSER_TAG.exam.back")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_navigate_back),
                            )
                        }
                    }
                },
                actions = { if (onBack != null) close() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val items = state.items
            when {
                state.isLoading && items == null -> LoadingView()
                state.error != null && items == null -> ErrorView(message = state.error, onRetry = onRetry)
                items != null && items.isEmpty() -> EmptyView(
                    title = stringResource(listedLevel.emptyListTitleRes),
                    icon = listedLevel.icon,
                )
                else -> {
                    PickerSearchField(
                        text = state.searchText,
                        placeholder = stringResource(listedLevel.searchPromptRes),
                        onTextChange = onSearchTextChange,
                    )
                    if (state.isFiltering && state.visibleItems.isEmpty()) {
                        EmptyView(
                            title = stringResource(R.string.archive_search_no_results, state.searchQuery),
                            icon = Icons.Filled.SearchOff,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("$COMPOSER_TAG.exam.list"),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(items = state.visibleItems, key = { it.path.components.joinToString("/") }) { item ->
                                ExamPickerRow(item = item, onClick = { onItemClick(item) })
                                HorizontalDivider(Modifier.padding(start = 72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSearchField(text: String, placeholder: String, onTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onTextChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("$COMPOSER_TAG.exam.search"),
    )
}

/**
 * One entry, like the archive's rows (no lock: the admin links any exam). A non-exam entry drills down; an exam is
 * picked, unless it has no files, which leaves it disabled.
 */
@Composable
private fun ExamPickerRow(item: ArchiveItem, onClick: () -> Unit) {
    val exam = item.exam
    val enabled = item.level != ArchiveLevel.EXAM || exam != null
    val subtitle = when {
        exam != null -> exam.kind.contentText(exam.fileCount)
        item.childCount != null -> item.level.childCountText(item.childCount)
        item.level == ArchiveLevel.EXAM -> stringResource(R.string.exam_no_files)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("$COMPOSER_TAG.exam.row.${item.name}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = exam?.kind?.icon ?: item.level.icon,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            exam != null -> ExamKindBadge(text = stringResource(exam.kind.badgeRes))
            item.level != ArchiveLevel.EXAM -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The "Sınav" action's target in the composer: the picked exam (with where it is in the archive) and a tap to pick
 * one (iOS `NotificationComposerExamRow`).
 */
@Composable
internal fun ExamRow(selectedExam: ArchiveItem?, enabled: Boolean, onPickExam: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onPickExam)
                .padding(vertical = 8.dp)
                .testTag("$COMPOSER_TAG.exam"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.notification_composer_exam_label),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = selectedExam?.name ?: stringResource(R.string.notification_composer_select),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        selectedExam?.let { exam ->
            Text(
                text = exam.path.displayNames.dropLast(1).joinToString(" › "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("$COMPOSER_TAG.examPath"),
            )
        }
    }
}
