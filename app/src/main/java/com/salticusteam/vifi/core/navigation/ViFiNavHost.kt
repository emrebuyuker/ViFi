package com.salticusteam.vifi.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind

/**
 * The app's single navigation graph. Destinations are slots so the host (AppRoot, FM-22) plugs in the
 * feature screens without this file depending on them; each slot receives its decoded route arguments.
 */
@Composable
fun ViFiNavHost(
    navController: NavHostController,
    home: @Composable () -> Unit,
    browse: @Composable (path: ArchivePath) -> Unit,
    exam: @Composable (path: ArchivePath, kind: ExamKind) -> Unit,
    account: @Composable () -> Unit,
    editProfile: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ViFiRoute.Home,
        modifier = modifier,
    ) {
        composable<ViFiRoute.Home> {
            home()
        }
        composable<ViFiRoute.Browse> { entry ->
            browse(entry.toRoute<ViFiRoute.Browse>().path)
        }
        composable<ViFiRoute.Exam> { entry ->
            val route = entry.toRoute<ViFiRoute.Exam>()
            exam(route.path, route.kind)
        }
        composable<ViFiRoute.Account> {
            account()
        }
        composable<ViFiRoute.EditProfile> {
            editProfile()
        }
    }
}
