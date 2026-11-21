package com.salticusteam.vifi.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchivePath

/**
 * The "Sınava gir" row at the top of a course's exam list (iOS `PracticeStartSection`). Shows nothing until the
 * course's subject has at least one practice question (and nothing on a failed read); a tap reports the subject, which
 * the host passes on as `ViFiRoute.PracticeSetup(course, subjectId)`. The archive list places it (SIT-01).
 */
@Composable
fun PracticeEntry(
    course: ArchivePath,
    onOpen: (subjectId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PracticeEntryViewModel = hiltViewModel(key = course.components.joinToString("/")),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(course) {
        viewModel.load(course)
    }

    val subjectId = state.subjectId ?: return
    PracticeEntryRow(onClick = { onOpen(subjectId) }, modifier = modifier)
}

/** Stateless row; previews and UI tests drive this. */
@Composable
fun PracticeEntryRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("practice.start"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(icon = Icons.Filled.PlayCircle, size = 36.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.practice_entry_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.practice_entry_subtitle),
                    style = MaterialTheme.typography.bodySmall,
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

@Preview(showBackground = true)
@Composable
private fun PracticeEntryRowPreview() {
    ViFiTheme {
        PracticeEntryRow(onClick = {}, modifier = Modifier.padding(16.dp))
    }
}
