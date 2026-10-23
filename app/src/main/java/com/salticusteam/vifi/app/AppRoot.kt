package com.salticusteam.vifi.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.navigation.ViFiNavHost
import com.salticusteam.vifi.core.navigation.ViFiRoute
import com.salticusteam.vifi.core.navigation.ViFiRouter
import com.salticusteam.vifi.core.navigation.rememberViFiRouter
import com.salticusteam.vifi.feature.browse.BrowseScreen

/**
 * The app's root composable (iOS RootView). Today it hosts the archive navigation stack; the gates that wrap
 * it on iOS — required update (forceupdate), sign-in (auth) and profile completion (profile) — are layered
 * here as their roadmap items land, as are the lifecycle hooks (badge clearing, disk-cache pruning).
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val router = rememberViFiRouter(navController)

    ViFiNavHost(
        navController = navController,
        modifier = Modifier.fillMaxSize(),
        home = {
            BrowseScreen(onItemClick = { router.open(it) }, onBack = null)
        },
        browse = { _ ->
            BrowseScreen(onItemClick = { router.open(it) }, onBack = { router.pop() })
        },
        exam = { path, kind ->
            // Placeholder until the exam viewers (VIEW-01/02) land.
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { ViFiTopAppBar(title = path.displayName.orEmpty(), onBack = { router.pop() }) },
            ) { padding ->
                EmptyView(
                    title = path.displayName.orEmpty(),
                    icon = kind.icon,
                    message = stringResource(R.string.exam_viewer_coming_soon),
                    modifier = Modifier.padding(padding),
                )
            }
        },
    )
}

/** Drills into a list entry, or opens an exam that has files. */
private fun ViFiRouter.open(item: ArchiveItem) {
    val exam = item.exam
    when {
        item.level != ArchiveLevel.EXAM -> push(ViFiRoute.Browse(item.path))
        exam != null -> push(ViFiRoute.Exam(item.path, exam.kind))
    }
}
