package com.salticusteam.vifi.core.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `FirebaseDeviceRegistrarTests`' document cases: the shared rules accept exactly this shape. */
class FirestoreDeviceRegistrarTest {

    private val serverTime = Any()

    @Test
    fun `the device document has exactly the four fields the rules allow`() {
        val document = FirestoreDeviceRegistrar.document("t-1", "4.1.0", serverTime)

        assertEquals(setOf("token", "platform", "appVersion", "updatedAt"), document.keys)
        assertEquals("t-1", document["token"])
        assertEquals("android", document["platform"])
        assertEquals("4.1.0", document["appVersion"])
        assertEquals(serverTime, document["updatedAt"])
    }

    @Test
    fun `the app version is clipped to 32 characters`() {
        val document = FirestoreDeviceRegistrar.document("t-1", "4.1.0-" + "x".repeat(40), serverTime)

        assertEquals(32, (document["appVersion"] as String).length)
    }
}
