package com.salticusteam.vifi.feature.notifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import com.salticusteam.vifi.feature.notifications.NotificationPermissionFlow.Page

/**
 * The flow's two pages (iOS `NotificationPrimingView` / `NotificationConsentView`), one after the other in the same
 * sheet. The message scrolls only when it does not fit (large font scales); the buttons always stay in reach below it.
 */
@Composable
internal fun NotificationPromptContent(
    state: NotificationPermissionFlow.State,
    source: NotificationPromptSource,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onNotNow: () -> Unit,
    onAnswer: (marketing: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.page,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
        label = "notificationPromptPage",
    ) { page ->
        when (page) {
            Page.PERMISSION -> PrimingPage(state, source, onEnable, onOpenSettings, onNotNow)
            Page.CONSENT -> ConsentPage(state, onAnswer)
        }
    }
}

/** Step 1/2: why notifications are worth allowing; only "Bildirimleri Aç" shows the system dialog. */
@Composable
private fun PrimingPage(
    state: NotificationPermissionFlow.State,
    source: NotificationPromptSource,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onNotNow: () -> Unit,
) {
    PromptLayout(
        icon = Icons.Filled.NotificationsActive,
        title = stringResource(
            if (source == NotificationPromptSource.REASK) {
                R.string.notification_prompt_priming_reask_title
            } else {
                R.string.notification_prompt_priming_title
            },
        ),
        tag = "notifications.priming",
        hasDragHandle = state.canDismiss,
        message = {
            BenefitRow(Icons.Filled.AutoStories, stringResource(R.string.notification_prompt_priming_benefit_exams))
            BenefitRow(Icons.Filled.MonetizationOn, stringResource(R.string.notification_prompt_priming_benefit_coins))
            BenefitRow(Icons.Filled.Campaign, stringResource(R.string.notification_prompt_priming_benefit_announcements))
            if (state.needsSettingsApp) {
                Text(
                    text = stringResource(R.string.notification_prompt_priming_denied_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().testTag("notifications.priming.deniedNote"),
                )
            }
        },
        actions = {
            if (state.needsSettingsApp) {
                PrimaryButton(
                    text = stringResource(R.string.notification_prompt_priming_open_settings),
                    isWorking = false,
                    enabled = !state.isWorking,
                    onClick = onOpenSettings,
                    tag = "notifications.priming.openSettings",
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.notification_prompt_priming_enable),
                    isWorking = state.isWorking,
                    enabled = !state.isWorking,
                    onClick = onEnable,
                    tag = "notifications.priming.enable",
                )
            }
            TextButton(
                onClick = onNotNow,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("notifications.priming.dismiss"),
            ) {
                Text(stringResource(R.string.notification_prompt_priming_not_now), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** Step 2/2: the campaign consent. Neither answer is preselected and both are written. */
@Composable
private fun ConsentPage(state: NotificationPermissionFlow.State, onAnswer: (Boolean) -> Unit) {
    PromptLayout(
        icon = Icons.Filled.AutoAwesome,
        title = stringResource(R.string.notification_prompt_consent_title),
        tag = "notifications.consent",
        hasDragHandle = state.canDismiss,
        message = {
            Text(
                text = stringResource(R.string.notification_prompt_consent_message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            BenefitRow(Icons.Filled.Bedtime, stringResource(R.string.notification_prompt_consent_quiet_hours))
            BenefitRow(Icons.Filled.Settings, stringResource(R.string.notification_prompt_consent_turn_off))
            if (state.saveFailed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("notifications.consent.error"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.notification_prompt_consent_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        actions = {
            PrimaryButton(
                text = stringResource(R.string.notification_prompt_consent_accept),
                isWorking = state.answering == true,
                enabled = !state.isWorking,
                onClick = { onAnswer(true) },
                tag = "notifications.consent.accept",
            )
            FilledTonalButton(
                onClick = { onAnswer(false) },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("notifications.consent.decline"),
            ) {
                ButtonLabel(stringResource(R.string.notification_prompt_consent_decline), isWorking = state.answering == false)
            }
        },
    )
}

/** iOS `NotificationPromptLayout`: icon, title and message above, the buttons pinned below. */
@Composable
private fun PromptLayout(
    icon: ImageVector,
    title: String,
    tag: String,
    hasDragHandle: Boolean,
    message: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                // Without the drag handle the page needs its own room at the top.
                .padding(top = if (hasDragHandle) 4.dp else 32.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IconBadge(icon = icon, size = 64.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = message,
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = actions,
        )
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Full width, black on the brand orange (white falls below 3:1 there); keeps its colours while working. */
@Composable
private fun PrimaryButton(text: String, isWorking: Boolean, enabled: Boolean, onClick: () -> Unit, tag: String) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag),
    ) {
        ButtonLabel(text, isWorking)
    }
}

@Composable
private fun ButtonLabel(text: String, isWorking: Boolean) {
    if (isWorking) {
        CircularProgressIndicator(
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
    } else {
        Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@ViFiThemePreviews
@Composable
private fun PrimingPagePreview() {
    ViFiTheme {
        NotificationPromptContent(
            state = NotificationPermissionFlow.State(Page.PERMISSION, NotificationPermission.NOT_DETERMINED),
            source = NotificationPromptSource.PROMPT,
            onEnable = {},
            onOpenSettings = {},
            onNotNow = {},
            onAnswer = {},
        )
    }
}

@ViFiThemePreviews
@Composable
private fun ConsentPageFailedPreview() {
    ViFiTheme {
        NotificationPromptContent(
            state = NotificationPermissionFlow.State(Page.CONSENT, NotificationPermission.AUTHORIZED, saveFailed = true),
            source = NotificationPromptSource.PROMPT,
            onEnable = {},
            onOpenSettings = {},
            onNotNow = {},
            onAnswer = {},
        )
    }
}
