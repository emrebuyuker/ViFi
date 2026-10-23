package com.salticusteam.vifi.core.util

/** Shared vifi-831a8 backend identifiers (same values as ViFi-iOS); never inline these at call sites. */
object FirebaseConstants {
    /** Named Firestore database (eur3). The DEFAULT database is empty — reads there silently return nothing. */
    const val FIRESTORE_DATABASE_ID = "vifi"

    /** Region of the shared callables (unlockExam, recordLoginDiagnostic); a wrong region yields NOT_FOUND. */
    const val FUNCTIONS_REGION = "europe-west1"

    /** Host of Firebase Storage download URLs (iOS RemoteFileLoader.firebaseStorageHost). */
    const val STORAGE_HOST = "firebasestorage.googleapis.com"
}
