package com.salticusteam.vifi.core.navigation

import android.annotation.SuppressLint
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Router stack semantics must match iOS `Router` (push / popToRoot / popTo / openExam). */
@RunWith(AndroidJUnit4::class)
class ViFiRouterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController
    private lateinit var router: ViFiRouter

    private val university = ArchivePath.ROOT.appending("itu")
    private val faculty = university.appending("muh")
    private val department = faculty.appending("blg")
    private val lesson = department.appending("blg101")
    private val exam = lesson.appending("2024-vize")

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            ViFiNavHost(navController = navController, home = {}, browse = {}, exam = { _, _ -> }, editProfile = {})
        }
        router = ViFiRouter(navController)
    }

    @Test
    fun push_appendsBrowseRoutes() {
        runOnUi {
            router.push(ViFiRoute.Browse(university))
            router.push(ViFiRoute.Browse(faculty))
        }

        assertEquals(listOf("home", "browse:itu", "browse:itu/muh"), stack())
    }

    @Test
    fun openExam_putsWholeHierarchyOnStack() {
        runOnUi { router.openExam(exam, ExamKind.PDF) }

        assertEquals(
            listOf(
                "home",
                "browse:itu",
                "browse:itu/muh",
                "browse:itu/muh/blg",
                "browse:itu/muh/blg/blg101",
                "exam:itu/muh/blg/blg101/2024-vize:PDF",
            ),
            stack(),
        )
    }

    @Test
    fun backFromExam_walksUpTheArchive() {
        runOnUi {
            router.openExam(exam, ExamKind.IMAGES)
            router.pop()
        }

        assertEquals("browse:itu/muh/blg/blg101", stack().last())
    }

    @Test
    fun popTo_keepsAncestors() {
        runOnUi {
            router.openExam(exam, ExamKind.PDF)
            router.popTo(faculty)
        }

        assertEquals(listOf("home", "browse:itu", "browse:itu/muh"), stack())
    }

    @Test
    fun popTo_rebuildsHierarchyWhenEntryIsMissing() {
        runOnUi { router.popTo(department) }

        assertEquals(listOf("home", "browse:itu", "browse:itu/muh", "browse:itu/muh/blg"), stack())
    }

    @Test
    fun popToRoot_andPopToRootPath_returnHome() {
        runOnUi {
            router.openExam(exam, ExamKind.PDF)
            router.popToRoot()
        }
        assertEquals(listOf("home"), stack())

        runOnUi {
            router.push(ViFiRoute.Browse(university))
            router.popTo(ArchivePath.ROOT)
        }
        assertEquals(listOf("home"), stack())
    }

    private fun runOnUi(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        composeRule.waitForIdle()
    }

    @SuppressLint("RestrictedApi")
    private fun stack(): List<String> =
        navController.currentBackStack.value
            .filter { it.destination.route != null }
            .map { it.describe() }

    private fun NavBackStackEntry.describe(): String = when {
        destination.hasRoute<ViFiRoute.Home>() -> "home"
        destination.hasRoute<ViFiRoute.Browse>() -> "browse:" + toRoute<ViFiRoute.Browse>().ids.joinToString("/")
        destination.hasRoute<ViFiRoute.Exam>() -> toRoute<ViFiRoute.Exam>().let {
            "exam:" + it.ids.joinToString("/") + ":" + it.kind.raw
        }
        else -> error("Unexpected destination ${destination.route}")
    }
}
