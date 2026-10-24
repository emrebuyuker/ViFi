package com.salticusteam.vifi.core.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Pins the app's resources to Turkish whatever the device language is (LOC-01). The app's own strings are
 * Turkish in the default `values/`, but library resources (Material components, accessibility labels) follow
 * the configuration's locale; with only `tr` packaged, an English device would otherwise fall back to their
 * English defaults. Only the resource configuration changes: `Locale.getDefault()` is left alone, so
 * locale-sensitive code elsewhere (e.g. `lowercase()` on ids) keeps its behavior.
 */
object AppLocale {

    val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

    /** [base] with its resources resolved in Turkish; the same context when it already is. */
    fun wrap(base: Context): Context {
        val current = base.resources.configuration
        if (current.locales[0]?.language == TURKISH.language) return base
        val pinned = Configuration(current).apply { setLocale(TURKISH) }
        return base.createConfigurationContext(pinned)
    }
}
