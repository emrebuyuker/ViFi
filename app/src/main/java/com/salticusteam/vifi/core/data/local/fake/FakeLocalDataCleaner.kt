package com.salticusteam.vifi.core.data.local.fake

import com.salticusteam.vifi.core.data.local.LocalDataCleaner

/** Records purges instead of touching the disk; for previews and tests. */
class FakeLocalDataCleaner : LocalDataCleaner {

    /** How many times [purgeAccountData] ran. */
    var purgeCount = 0
        private set

    override suspend fun purgeAccountData() {
        purgeCount++
    }
}
