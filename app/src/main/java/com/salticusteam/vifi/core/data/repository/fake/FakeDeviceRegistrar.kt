package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.DeviceRegistrar

/** Device registrations kept in memory; a set failure is thrown instead, to exercise the error paths. */
class FakeDeviceRegistrar : DeviceRegistrar {

    /** The token currently registered per uid. */
    val tokens = mutableMapOf<String, String>()

    /** Every successful registration, in order, as `uid to token`. */
    val registrations = mutableListOf<Pair<String, String>>()

    /** Every successful removal, in order (the uids). */
    val unregistrations = mutableListOf<String>()

    var registerFailure: Exception? = null
    var unregisterFailure: Exception? = null

    override suspend fun register(uid: String, token: String) {
        registerFailure?.let { throw it }
        tokens[uid] = token
        registrations += uid to token
    }

    override suspend fun unregister(uid: String) {
        unregisterFailure?.let { throw it }
        tokens.remove(uid)
        unregistrations += uid
    }
}
