package com.salticusteam.vifi.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveError

/** Full-screen loading indicator (iOS LoadingStateView). */
@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.common_loading),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("state.loading"),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Full-screen error with a retry action (iOS ErrorStateView).
 * [message] is a domain error's Turkish `userMessage`, never a raw exception message.
 */
@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StateMessage(
        icon = Icons.Filled.Warning,
        title = stringResource(R.string.common_error_title),
        message = message,
        modifier = modifier.testTag("state.error"),
    ) {
        // Primary button content is onPrimary (black): white on the brand orange falls below 3:1.
        Button(onClick = onRetry, modifier = Modifier.testTag("state.retry")) {
            Text(stringResource(R.string.common_try_again))
        }
    }
}

/** Full-screen empty state (iOS EmptyStateView). */
@Composable
fun EmptyView(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Inbox,
    message: String? = null,
) {
    StateMessage(
        icon = icon,
        title = title,
        message = message,
        modifier = modifier.testTag("state.empty"),
    )
}

@Composable
private fun StateMessage(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // decorative; the title carries the meaning
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            action?.invoke()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingViewPreview() {
    ViFiTheme { LoadingView() }
}

@Preview(showBackground = true)
@Composable
private fun ErrorViewPreview() {
    ViFiTheme { ErrorView(message = ArchiveError.Offline.userMessage, onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun EmptyViewPreview() {
    ViFiTheme { EmptyView(title = "Kayıt bulunamadı") }
}
