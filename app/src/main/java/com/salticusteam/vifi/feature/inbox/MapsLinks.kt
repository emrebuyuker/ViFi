package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.model.NotificationLocation
import java.util.Locale

/**
 * The links that open a campaign's place (iOS `MapsLinks`, NDEC-03): Google Maps (the app when installed, else the
 * browser, with the same URL) and any other maps app through a `geo:` link. Pure, so the exact URLs are unit-tested.
 */
internal object MapsLinks {

    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /** `https://www.google.com/maps/search/?api=1&query=<lat>%2C<lng>[&query_place_id=<id>]`. */
    fun googleMaps(location: NotificationLocation): String = buildString {
        append("https://www.google.com/maps/search/?api=1&query=")
        append(coordinate(location.latitude)).append("%2C").append(coordinate(location.longitude))
        location.placeId?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&query_place_id=").append(encode(it)) }
    }

    /** `geo:<lat>,<lng>?q=<lat>,<lng>(<name>)`: the pin with its label, in whichever maps app the user picks. */
    fun geo(location: NotificationLocation): String {
        val point = coordinate(location.latitude) + "," + coordinate(location.longitude)
        val label = location.name.trim().takeIf { it.isNotEmpty() }?.let { "(" + encode(it) + ")" }.orEmpty()
        return "geo:$point?q=$point$label"
    }

    /** Six decimals at most, trailing zeros and dot dropped, `-0` as `0`, never grouped by the locale. */
    fun coordinate(value: Double): String {
        val text = String.format(Locale.ROOT, "%.6f", value).trimEnd('0').trimEnd('.')
        return if (text == "-0") "0" else text
    }

    /** Keeps only `A-Z a-z 0-9 - . _ ~`; everything else is UTF-8 `%XX` (so `&`, `+`, `#` and `%` too). */
    fun encode(text: String): String = buildString {
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val char = byte.toInt().toChar()
            val unreserved = byte >= 0 && (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~")
            if (unreserved) append(char) else append('%').append("%02X".format(byte.toInt() and 0xFF))
        }
    }
}
