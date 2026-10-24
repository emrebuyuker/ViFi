package com.salticusteam.vifi.feature.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.AppStoreLink
import com.salticusteam.vifi.core.model.AppUpdate

/**
 * The blocking screen shown when the installed version is below the required minimum (iOS ForceUpdateView).
 * It fills the screen and cannot be dismissed — the back button is swallowed — so the only way forward is to
 * update. The gate replaces the rest of the UI (see [AppUpdateGate]) and lifts on its own when the live policy
 * no longer requires an update.
 */
@Composable
fun ForceUpdateScreen(
    update: AppUpdate,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { /* the gate cannot be dismissed */ }

    Scaffold(
        modifier = modifier.testTag("forceUpdate.screen"),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.mipmap.icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(19.dp)),
                )
                Text(
                    text = stringResource(R.string.force_update_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("forceUpdate.title"),
                )
                Text(
                    text = update.message ?: stringResource(R.string.force_update_default_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("forceUpdate.message"),
                )
            }
            Spacer(Modifier.size(24.dp))
            Button(
                onClick = onUpdate,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .testTag("forceUpdate.update"),
            ) {
                Text(stringResource(R.string.update_action_update), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@ViFiThemePreviews
@Composable
private fun ForceUpdateScreenPreview() {
    ViFiTheme {
        ForceUpdateScreen(
            update = AppUpdate(version = "3.1", storeUrl = AppStoreLink.PRODUCT_URL, message = null),
            onUpdate = {},
        )
    }
}
