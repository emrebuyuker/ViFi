package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.AppUpdateChecker
import com.salticusteam.vifi.core.model.AppStoreLink
import com.salticusteam.vifi.core.model.AppUpdate
import com.salticusteam.vifi.core.model.AppUpdateStatus
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Sample update policy: "no update" unless a launch argument asks for the blocking gate (`FORCE_UPDATE`, iOS
 * -ViFiForceUpdate) or the dismissible prompt (`OPTIONAL_UPDATE`); the gate wins when both are set.
 */
class FakeAppUpdateChecker(
    private val status: () -> AppUpdateStatus = {
        when {
            LaunchArgs.isSet(LaunchArg.FORCE_UPDATE) ->
                AppUpdateStatus.Required(AppUpdate(version = "99.0", storeUrl = AppStoreLink.PRODUCT_URL, message = null))
            LaunchArgs.isSet(LaunchArg.OPTIONAL_UPDATE) ->
                AppUpdateStatus.Optional(AppUpdate(version = "99.0", storeUrl = AppStoreLink.PRODUCT_URL, message = null))
            else -> AppUpdateStatus.UpToDate
        }
    },
) : AppUpdateChecker {

    override fun statusChanges(): Flow<AppUpdateStatus> = flowOf(status())
}
