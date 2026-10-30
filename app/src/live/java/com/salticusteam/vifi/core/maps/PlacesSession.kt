package com.salticusteam.vifi.core.maps

/**
 * A Places search session: what Google bills a search by. The suggestions fetched while typing and the one place
 * fetch that follows share a token; the fetch ends the session ([restart]). A token is renewed before a search
 * once it is older than [lifetimeMillis], so a long pause does not send a stale one (iOS `GooglePlacesService`).
 */
internal class PlacesSession<Token : Any>(
    private val lifetimeMillis: Long,
    private val newToken: () -> Token,
    private val now: () -> Long,
) {

    /** The token of the session under way. */
    var token: Token = newToken()
        private set

    private var startedAt = now()

    /** Starts a new session if this one has lasted longer than its lifetime. */
    fun renewIfStale() {
        if (now() - startedAt > lifetimeMillis) restart()
    }

    /** Ends the session; the next request starts a new one. */
    fun restart() {
        token = newToken()
        startedAt = now()
    }
}
