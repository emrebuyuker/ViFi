package com.salticusteam.vifi.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.ContactSupportDialog
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.component.rememberContactSupportLauncher
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.AnalyticsValues
import com.salticusteam.vifi.feature.onboarding.OnboardingScreen

/**
 * App information (iOS AboutView), opened from the account screen: logo, version, what ViFi offers, where its data
 * comes from, the tour replay and "Bize Ulaş". The replay never touches the first-launch flag.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    var isShowingTour by rememberSaveable { mutableStateOf(false) }
    val contactSupport = rememberContactSupportLauncher()

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    AboutContent(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toString(),
        onReplayTour = { isShowingTour = true },
        onContact = { contactSupport.open() },
        onBack = onBack,
    )

    if (isShowingTour) {
        OnboardingScreen(
            source = AnalyticsValues.ONBOARDING_SOURCE_REPLAY,
            onFinish = { isShowingTour = false },
        )
    }
    ContactSupportDialog(contactSupport)
}

/** Stateless About content; previews and UI tests drive this. */
@Composable
fun AboutContent(
    versionName: String,
    versionCode: String,
    onReplayTour: () -> Unit,
    onContact: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.about_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("about.list"),
        ) {
            item(key = "identity") { IdentitySection(versionName, versionCode) }
            item(key = "description") {
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("about.description"),
                )
            }
            item(key = "highlights") {
                SectionHeader(stringResource(R.string.about_highlights_header))
                HighlightRow(Icons.Filled.Description, R.string.about_highlight_formats)
                HighlightRow(Icons.Filled.ZoomIn, R.string.about_highlight_zoom)
                HighlightRow(Icons.Filled.History, R.string.about_highlight_recents)
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
            item(key = "replay") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_replay_tour)) },
                    leadingContent = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onReplayTour)
                        .testTag("about.replayOnboarding"),
                )
                HorizontalDivider()
            }
            item(key = "dataSource") {
                SectionHeader(stringResource(R.string.about_data_source_header))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_data_source_message)) },
                    leadingContent = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.testTag("about.dataSource"),
                )
                HorizontalDivider()
            }
            item(key = "contact") {
                SectionHeader(stringResource(R.string.about_contact_header))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_contact_button)) },
                    leadingContent = { Icon(Icons.Filled.Email, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onContact)
                        .testTag("about.contact"),
                )
                Text(
                    text = stringResource(R.string.about_contact_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Logo, name, subtitle and the version capsule, read as one element. */
@Composable
private fun IdentitySection(versionName: String, versionCode: String) {
    // iOS ScaledMetric(84) capped at 120.
    val logoSize = minOf((84 * LocalDensity.current.fontScale).dp, 120.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.mipmap.icon),
            contentDescription = null, // decorative; the name carries the meaning
            modifier = Modifier
                .size(logoSize)
                .shadow(8.dp, RoundedCornerShape(logoSize * 0.225f))
                .clip(RoundedCornerShape(logoSize * 0.225f)),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.about_app_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.about_version, versionName, versionCode),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag("about.version"),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun HighlightRow(icon: ImageVector, label: Int) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { IconBadge(icon = icon, size = 36.dp) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@ViFiThemePreviews
@Composable
private fun AboutContentPreview() {
    ViFiTheme {
        AboutContent(versionName = "2.5", versionCode = "16", onReplayTour = {}, onContact = {}, onBack = {})
    }
}
