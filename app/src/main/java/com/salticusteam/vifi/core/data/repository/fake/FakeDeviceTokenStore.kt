package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.DeviceTokenStore

/** Keeps registered tokens in memory; [failure] is thrown instead, to exercise the error path. */
class FakeDeviceTokenStore : DeviceTokenStore {

    /** Every successful registration, in order, as `uid to token`. */
    val registrations = mutableListOf<Pair<String, String>>()

    var failure: Exception? = null

    override suspend fun register(uid: String, token: String) {
        failure?.let { throw it }
        registrations += uid to token
    }
}
