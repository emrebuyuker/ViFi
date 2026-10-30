package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.DeviceRegistration

/** Records sign-out removals (mock flavor, tests); never fails, like the real one. */
class FakeDeviceRegistration : DeviceRegistration {

    /** The uids removed, in order. */
    val unregistered = mutableListOf<String>()

    override suspend fun unregister(uid: String) {
        unregistered += uid
    }
}
