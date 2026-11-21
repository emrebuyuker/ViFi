package com.salticusteam.vifi.feature.examviewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import kotlinx.coroutines.delay
import java.util.WeakHashMap

/**
 * Keeps exam content out of screenshots, recordings and the app switcher (iOS `screenCaptureProtection`).
 *
 * - FLAG_SECURE on the window while [content] is shown (VIEW-10): screenshots are blocked, recordings and the
 *   recents thumbnail show black. The full-screen pager is drawn in the same window, so it is covered too.
 * - While the screen is mirrored to a presentation display, an explanatory opaque cover (VIEW-11). Local
 *   recording cannot be detected on Android; FLAG_SECURE already blacks it out.
 * - On Android 14+, a 2.5 s "Sınav içeriği korumalıdır" notice when a screenshot is attempted (VIEW-12).
 */
@Composable
fun ScreenCaptureProtection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    SecureWindowEffect(context.findActivity())
    val isMirrored = rememberIsMirrored(context)
    val screenshotCount = rememberScreenshotCount(context.findActivity())

    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (isMirrored) CaptureCover()
        ScreenshotNotice(trigger = screenshotCount)
    }
}

/**
 * FLAG_SECURE is one flag per window, but protected screens overlap during navigation: the file viewer that redirects
 * to the question screen (recents, a notification's "Sınava Git") is disposed only after the question screen has set
 * the flag, so a plain clear on dispose would leave the question screen unprotected (SQA-04). The flag is therefore
 * held by count and cleared when the last protected screen of the window goes away.
 */
@Composable
private fun SecureWindowEffect(activity: Activity?) {
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null && secureWindowHolds.acquire(window)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (window != null && secureWindowHolds.release(window)) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

/** Protected screens currently shown per window; touched only from composition effects (the main thread). */
private val secureWindowHolds = HoldCounter<Window>()

/**
 * Counts overlapping holds per key (weakly, so a destroyed window is not kept). [acquire] is `true` for the first hold
 * of a key (set the flag), [release] is `true` when its last hold goes away (clear it). An unmatched release is ignored.
 */
internal class HoldCounter<K : Any> {

    private val holds = WeakHashMap<K, Int>()

    fun acquire(key: K): Boolean {
        val count = holds[key] ?: 0
        holds[key] = count + 1
        return count == 0
    }

    fun release(key: K): Boolean {
        val count = holds[key] ?: return false
        return if (count <= 1) {
            holds.remove(key)
            true
        } else {
            holds[key] = count - 1
            false
        }
    }
}

/** Whether a presentation display (cast, HDMI, wireless mirroring) is attached. */
@Composable
private fun rememberIsMirrored(context: Context): Boolean {
    val displayManager = remember(context) { context.getSystemService(DisplayManager::class.java) }
    var isMirrored by remember { mutableStateOf(displayManager.hasPresentationDisplay()) }
    DisposableEffect(displayManager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = update()
            override fun onDisplayRemoved(displayId: Int) = update()
            override fun onDisplayChanged(displayId: Int) = update()
            private fun update() {
                isMirrored = displayManager.hasPresentationDisplay()
            }
        }
        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { displayManager?.unregisterDisplayListener(listener) }
    }
    return isMirrored
}

private fun DisplayManager?.hasPresentationDisplay(): Boolean =
    this?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.isNotEmpty() == true

/** Counts screenshots taken while shown (Android 14+); always 0 on older versions. */
@Composable
private fun rememberScreenshotCount(activity: Activity?): Int {
    var count by remember { mutableIntStateOf(0) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && activity != null) {
        DisposableEffect(activity) {
            val callback = Activity.ScreenCaptureCallback { count++ }
            activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
            onDispose { activity.unregisterScreenCaptureCallback(callback) }
        }
    }
    return count
}

/** Opaque cover over the exam while it is mirrored (iOS `CaptureCoverView`). */
@Composable
private fun CaptureCover() {
    Surface(
        modifier = Modifier.fillMaxSize().testTag("exam.captureCover"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = null, // decorative; the title carries the meaning
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.exam_capture_cover_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.exam_capture_cover_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A capsule flashed at the top for 2.5 s each time [trigger] grows (iOS `ScreenshotNoticeView`). */
@Composable
private fun ScreenshotNotice(trigger: Int) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        isVisible = true
        delay(SCREENSHOT_NOTICE_MILLIS)
        isVisible = false
    }
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("exam.screenshotNotice"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.exam_screenshot_notice),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

private const val SCREENSHOT_NOTICE_MILLIS = 2_500L

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
