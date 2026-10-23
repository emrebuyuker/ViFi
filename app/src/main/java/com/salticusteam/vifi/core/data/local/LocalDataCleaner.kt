package com.salticusteam.vifi.core.data.local

/**
 * Wipes what the signed-in user left on the device once their account is deleted (iOS `onDeleted` →
 * `fileLoader.purge()`), so a shared device keeps no exam data from a deleted account. Signing out keeps the
 * downloaded files (a re-login needs no re-download); only deletion calls this.
 */
interface LocalDataCleaner {
    /** Removes the recently viewed exams and every downloaded exam file. Never throws. */
    suspend fun purgeAccountData()
}
