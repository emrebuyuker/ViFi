package com.salticusteam.vifi.feature.premium

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.model.SubscriptionOption
import com.salticusteam.vifi.core.model.SubscriptionPeriod
import com.salticusteam.vifi.core.util.LegalLinks

/**
 * The ViFi Premium paywall (iOS PaywallView), opened from the account screen and from the unlock sheet: the
 * benefits, the plans (yearly by default), Google Play's renewal terms and the legal links, then "Abone Ol" and
 * "Satın alımları geri yükle". It closes itself once a subscription is active; [onClose] is also the "✕".
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PaywallEvent.Subscribed -> currentOnClose()
            }
        }
    }

    PaywallContent(
        state = state,
        onClose = onClose,
        onPlanSelected = viewModel::onPlanSelected,
        onSubscribe = viewModel::onSubscribe,
        onRestore = viewModel::onRestore,
        onRetry = viewModel::onRetry,
        onOpenTerms = { LegalLinks.open(context, LegalLinks.TERMS_URL) },
        onOpenPrivacy = { LegalLinks.open(context, LegalLinks.PRIVACY_URL) },
    )
}

/** Stateless paywall content; previews and UI tests drive this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallContent(
    state: PaywallUiState,
    onClose: () -> Unit,
    onPlanSelected: (basePlanId: String) -> Unit,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Leaving mid-purchase would lose its outcome (iOS disables "Kapat" meanwhile).
    BackHandler(enabled = state.isPurchasing) {}

    Scaffold(
        modifier = modifier.testTag("paywall.sheet"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paywall_title), style = MaterialTheme.typography.titleLarge) },
                // A modal flow: close ("✕") rather than a back arrow.
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        enabled = !state.isPurchasing,
                        modifier = Modifier.testTag("paywall.close"),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.paywall_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { PurchaseBar(state = state, onSubscribe = onSubscribe, onRestore = onRestore) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Header()
            Benefits()
            PlanList(state = state, onPlanSelected = onPlanSelected, onRetry = onRetry)
            LegalFooter(onOpenTerms = onOpenTerms, onOpenPrivacy = onOpenPrivacy)
        }
    }
}

/** The crown, "Tüm sınavlara sınırsız eriş" and what Premium means. */
@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(icon = Icons.Filled.WorkspacePremium, size = 56.dp)
        Text(
            text = stringResource(R.string.paywall_headline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.paywall_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Benefits() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BenefitRow(R.string.paywall_benefit_all_exams)
        BenefitRow(R.string.paywall_benefit_no_locks)
        BenefitRow(R.string.paywall_benefit_unlimited)
        BenefitRow(R.string.paywall_benefit_cancel)
    }
}

@Composable
private fun BenefitRow(@StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The plan cards once loaded; a spinner meanwhile, or the retry after a failed first load. */
@Composable
private fun PlanList(state: PaywallUiState, onPlanSelected: (String) -> Unit, onRetry: () -> Unit) {
    val options = state.options
    when {
        options != null -> Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { option ->
                PlanCard(
                    option = option,
                    isSelected = option.basePlanId == state.selectedPlanId,
                    enabled = !state.isBusy,
                    onClick = { onPlanSelected(option.basePlanId) },
                )
            }
        }
        state.loadFailed -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.paywall_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("paywall.loadFailed"),
            )
            TextButton(onClick = onRetry, modifier = Modifier.testTag("paywall.retry")) {
                Text(stringResource(R.string.common_try_again))
            }
        }
        else -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val loading = stringResource(R.string.common_loading)
            CircularProgressIndicator(
                modifier = Modifier
                    .semantics { contentDescription = loading }
                    .testTag("paywall.loading"),
            )
        }
    }
}

