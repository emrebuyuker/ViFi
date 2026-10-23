package com.salticusteam.vifi.core.util

import android.util.Log
import timber.log.Timber

/**
 * Log categories (iOS `Logger.archive` / `.files` / `.app`). Use `LogCategory.ARCHIVE.log.e(error, "…")`;
 * never call android.util.Log or println directly in new code.
 *
 * Privacy (iOS `.private`): never put phone numbers, tokens, uids or other personal data in a message.
 */
enum class LogCategory(val tag: String) {
    ARCHIVE("Archive"),
    FILES("Files"),
    APP("App");

    /** Timber tree tagged with this category for the next call. */
    val log: Timber.Tree get() = Timber.tag(tag)
}

/** Planted once at start-up: full logs in debug, warnings and errors only in release. */
object Logging {

    fun install(debug: Boolean) {
        if (Timber.treeCount > 0) return
        Timber.plant(if (debug) Timber.DebugTree() else ReleaseTree())
    }
}

/** Release builds keep only warnings and errors, so routine debug output never reaches device logs. */
class ReleaseTree : Timber.Tree() {

    public override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Log.println(priority, tag ?: "ViFi", message)
    }
}
