package com.salticusteam.vifi.core.maps

import com.salticusteam.vifi.BuildConfig

/**
 * The Google Maps / Places API key the build was given, or nothing when it was built without one (iOS
 * `GoogleMapsConfiguration`).
 *
 * The key is never in the repository: `MAPS_API_KEY` in local.properties (git-ignored) reaches the manifest's
 * `com.google.android.geo.API_KEY` (read by the Maps SDK) and [BuildConfig.MAPS_API_KEY] (handed to Places). A
 * checkout without it still builds; the value is then empty, and the location picker reports the map as not
 * configured instead of letting the SDKs fail.
 */
class GoogleMapsConfiguration private constructor(
    /** The usable key, or `null` when none was configured. */
    val apiKey: String?,
) {

    val isAvailable: Boolean get() = apiKey != null

    // Never prints the key: it would end up in logs and crash reports.
    override fun toString(): String = "GoogleMapsConfiguration(isAvailable=$isAvailable)"

    companion object {

        /** The configuration of the running app. */
        val current: GoogleMapsConfiguration by lazy { from(BuildConfig.MAPS_API_KEY) }

        /** The trimmed key, unless it is missing, blank, not text, or a placeholder that was never filled in. */
        fun from(value: Any?): GoogleMapsConfiguration {
            val text = (value as? String)?.trim()?.takeIf { it.isNotEmpty() }
            // "${MAPS_API_KEY}" (or iOS's "$(…)") stays literal when the build did not substitute it.
            val usable = text?.takeUnless { it.startsWith("\${") || it.startsWith("$(") }
            return GoogleMapsConfiguration(usable)
        }
    }
}
