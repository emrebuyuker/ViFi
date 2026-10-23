package com.salticusteam.vifi.core.util

/**
 * How long ago something happened, bucketed for a Turkish "… önce" label. Pure and locale-free: the UI maps
 * each case to a string resource, so the label stays Turkish whatever the device language is (platform
 * `DateUtils.getRelativeTimeSpanString` follows the system locale). A time in the future (clock skew) counts as now.
 */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val count: Int) : RelativeTime
    data class Hours(val count: Int) : RelativeTime
    data class Days(val count: Int) : RelativeTime
    data class Weeks(val count: Int) : RelativeTime
    data class Months(val count: Int) : RelativeTime
    data class Years(val count: Int) : RelativeTime

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR
        private const val WEEK = 7 * DAY
        private const val MONTH = 30 * DAY
        private const val YEAR = 365 * DAY

        /** Buckets the gap between [thenMillis] and [nowMillis] (both epoch milliseconds). */
        fun between(thenMillis: Long, nowMillis: Long): RelativeTime {
            val elapsed = (nowMillis - thenMillis).coerceAtLeast(0)
            return when {
                elapsed < MINUTE -> JustNow
                elapsed < HOUR -> Minutes((elapsed / MINUTE).toInt())
                elapsed < DAY -> Hours((elapsed / HOUR).toInt())
                elapsed < WEEK -> Days((elapsed / DAY).toInt())
                elapsed < MONTH -> Weeks((elapsed / WEEK).toInt())
                elapsed < YEAR -> Months((elapsed / MONTH).toInt())
                else -> Years((elapsed / YEAR).toInt())
            }
        }
    }
}
