package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.AppUpdateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtdbAppConfigReaderTest {

    @Test
    fun `parse keeps every well formed field`() {
        val config = RtdbAppConfigReader.parse(
            mapOf(
                "minimumVersion" to "3.0",
                "latestVersion" to "3.1",
                "storeUrl" to "https://play.google.com/store/apps/details?id=com.salticusteam.vifi",
                "message" to "Güncelle",
            ),
        )
        assertEquals(
            AppUpdateConfig("3.0", "3.1", "https://play.google.com/store/apps/details?id=com.salticusteam.vifi", "Güncelle"),
            config,
        )
    }

    @Test
    fun `parse drops empty and non string fields`() {
        val config = RtdbAppConfigReader.parse(mapOf("minimumVersion" to "", "latestVersion" to 3, "message" to "x"))
        assertEquals(AppUpdateConfig(message = "x"), config)
    }

    @Test
    fun `parse drops an invalid store url`() {
        assertEquals(
            AppUpdateConfig(minimumVersion = "3.0"),
            RtdbAppConfigReader.parse(mapOf("minimumVersion" to "3.0", "storeUrl" to "not a url")),
        )
        assertEquals(
            AppUpdateConfig(minimumVersion = "3.0"),
            RtdbAppConfigReader.parse(mapOf("minimumVersion" to "3.0", "storeUrl" to "javascript:alert(1)")),
        )
    }

    @Test
    fun `parse when node is missing or has no usable field returns null`() {
        assertNull(RtdbAppConfigReader.parse(null))
        assertNull(RtdbAppConfigReader.parse("text"))
        assertNull(RtdbAppConfigReader.parse(emptyMap<String, Any>()))
        assertNull(RtdbAppConfigReader.parse(mapOf("minimumVersion" to "")))
    }
}
