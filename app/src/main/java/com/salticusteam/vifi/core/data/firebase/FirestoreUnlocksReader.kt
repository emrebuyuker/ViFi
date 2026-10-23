package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.salticusteam.vifi.core.data.repository.UnlocksReader
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Reads the user's unlocked exams from the named 'vifi' Firestore `users/<uid>/unlocks` collection, where each
 * document id is an exam id (iOS `FirebaseUnlocksReader`). Only the `unlockExam` Cloud Function writes them.
 */
class FirestoreUnlocksReader @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UnlocksReader {

    override suspend fun unlockedExamIds(uid: String): Set<String> =
        firestore.collection(USERS).document(uid).collection(UNLOCKS).get().await()
            .documents.mapTo(mutableSetOf()) { it.id }

    private companion object {
        const val USERS = "users"
        const val UNLOCKS = "unlocks"
    }
}
