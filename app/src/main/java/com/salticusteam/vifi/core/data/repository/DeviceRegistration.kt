package com.salticusteam.vifi.core.data.repository

/**
 * The sign-out side of this install's push registration, for the screens that sign out or delete the account
 * (iOS `DeviceRegistrationStore.unregister`). Implemented by the app-wide device registration store.
 */
interface DeviceRegistration {

    /**
     * Stops registering this install and removes it from [uid], waiting at most a few seconds. Never throws: a slow
     * or failed removal must not block signing out (the server prunes a device whose token stops working).
     */
    suspend fun unregister(uid: String)
}
