package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Coroutine bridges for Firebase listener APIs (iOS AsyncStream equivalent).
 *
 * Data-layer only: these expose raw Firebase types, so repositories must map them to
 * domain models before anything reaches a ViewModel (see .claude/rules/firebase-data.md).
 * One-shot calls don't need a helper here — use `Task.await()` from kotlinx-coroutines-play-services.
 */

/** Emits the query's current value and every later change; the listener is removed when collection stops. */
fun Query.valueEvents(): Flow<DataSnapshot> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            close(error.toException())
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}.conflate()

/** Emits the signed-in user (null when signed out) on registration and on every auth state change. */
fun FirebaseAuth.authStateChanges(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}.conflate()
