package com.salticusteam.vifi.core.designsystem.component

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Compact pill with the user's coin balance for top bars (iOS CoinBalanceBadge). Stateless: the caller passes
 * the live balance from the wallet store (coin area), so every bar updates at once. The number counts between values.
 */
@Composable
fun CoinBalanceBadge(
    balance: Int,
    modifier: Modifier = Modifier,
) {
    val shown by animateIntAsState(targetValue = balance, label = "coinBalance")
    val description = stringResource(R.string.cd_coin_balance, formatCoins(balance))

    Row(
        modifier = modifier
            .testTag("coin.balance")
            .clearAndSetSemantics { contentDescription = description }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.MonetizationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = formatCoins(shown),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A coin amount with Turkish digit grouping ("1.250"), for every coin label in the UI. */
fun formatCoins(amount: Int): String = TURKISH_NUMBER.format(amount.toLong())

// NumberFormat is not thread-safe; it is only used from composition, which runs on the main thread.
private val TURKISH_NUMBER: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR"))

@Preview(showBackground = true)
@Composable
private fun CoinBalanceBadgePreview() {
    ViFiTheme { CoinBalanceBadge(balance = 1250) }
}
