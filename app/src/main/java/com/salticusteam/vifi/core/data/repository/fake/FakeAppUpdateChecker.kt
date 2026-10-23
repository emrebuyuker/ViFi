package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.AppUpdateChecker
import com.salticusteam.vifi.core.model.AppStoreLink
import com.salticusteam.vifi.core.model.AppUpdate
import com.salticusteam.vifi.core.model.AppUpdateStatus
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Sample update policy: "no update" unless the `FORCE_UPDATE` launch argument asks for the gate (iOS -ViFiForceUpdate). */
class FakeAppUpdateChecker(
    private val status: () -> AppUpdateStatus = {
        if (LaunchArgs.isSet(LaunchArg.FORCE_UPDATE)) {
            AppUpdateStatus.Required(AppUpdate(version = "99.0", storeUrl = AppStoreLink.PRODUCT_URL, message = null))
        } else {
            AppUpdateStatus.UpToDate
        }
    },
) : AppUpdateChecker {

    override fun statusChanges(): Flow<AppUpdateStatus> = flowOf(status())
}