/** One selectable plan: a card with a selection ring, read by TalkBack as one radio button. */
@Composable
private fun PlanCard(option: SubscriptionOption, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp)
            .testTag("paywall.option.${option.period.testTagSuffix}"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(option.period.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (option.period == SubscriptionPeriod.YEARLY) {
                Text(
                    text = stringResource(R.string.paywall_plan_best_value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = option.displayPrice,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Google Play's renewal and cancellation terms, then the terms of use and the privacy policy (Play policy). */
@Composable
private fun LegalFooter(onOpenTerms: () -> Unit, onOpenPrivacy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.paywall_auto_renew_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenTerms, modifier = Modifier.testTag("paywall.terms")) {
                Text(stringResource(R.string.paywall_terms))
            }
            TextButton(onClick = onOpenPrivacy, modifier = Modifier.testTag("paywall.privacy")) {
                Text(stringResource(R.string.paywall_privacy))
            }
        }
    }
}

/** The bar pinned to the bottom: the last message, "Abone Ol · fiyat" and "Satın alımları geri yükle". */
@Composable
private fun PurchaseBar(state: PaywallUiState, onSubscribe: () -> Unit, onRestore: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val message = state.error ?: state.notice?.let { stringResource(it.messageRes) }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("paywall.error"),
                )
            }
            SubscribeButton(state = state, onSubscribe = onSubscribe)
            TextButton(
                onClick = onRestore,
                enabled = !state.isBusy,
                modifier = Modifier.testTag("paywall.restore"),
            ) {
                if (state.isRestoring) {
                    ProgressIndicator(Modifier.size(18.dp).testTag("paywall.restore.progress"))
                } else {
                    Text(stringResource(R.string.paywall_restore))
                }
            }
        }
    }
}

@Composable
private fun SubscribeButton(state: PaywallUiState, onSubscribe: () -> Unit) {
    val title = state.selectedOption?.let { stringResource(R.string.paywall_subscribe_price, it.displayPrice) }
        ?: stringResource(R.string.paywall_subscribe)
    Button(
        onClick = onSubscribe,
        enabled = state.canSubscribe,
        // Disabled while buying, but kept orange so the spinner stays visible in both themes (iOS dark-mode fix).
        colors = if (state.isPurchasing) {
            ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag("paywall.subscribe"),
    ) {
        if (state.isPurchasing) {
            ProgressIndicator(Modifier.size(22.dp).testTag("paywall.progress"))
        } else {
            Text(text = title, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A small spinner in the surrounding content color, announced as loading. */
@Composable
private fun ProgressIndicator(modifier: Modifier) {
    val loading = stringResource(R.string.common_loading)
    CircularProgressIndicator(
        modifier = modifier.semantics { contentDescription = loading },
        color = LocalContentColor.current,
        strokeWidth = 2.dp,
    )
}

@get:StringRes
private val SubscriptionPeriod.titleRes: Int
    get() = when (this) {
        SubscriptionPeriod.MONTHLY -> R.string.paywall_plan_monthly
        SubscriptionPeriod.YEARLY -> R.string.paywall_plan_yearly
    }

/** The plan card's testTag suffix, aligned with iOS (`paywall.option.yearly`). */
private val SubscriptionPeriod.testTagSuffix: String
    get() = when (this) {
        SubscriptionPeriod.MONTHLY -> "monthly"
        SubscriptionPeriod.YEARLY -> "yearly"
    }

@get:StringRes
private val PaywallNotice.messageRes: Int
    get() = when (this) {
        PaywallNotice.PURCHASE_PENDING -> R.string.paywall_purchase_pending
        PaywallNotice.NOTHING_TO_RESTORE -> R.string.paywall_nothing_to_restore
    }

private val previewPlans = PaywallUiState(
    options = FakeSubscriptionService.SAMPLE_OPTIONS,
    selectedPlanId = StoreProductIds.YEARLY_PLAN,
)

@ViFiThemePreviews
@Composable
private fun PaywallContentPreview() {
    ViFiTheme {
        PaywallContent(previewPlans, {}, {}, {}, {}, {}, {}, {})
    }
}

@ViFiThemePreviews
@Composable
private fun PaywallPurchasingPreview() {
    ViFiTheme {
        PaywallContent(previewPlans.copy(isPurchasing = true), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PaywallLoadingPreview() {
    ViFiTheme {
        PaywallContent(PaywallUiState(), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PaywallLoadFailedPreview() {
    ViFiTheme {
        PaywallContent(
            PaywallUiState(loadFailed = true, error = SubscriptionError.StoreUnavailable.userMessage),
            {}, {}, {}, {}, {}, {}, {},
        )
    }
}
