package com.salticusteam.vifi.core.model

// Added in 2.5 — a release the user can move to, and where to get it.
data class AppUpdate(
    val version: String,
    val storeUrl: String,
    /** A message from the remote config, shown instead of the default copy; `null` uses the default. */
    val message: String?,
)

// Added in 2.5 — the update requirement for the installed version.
sealed interface AppUpdateStatus {
    /** Up to date, or the policy could not be read. */
    data object UpToDate : AppUpdateStatus

    /** A newer version exists; the user may keep using the app (a dismissible prompt). */
    data class Optional(val update: AppUpdate) : AppUpdateStatus

    /** The installed version is below the required minimum; the app is blocked until it updates. */
    data class Required(val update: AppUpdate) : AppUpdateStatus
}

// Added in 2.5 — the remote update policy for this platform (`config/appUpdate/android`), every field optional.
/**
 * A missing [minimumVersion] disables the hard gate, a missing [latestVersion] disables the optional prompt,
 * and a missing [storeUrl] falls back to [AppStoreLink.PRODUCT_URL].
 */
data class AppUpdateConfig(
    val minimumVersion: String? = null,
    val latestVersion: String? = null,
    val storeUrl: String? = null,
    val message: String? = null,
)

// Added in 2.5 — where to send a user who needs to update.
object AppStoreLink {
    /** The app's Play Store page, used only when the remote config omits `storeUrl` (the built-in safety net). */
    const val PRODUCT_URL = "https://play.google.com/store/apps/details?id=com.salticusteam.vifi"
}
