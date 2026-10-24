package com.salticusteam.vifi.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme

/**
 * Lays an opaque branded cover over the whole app whenever the host Activity is not resumed (iOS RootView's
 * `AppSwitcherCover` while `scenePhase != .active`), so no screen — profile, moderation, inbox, exam pages —
 * appears in the recent-apps snapshot. Shown without animation: the snapshot is taken as the app leaves the
 * foreground. Android 13+ additionally turns the recents screenshot off ([MainActivity]); the exam viewers keep
 * their own `FLAG_SECURE` protection.
 */
@Composable
fun AppSwitcherCover(content: @Composable () -> Unit) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            AppSwitcherCoverContent()
        }
    }
}

@Composable
private fun AppSwitcherCoverContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Purely visual: nothing to read out while the app is leaving the foreground.
            .clearAndSetSemantics {}
            .testTag("app.switcherCover"),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.icon),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppSwitcherCoverPreview() {
    ViFiTheme {
        AppSwitcherCoverContent()
    }
}
