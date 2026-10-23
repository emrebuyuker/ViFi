package com.salticusteam.vifi.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.ExamKindBadge
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.badgeRes
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.RecentExam
import com.salticusteam.vifi.core.util.RelativeTime
import com.salticusteam.vifi.feature.browse.BrowseScreen

/**
 * The home screen (iOS HomeView): the university list under a hero header, the add-exam call-to-action and
 * the recently viewed shelf. The list, search and toolbar are [BrowseScreen] at the root path.
 */
@Composable
fun HomeScreen(
    onItemClick: (ArchiveItem) -> Unit,
    onRecentClick: (RecentExam) -> Unit,
    onAddExamClick: () -> Unit,
    onAccountClick: () -> Unit,
    onInboxClick: () -> Unit,
    onAboutClick: () -> Unit,
    onEarnCoins: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BrowseScreen(
        onItemClick = onItemClick,
        onBack = null,
        onAccountClick = onAccountClick,
        onEarnCoins = onEarnCoins,
        onInboxClick = onInboxClick,
        onAboutClick = onAboutClick,
        listHeader = {
            HomeHeader(
                state = state,
                onAddExamClick = onAddExamClick,
                onRecentClick = { exam ->
                    viewModel.onRecentOpened()
                    onRecentClick(exam)
                },
                onRecentRemove = viewModel::onRemove,
                onClearClick = viewModel::onClearRequested,
            )
        },
    )

    if (state.isClearConfirmationShown) {
        ClearRecentsDialog(onConfirm = viewModel::onClearConfirmed, onDismiss = viewModel::onClearDismissed)
    }
}

/** Hero, add-exam card and the recents shelf, shown above the university list. */
@Composable
internal fun HomeHeader(
    state: HomeUiState,
    onAddExamClick: () -> Unit,
    onRecentClick: (RecentExam) -> Unit,
    onRecentRemove: (RecentExam) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroHeader()
        AddExamCard(onClick = onAddExamClick)
        if (state.recents.isNotEmpty()) {
            RecentExamsSection(
                exams = state.recents,
                onClick = onRecentClick,
                onRemove = onRecentRemove,
                onClearClick = onClearClick,
            )
        }
    }
}

@Composable
private fun HeroHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("home.hero"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = Icons.Filled.School, size = 56.dp)
        Column {
            Text(
                text = stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The call to the add-exam flow (iOS addExamSection). */
@Composable
private fun AddExamCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("home.addExam"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = Icons.Filled.AddToPhotos)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_add_exam_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.home_add_exam_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentExamsSection(
    exams: List<RecentExam>,
    onClick: (RecentExam) -> Unit,
    onRemove: (RecentExam) -> Unit,
    onClearClick: () -> Unit,
) {
    Column(Modifier.testTag("home.recents")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_recents_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(onClick = onClearClick, modifier = Modifier.testTag("home.recents.clear")) {
                Text(stringResource(R.string.home_recents_clear))
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = exams, key = { it.path.components.joinToString("/") }) { exam ->
                RecentExamCard(exam = exam, onClick = { onClick(exam) }, onRemove = { onRemove(exam) })
            }
        }
    }
}

/** One recent exam: kind icon and badge, title, "University › Lesson" and when it was opened. Long press removes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentExamCard(exam: RecentExam, onClick: () -> Unit, onRemove: () -> Unit) {
    var menuShown by remember { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.home_recents_remove)
    val opened = relativeTimeLabel(remember(exam.openedAt) { RelativeTime.between(exam.openedAt, System.currentTimeMillis()) })

    Card(
        modifier = Modifier
            .width(220.dp)
            .combinedClickable(onClick = onClick, onLongClick = { menuShown = true }, onLongClickLabel = removeLabel)
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true })
            }
            .testTag("home.recent.${exam.path.components.joinToString("/")}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = exam.kind.icon, size = 32.dp)
                ExamKindBadge(text = stringResource(exam.kind.badgeRes))
            }
            Text(
                text = exam.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = exam.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = opened,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuShown, onDismissRequest = { menuShown = false }) {
            DropdownMenuItem(
                text = { Text(removeLabel) },
                onClick = {
                    menuShown = false
                    onRemove()
                },
                modifier = Modifier.testTag("home.recent.remove"),
            )
        }
    }
}

@Composable
private fun ClearRecentsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_recents_clear_title)) },
        text = { Text(stringResource(R.string.home_recents_clear_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("home.recents.clearConfirm")) {
                Text(stringResource(R.string.home_recents_clear_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_recents_clear_cancel)) }
        },
        modifier = Modifier.testTag("home.recents.clearDialog"),
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeHeaderPreview() {
    val path = ArchivePath.ROOT.appending("itu", "İTÜ").appending("muh", "MÜH").appending("blg", "BLG")
        .appending("blg101", "BLG 101").appending("v", "2023 Vize")
    ViFiTheme {
        HomeHeader(
            state = HomeUiState(recents = listOf(RecentExam(path, ExamKind.PDF, System.currentTimeMillis() - 3_600_000))),
            onAddExamClick = {}, onRecentClick = {}, onRecentRemove = {}, onClearClick = {},
        )
    }
}

/** Turkish "… önce" label for [time]. */
@Composable
private fun relativeTimeLabel(time: RelativeTime): String = when (time) {
    RelativeTime.JustNow -> stringResource(R.string.common_relative_just_now)
    is RelativeTime.Minutes -> pluralStringResource(R.plurals.common_relative_minutes_ago, time.count, time.count)
    is RelativeTime.Hours -> pluralStringResource(R.plurals.common_relative_hours_ago, time.count, time.count)
    is RelativeTime.Days -> pluralStringResource(R.plurals.common_relative_days_ago, time.count, time.count)
    is RelativeTime.Weeks -> pluralStringResource(R.plurals.common_relative_weeks_ago, time.count, time.count)
    is RelativeTime.Months -> pluralStringResource(R.plurals.common_relative_months_ago, time.count, time.count)
    is RelativeTime.Years -> pluralStringResource(R.plurals.common_relative_years_ago, time.count, time.count)
}
