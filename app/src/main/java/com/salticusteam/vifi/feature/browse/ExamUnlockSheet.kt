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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.salticusteam.vifi.core.model.ExamLock
import com.salticusteam.vifi.core.model.ExamUnlockError

/**
 * Shown when the user taps a locked exam (iOS ExamUnlockSheet): offers to unlock it with coins, or — when the
 * balance is too low — points to adding exams to earn coins. Cannot be dismissed while an unlock is in flight.
 * [onEarnCoins] opens the add-exam flow; when `null` the earn branch shows its explanation only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamUnlockSheet(
    examName: String,
    state: ExamUnlockUiState,
    onUnlock: () -> Unit,
    onEarnCoins: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !state.isUnlocking },
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                enabled = !state.isUnlocking,
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
            AffordContent(balance = state.balance, isUnlocking = state.isUnlocking, onUnlock = onUnlock)
        } else {
            EarnContent(onEarnCoins = onEarnCoins)
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("exam.unlock.error"),
            )
        }
    }
}

@Composable
private fun AffordContent(balance: Int, isUnlocking: Boolean, onUnlock: () -> Unit) {
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
            enabled = !isUnlocking,
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
private fun EarnContent(onEarnCoins: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.exam_unlock_earn_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onEarnCoins != null) {
            Button(onClick = onEarnCoins, modifier = Modifier.fillMaxWidth().testTag("exam.unlock.earn")) {
                Text(stringResource(R.string.exam_unlock_earn_button), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamUnlockSheetAffordPreview() {
    ViFiTheme {
        ExamUnlockSheetContent("2023 Final", ExamUnlockUiState(balance = 120), {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamUnlockSheetEarnPreview() {
    ViFiTheme {
        ExamUnlockSheetContent("2023 Final", ExamUnlockUiState(balance = 20, error = ExamUnlockError.InsufficientCoins.userMessage), {}, {}, {})
    }
}
