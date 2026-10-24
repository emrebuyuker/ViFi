package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AppStoreLink
import com.salticusteam.vifi.core.model.AppUpdate
import com.salticusteam.vifi.core.model.AppUpdateConfig
import com.salticusteam.vifi.core.model.AppUpdateStatus
import com.salticusteam.vifi.core.util.AppVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Derives the update requirement from the live remote policy (iOS `RemoteAppUpdateChecker`).
 *
 * A required update is latched: once the gate is up, a later unreadable policy (`null`) keeps it up, and only
 * a successful read that proves the installed version satisfies the policy lifts it. Required wins over optional.
 * [fallbackStoreUrl] is used only when the policy omits `storeUrl`.
 */
class RemoteAppUpdateChecker(
    private val reader: AppConfigReader,
    private val installedVersion: String,
    private val fallbackStoreUrl: String? = AppStoreLink.PRODUCT_URL,
) : AppUpdateChecker {

    override fun statusChanges(): Flow<AppUpdateStatus> = flow {
        var latched: AppUpdate? = null
        reader.configChanges().collect { config ->
            val status = if (config != null) {
                status(config, installedVersion, fallbackStoreUrl)
            } else {
                latched?.let { AppUpdateStatus.Required(it) } ?: AppUpdateStatus.UpToDate
            }
            if (status is AppUpdateStatus.Required) {
                latched = status.update
            } else if (config != null) {
                latched = null
            }
            emit(status)
        }
    }.distinctUntilChanged()

    companion object {
        /**
         * The requirement a readable [config] puts on [installedVersion]; required wins over optional. Up to date
         * when there is nowhere to send the user (no `storeUrl` and no fallback): a prompt without a destination
         * would be a dead end.
         */
        fun status(
            config: AppUpdateConfig,
            installedVersion: String,
            fallbackStoreUrl: String? = AppStoreLink.PRODUCT_URL,
        ): AppUpdateStatus {
            val storeUrl = config.storeUrl ?: fallbackStoreUrl ?: return AppUpdateStatus.UpToDate
            val minimum = config.minimumVersion
            val latest = config.latestVersion
            return when {
                minimum != null && AppVersion.isNewer(minimum, installedVersion) ->
                    AppUpdateStatus.Required(AppUpdate(minimum, storeUrl, config.message))
                latest != null && AppVersion.isNewer(latest, installedVersion) ->
                    AppUpdateStatus.Optional(AppUpdate(latest, storeUrl, config.message))
                else -> AppUpdateStatus.UpToDate
            }
        }
    }
}
