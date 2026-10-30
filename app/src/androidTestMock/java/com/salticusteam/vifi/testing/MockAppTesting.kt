package com.salticusteam.vifi.testing

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.salticusteam.vifi.app.MainActivity
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
import java.io.File
import org.junit.Assert.fail

/**
 * Launches the mock app the way a tap on its icon would, with iOS's scenario arguments as intent extras
 * (`-ViFiAdmin`, `-ViFiOpenNotification p-paywall`, …).
 */
object MockApp {

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun launch(vararg flags: LaunchArg, openNotification: String? = null): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java)
        flags.forEach { intent.putExtra(it.extraKey, true) }
        openNotification?.let { intent.putExtra(LaunchArg.OPEN_NOTIFICATION.extraKey, it) }
        // MainActivity reads them too; set here as well so anything the test injects first sees the same scenario.
        LaunchArgs.update(intent.extras)
        return ActivityScenario.launch(intent)
    }

    /** Back to "no scenario", so a later test class that never launches the app does not inherit one. */
    fun reset() {
        LaunchArgs.update(null)
    }
}

/**
 * WorkManager for the instrumented app: the test application is Hilt's, not `VifiApplication`, so nothing configures
 * it and the exam form (whose upload queue it is) could not open. Initialized once per process; each test hands it
 * the worker factory of its own Hilt graph.
 */
object TestWorkManager {

    private object CurrentFactory : WorkerFactory() {
        @Volatile
        var delegate: WorkerFactory? = null

        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = delegate?.createWorker(appContext, workerClassName, workerParameters)
    }

    fun install(factory: WorkerFactory) {
        CurrentFactory.delegate = factory
        synchronized(this) {
            if (!WorkManager.isInitialized()) {
                val configuration = Configuration.Builder().setWorkerFactory(CurrentFactory).build()
                WorkManager.initialize(MockApp.context, configuration)
            }
        }
    }
}

/**
 * The document picker answers with [file] instead of showing the system UI (what Espresso-Intents' stubbing does,
 * without the extra dependency): the result reaches the form's own `OpenDocument` launcher. Remove it with
 * [removePickerAnswer].
 */
fun answerDocumentPicker(file: File): Instrumentation.ActivityMonitor {
    val result = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
    val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
            result.takeIf { intent.action == Intent.ACTION_OPEN_DOCUMENT }
    }
    InstrumentationRegistry.getInstrumentation().addMonitor(monitor)
    return monitor
}

fun removePickerAnswer(monitor: Instrumentation.ActivityMonitor) {
    InstrumentationRegistry.getInstrumentation().removeMonitor(monitor)
}

/** Nodes whose test tag starts with [prefix]: rows keyed by an id the test does not know (a new send). */
fun hasTestTagPrefix(prefix: String): SemanticsMatcher = SemanticsMatcher("TestTag starts with '$prefix'") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
}

/** Waits for exactly one node tagged [tag] (iOS `waitForExistence`). */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForTag(tag: String, timeoutMillis: Long = TIMEOUT_MS) {
    waitUntilExactlyOneExists(hasTestTag(tag), timeoutMillis)
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForTagGone(tag: String, timeoutMillis: Long = TIMEOUT_MS) {
    waitUntilDoesNotExist(hasTestTag(tag), timeoutMillis)
}

/**
 * Fails as soon as a node tagged [tag] shows up within [forMillis]: for what must *not* happen after a delay (a
 * re-ask that waits 700 ms, an inbox that must not reopen). Polls the UI rather than sleeping blind.
 */
fun ComposeTestRule.assertNeverAppears(tag: String, forMillis: Long = QUIET_MS) {
    val deadline = SystemClock.uptimeMillis() + forMillis
    while (SystemClock.uptimeMillis() < deadline) {
        waitForIdle()
        if (onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) fail("'$tag' appeared within $forMillis ms")
        SystemClock.sleep(POLL_MS)
    }
}

/** Scrolls the lazy list tagged [listTag] to the node tagged [tag] and taps it. */
fun ComposeTestRule.scrollToAndClick(listTag: String, tag: String) {
    onNodeWithTag(listTag).performScrollToNode(hasTestTag(tag))
    onNodeWithTag(tag).performClick()
}

const val TIMEOUT_MS = 10_000L
private const val QUIET_MS = 2_000L
private const val POLL_MS = 100L
