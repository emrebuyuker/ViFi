package com.salticusteam.vifi.feature.browse

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.formatCoins
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.ExamLock
import com.salticusteam.vifi.core.model.ExamUnlockError

/**
 * Shown when the user taps a locked exam (iOS ExamUnlockSheet): offers to unlock it with coins, or — when the
 * balance is too low — points to adding exams to earn coins; "veya" buying just this exam with money (always), and
 * switching to Premium instead. While an unlock or a purchase is in flight the other actions and dismissing are
 * locked. [onEarnCoins] opens the add-exam flow and [onGoPremium] the paywall; when `null` the earn branch shows
 * its explanation only and the Premium link is hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamUnlockSheet(
    examName: String,
    state: ExamUnlockUiState,
    onUnlock: () -> Unit,
    onPurchaseWithMoney: () -> Unit,
    onEarnCoins: (() -> Unit)?,
    onGoPremium: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // The sheet state keeps the first lambda, so it reads the latest busy flag through this.
    val isBusy by rememberUpdatedState(state.isBusy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !isBusy },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("exam.unlock.sheet"),
    ) {
        ExamUnlockSheetContent(
            examName = examName,
            state = state,
            onUnlock = onUnlock,
            onEarnCoins = onEarnCoins,
            onClose = onDismiss,
            onPurchaseWithMoney = onPurchaseWithMoney,
            onGoPremium = onGoPremium,
        )
    }
}

@Composable
internal fun ExamUnlockSheetContent(
    examName: String,
    state: ExamUnlockUiState,
    onUnlock: () -> Unit,
    onEarnCoins: (() -> Unit)?,
    onClose: () -> Unit,
    onPurchaseWithMoney: () -> Unit = {},
    onGoPremium: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.exam_unlock_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(
                onClick = onClose,
                enabled = !state.isBusy,
                modifier = Modifier.align(Alignment.CenterEnd).testTag("exam.unlock.close"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.exam_unlock_close))
            }
        }

        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = examName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.exam_unlock_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.canAfford) {
            AffordContent(
                balance = state.balance,
                isUnlocking = state.isUnlocking,
                enabled = !state.isBusy,
                onUnlock = onUnlock,
            )
        } else {
            EarnContent(enabled = !state.isBusy, onEarnCoins = onEarnCoins)
        }

        MoneyUnlockOption(isPurchasing = state.isPurchasing, enabled = !state.isBusy, onPurchase = onPurchaseWithMoney)

        val message = state.error
            ?: if (state.isPurchasePending) stringResource(R.string.exam_unlock_purchase_pending) else null
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("exam.unlock.error"),
            )
        }

        if (onGoPremium != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                TextButton(
                    onClick = onGoPremium,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("exam.unlock.premium"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.exam_unlock_premium),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AffordContent(balance: Int, isUnlocking: Boolean, enabled: Boolean, onUnlock: () -> Unit) {
    val cost = formatCoins(ExamLock.UNLOCK_COST)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.MonetizationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.exam_unlock_cost_balance, cost, formatCoins(balance)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onUnlock,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("exam.unlock.confirm"),
        ) {
            if (isUnlocking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).testTag("exam.unlock.progress"),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.exam_unlock_confirm, cost), fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            text = stringResource(R.string.exam_unlock_permanent_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EarnContent(enabled: Boolean, onEarnCoins: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.exam_unlock_earn_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onEarnCoins != null) {
            Button(
                onClick = onEarnCoins,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("exam.unlock.earn"),
            ) {
                Text(stringResource(R.string.exam_unlock_earn_button), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * "veya" + buying just this exam with money: an alternative to coins, always offered. Google Play's sheet shows
 * the price; Premium (the link below) is the better deal for more than a couple of exams.
 */
@Composable
private fun MoneyUnlockOption(isPurchasing: Boolean, enabled: Boolean, onPurchase: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.exam_unlock_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = onPurchase,
            enabled = enabled,
            // Disabled while buying, but keeps its fill so the spinner stays visible in both themes.
            colors = if (isPurchasing) {
                ButtonDefaults.filledTonalButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
            modifier = Modifier.fillMaxWidth().testTag("exam.unlock.buy"),
        ) {
            if (isPurchasing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).testTag("exam.unlock.buy.progress"),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(imageVector = Icons.Filled.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.exam_unlock_buy),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@ViFiThemePreviews
@Composable
private fun ExamUnlockSheetAffordPreview() {
    ViFiTheme {
        ExamUnlockSheetContent("2023 Final", ExamUnlockUiState(balance = 120), {}, {}, {}, {}, {})
    }
}

@ViFiThemePreviews
@Composable
private fun ExamUnlockSheetPurchasingPreview() {
    ViFiTheme {
        ExamUnlockSheetContent("2023 Final", ExamUnlockUiState(balance = 20, isPurchasing = true), {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamUnlockSheetEarnPreview() {
    ViFiTheme {
        ExamUnlockSheetContent("2023 Final", ExamUnlockUiState(balance = 20, error = ExamUnlockError.InsufficientCoins.userMessage), {}, {}, {})
    }
}
