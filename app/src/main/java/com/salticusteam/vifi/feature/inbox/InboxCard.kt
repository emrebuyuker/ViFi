package com.salticusteam.vifi.feature.inbox

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.NotificationAction
import com.salticusteam.vifi.core.model.NotificationKind

/**
 * One notification (iOS `InboxRow`): the kind's badge, title, body, the "Kampanya" label of a campaign (Play's and
 * Apple's disclosure), the date and the unread dot; then the image and the action button when there are. With a
 * button, the card, its image and the button all run the same action; without one the card is inert.
 */
@Composable
internal fun InboxCard(
    entry: InboxEntry,
    isHighlighted: Boolean,
    isWorking: Boolean,
    imageLoader: ImageLoader?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = entry.item
    val action = entry.action
    val containerColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = HIGHLIGHT_ALPHA)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("inbox.row.${item.id}")
            .semantics { selected = isHighlighted },
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (action != null) Modifier.clickable(role = Role.Button, onClick = onAction) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                KindBadge(item.kind)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.body.isNotEmpty()) {
                        Text(
                            text = item.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Caption(isPromo = item.kind == NotificationKind.PROMO, date = item.createdAt.toInboxDate())
                }
                if (entry.isUnread) UnreadDot(Modifier.testTag("inbox.unread.${item.id}"))
            }
            entry.imageUrl?.let { url ->
                InboxImage(
                    url = url,
                    imageLoader = imageLoader,
                    onClick = onAction.takeIf { action != null },
                    modifier = Modifier.testTag("inbox.row.${item.id}.image"),
                )
            }
            action?.let {
                ActionButton(
                    action = it,
                    isWorking = isWorking,
                    onClick = onAction,
                    modifier = Modifier.testTag("inbox.row.${item.id}.action"),
                )
            }
        }
    }
}

@Composable
private fun KindBadge(kind: NotificationKind) {
    val icon = when (kind) {
        NotificationKind.GENERAL -> Icons.Filled.Campaign
        NotificationKind.PROMO -> Icons.Filled.LocalOffer
        NotificationKind.SYSTEM -> Icons.Filled.NotificationsActive
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = BADGE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Caption(isPromo: Boolean, date: String?) {
    if (!isPromo && date == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isPromo) {
            Text(
                text = stringResource(R.string.inbox_kind_promo),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        date?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UnreadDot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_inbox_unread)
    Box(
        modifier = modifier
            .padding(top = 6.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = description },
    )
}

/** Loaded with the user's credentials (images are never public); a grey placeholder while loading, an icon on failure. */
@Composable
private fun InboxImage(url: String, imageLoader: ImageLoader?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_inbox_image)
    // Scales with the text size (iOS `@ScaledMetric` 160), never taller than 280 dp.
    val height = (IMAGE_HEIGHT * LocalDensity.current.fontScale).coerceAtMost(IMAGE_MAX_HEIGHT)
    var phase by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (imageLoader != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                onState = { phase = it },
                modifier = Modifier.fillMaxWidth().height(height),
            )
        }
        when (phase) {
            is AsyncImagePainter.State.Success -> Unit
            is AsyncImagePainter.State.Error -> Icon(
                Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

/** Brand orange with black text (white on orange fails contrast); progress instead of the icon while working. */
@Composable
private fun ActionButton(action: NotificationAction, isWorking: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = !isWorking, modifier = modifier.fillMaxWidth()) {
        if (isWorking) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Icon(action.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = stringResource(action.labelRes()), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** The button's label per action (iOS `button(for:)`). */
@StringRes
private fun NotificationAction.labelRes(): Int = when (this) {
    is NotificationAction.Exam -> R.string.inbox_action_exam
    NotificationAction.Paywall -> R.string.inbox_action_paywall
    NotificationAction.AddExam -> R.string.inbox_action_add_exam
    is NotificationAction.Url -> R.string.inbox_action_url
    is NotificationAction.Location -> R.string.inbox_action_location
}

private fun NotificationAction.icon(): ImageVector = when (this) {
    is NotificationAction.Exam -> Icons.Filled.FindInPage
    NotificationAction.Paywall -> Icons.Filled.Star
    NotificationAction.AddExam -> Icons.Filled.LibraryAdd
    is NotificationAction.Url -> Icons.Filled.OpenInBrowser
    is NotificationAction.Location -> Icons.Filled.Place
}

private const val HIGHLIGHT_ALPHA = 0.18f
private const val BADGE_ALPHA = 0.15f
private val IMAGE_HEIGHT = 160.dp
private val IMAGE_MAX_HEIGHT = 280.dp
