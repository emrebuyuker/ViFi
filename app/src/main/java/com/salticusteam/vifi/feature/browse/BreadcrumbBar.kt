package com.salticusteam.vifi.feature.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath

/**
 * One capsule of the trail: chip [index] stands for `path.prefix(index)` ([target]), so chip 0 is the university
 * list and has no [title] of its own. The [isCurrent] chip is the screen being shown.
 */
internal data class BreadcrumbChip(
    val index: Int,
    val title: String?,
    val target: ArchivePath,
    val isCurrent: Boolean,
)

/** The chips of the trail to [path]: the university list first, then each level's display name. */
internal fun breadcrumbChips(path: ArchivePath): List<BreadcrumbChip> =
    (0..path.depth).map { index ->
        BreadcrumbChip(
            index = index,
            title = if (index == 0) null else path.displayNames[index - 1],
            target = path.prefix(index),
            isCurrent = index == path.depth,
        )
    }

/**
 * Horizontally scrolling trail of the current archive location: "Üniversiteler › … › current" (iOS BreadcrumbBar).
 * Tapping an ancestor chip returns to that level; the last chip is the current screen, highlighted and not
 * tappable. The bar starts scrolled to its end so the current chip is visible.
 */
@Composable
fun BreadcrumbBar(
    path: ArchivePath,
    onSelect: (ArchivePath) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // Re-runs once the first layout sets maxValue: the iOS `defaultScrollAnchor(.trailing)`.
    LaunchedEffect(path, scrollState.maxValue) { scrollState.scrollToEndOnceMeasured() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .testTag("browse.breadcrumb"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        breadcrumbChips(path).forEach { chip ->
            if (chip.index > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
            BreadcrumbCapsule(
                title = chip.title ?: stringResource(R.string.archive_level_university_list_title),
                showsRootIcon = chip.index == 0,
                isCurrent = chip.isCurrent,
                onClick = { onSelect(chip.target) }.takeUnless { chip.isCurrent },
                modifier = Modifier.testTag("breadcrumb.${chip.index}"),
            )
        }
    }
}

/**
 * Scrolls to the end, but only once the content has been measured. Before the first layout `maxValue` is still
 * `Int.MAX_VALUE`, and scrolling there from a restored non-zero offset (back navigation, configuration change)
 * overflows `ScrollState`'s Int value, so the next scroll to the real end lands at a random spot (QA-02).
 */
internal suspend fun ScrollState.scrollToEndOnceMeasured() {
    if (maxValue != Int.MAX_VALUE) scrollTo(maxValue)
}

/** A chip of the trail; [onClick] is `null` for the current (selected, not tappable) chip. */
@Composable
private fun BreadcrumbCapsule(
    title: String,
    showsRootIcon: Boolean,
    isCurrent: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.archive_breadcrumb_hint)
    val interaction = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClickLabel = hint, onClick = onClick)
    } else {
        Modifier.semantics { selected = true }
    }
    Surface(
        modifier = modifier
            .then(interaction)
            .then(
                if (isCurrent) {
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)), CircleShape)
                } else {
                    Modifier
                },
            ),
        shape = CircleShape,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showsRootIcon) {
                Icon(
                    imageVector = ArchiveLevel.UNIVERSITY.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BreadcrumbBarPreview() {
    val deep = ArchivePath.ROOT
        .appending("dpu", "KÜTAHYA DUMLUPINAR ÜNİVERSİTESİ")
        .appending("fen", "FEN EDEBİYAT FAKÜLTESİ")
        .appending("fiz", "FİZİK BÖLÜMÜ")
        .appending("kuantum", "KUANTUM FİZİĞİ")
    ViFiTheme {
        Column {
            BreadcrumbBar(path = deep, onSelect = {})
            BreadcrumbBar(path = ArchivePath.ROOT.appending("bozok", "BOZOK ÜNİVERSİTESİ"), onSelect = {})
        }
    }
}
