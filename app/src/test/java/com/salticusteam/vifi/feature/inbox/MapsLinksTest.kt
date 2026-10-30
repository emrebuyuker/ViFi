package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.model.NotificationLocation
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `MapsLinksTests` (9), with `geo:` in place of Apple Maps (NDEC-03). */
class MapsLinksTest {

    private val cafe = NotificationLocation(40.9903, 29.0290, "ViFi Öğrenci Kafesi", "Caferağa, Kadıköy, İstanbul")

    @Test
    fun `Google Maps opens the place by coordinates`() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=40.9903%2C29.029", MapsLinks.googleMaps(cafe))
    }

    @Test
    fun `Google Maps carries the place id when there is one`() {
        val place = NotificationLocation(41.0082, 28.9784, "Sultanahmet", placeId = " ChIJ9z1-abc_DEF ")

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=41.0082%2C28.9784&query_place_id=ChIJ9z1-abc_DEF",
            MapsLinks.googleMaps(place),
        )
    }

    @Test
    fun `a blank place id is left out and an odd one is escaped`() {
        assertEquals(MapsLinks.googleMaps(cafe), MapsLinks.googleMaps(cafe.copy(placeId = "  ")))
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=40.9903%2C29.029&query_place_id=a%2Fb%26c",
            MapsLinks.googleMaps(cafe.copy(placeId = "a/b&c")),
        )
    }

    @Test
    fun `the Google Maps app is asked for by package, with the same URL`() {
        assertEquals("com.google.android.apps.maps", MapsLinks.GOOGLE_MAPS_PACKAGE)
        assertEquals("https", MapsLinks.googleMaps(cafe).substringBefore(':'))
    }

    @Test
    fun `another maps app gets a geo link with the escaped name as the label`() {
        assertEquals(
            "geo:40.9903,29.029?q=40.9903,29.029(ViFi%20%C3%96%C4%9Frenci%20Kafesi)",
            MapsLinks.geo(cafe),
        )
    }

    @Test
    fun `a geo link without a name has no label`() {
        assertEquals("geo:40.9903,29.029?q=40.9903,29.029", MapsLinks.geo(cafe.copy(name = "  ")))
    }

    @Test
    fun `escaping keeps only the unreserved characters and round-trips`() {
        val name = "Çay & Kahve +%20#1"
        val encoded = MapsLinks.encode(name)

        assertEquals("%C3%87ay%20%26%20Kahve%20%2B%2520%231", encoded)
        assertEquals(name, URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8"))
    }

    @Test
    fun `the place name is trimmed before it is escaped`() {
        assertEquals(
            "geo:40.9903,29.029?q=40.9903,29.029(Kafe)",
            MapsLinks.geo(cafe.copy(name = "  Kafe  ")),
        )
    }

    @Test
    fun `coordinates use at most six decimals, no trailing zeros and never -0`() {
        assertEquals("-33.8688", MapsLinks.coordinate(-33.8688))
        assertEquals("29", MapsLinks.coordinate(29.0))
        assertEquals("0", MapsLinks.coordinate(-0.0000001))
        assertEquals("0", MapsLinks.coordinate(0.0))
        assertEquals("41.123457", MapsLinks.coordinate(41.1234567))
        assertEquals("0.00001", MapsLinks.coordinate(0.00001))
        assertEquals("-180", MapsLinks.coordinate(-180.0))
    }
}
