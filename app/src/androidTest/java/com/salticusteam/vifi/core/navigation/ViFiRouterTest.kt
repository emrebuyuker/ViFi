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

/** Router stack semantics must match iOS `Router` (push / popToRoot / popTo / openExam) and push tap routing. */
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
            ViFiNavHost(navController = navController, home = {}, browse = {}, exam = { _, _ -> }, account = {}, editProfile = {}, about = {})
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

    @Test
    fun showInbox_onTopOfTheInbox_onlyMovesTheHighlight() {
        runOnUi {
            router.showInbox("n-1")
            router.showInbox("n-2")
        }

        assertEquals(listOf("home", "inbox:n-2"), stack())
    }

    @Test
    fun bellOpensWithoutHighlight_aTapMovesIt_andBackLeavesNoInbox() {
        runOnUi { router.push(ViFiRoute.Inbox()) }
        assertEquals(listOf("home", "inbox:-"), stack())

        runOnUi { router.showInbox("n-3") }
        assertEquals(listOf("home", "inbox:n-3"), stack())

        runOnUi { router.pop() }
        assertEquals(listOf("home"), stack())
    }

    @Test
    fun showInbox_overAnotherScreen_stacksOnIt() {
        runOnUi {
            router.push(ViFiRoute.Browse(university))
            router.showInbox(null)
        }

        assertEquals(listOf("home", "browse:itu", "inbox:-"), stack())
    }

    // --- Structured exams (4.2.0, iOS ExamRoutingTests / SQS-01) ---

    @Test
    fun openExam_withQuestions_opensTheQuestionScreenWithItsAncestors() {
        runOnUi { router.openExam(exam, ExamKind.IMAGES, questionCount = 4) }

        val stack = stack()
        assertEquals("questions:itu/muh/blg/blg101/2024-vize:JPG", stack.last())
        assertEquals(exam.depth + 1, stack.size) // home + 4 lists + the exam
    }

    @Test
    fun replaceExamWithQuestions_swapsTheViewer_andBackSkipsIt() {
        runOnUi {
            router.openExam(exam, ExamKind.IMAGES)
            router.replaceExamWithQuestions(ViFiRoute.StructuredExam(exam, ExamKind.IMAGES))
        }
        assertEquals("questions:itu/muh/blg/blg101/2024-vize:JPG", stack().last())
        assertEquals(exam.depth + 1, stack().size)

        runOnUi { router.pop() }
        assertEquals("browse:itu/muh/blg/blg101", stack().last())
    }

    @Test
    fun replaceExamWithQuestions_twice_leavesOneQuestionScreen() {
        val route = ViFiRoute.StructuredExam(exam, ExamKind.IMAGES)
        runOnUi {
            router.openExam(exam, ExamKind.IMAGES)
            router.replaceExamWithQuestions(route)
            router.replaceExamWithQuestions(route)
        }

        assertEquals(1, stack().count { it.startsWith("questions:") })
        assertEquals(exam.depth + 1, stack().size)
    }

    @Test
    fun replaceExamWithQuestions_afterLeavingTheViewer_doesNothing() {
        runOnUi {
            router.openExam(exam, ExamKind.IMAGES)
            router.pop()
            router.replaceExamWithQuestions(ViFiRoute.StructuredExam(exam, ExamKind.IMAGES))
        }

        assertEquals("browse:itu/muh/blg/blg101", stack().last())
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
        destination.hasRoute<ViFiRoute.Inbox>() -> "inbox:" + (toRoute<ViFiRoute.Inbox>().highlightId ?: "-")
        destination.hasRoute<ViFiRoute.Exam>() -> toRoute<ViFiRoute.Exam>().let {
            "exam:" + it.ids.joinToString("/") + ":" + it.kind.raw
        }
        destination.hasRoute<ViFiRoute.StructuredExam>() -> toRoute<ViFiRoute.StructuredExam>().let {
            "questions:" + it.ids.joinToString("/") + ":" + it.kind.raw
        }
        else -> error("Unexpected destination ${destination.route}")
    }
}
