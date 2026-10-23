package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-time Realtime Database configuration (iOS `ViFiDatabase.shared` parity): on-disk persistence with a
 * 50 MB cache, so the archive and config stay readable offline.
 *
 * Must run before ANY RTDB use in the process — `setPersistenceEnabled` throws once the database has been
 * touched. `VifiApplication` calls it first thing in `onCreate`, ahead of the legacy screens that still call
 * `FirebaseDatabase.getInstance()` directly. Safe to call again: later calls are no-ops.
 */
object FirebaseDatabaseSetup {

    private const val CACHE_SIZE_BYTES = 50L * 1024 * 1024

    private val configured = AtomicBoolean(false)

    fun configure(database: FirebaseDatabase = FirebaseDatabase.getInstance()) {
        if (!configured.compareAndSet(false, true)) return
        database.setPersistenceEnabled(true)
        database.setPersistenceCacheSizeBytes(CACHE_SIZE_BYTES)
    }
}
